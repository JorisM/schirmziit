use crate::AppState;
use crate::error::ApiError;
use crate::routes::enroll::Device;
use axum::extract::{DefaultBodyLimit, State};
use axum::{Json, Router, routing::post};
use chrono::{Duration, Utc};
use nestling_core::wire::{IngestRequest, IngestResponse, Rejected, SCHEMA_VERSION};

pub const MAX_HOURS: usize = 500;
pub const MAX_BODY_BYTES: usize = 1_000_000;
/// 120 requests/hour/device: a 30-minute agent needs 2, so this leaves room for
/// retries and backlog splitting while capping a runaway client.
const MAX_REQUESTS_PER_HOUR: u32 = 120;
/// A phone's clock can be wrong (or set by a child). Anything beyond this is
/// not a clock skew, it is bogus data.
const FUTURE_TOLERANCE_HOURS: i64 = 1;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/ingest", post(ingest))
        .layer(DefaultBodyLimit::max(MAX_BODY_BYTES))
}

#[utoipa::path(
    post, path = "/v1/ingest", request_body = IngestRequest,
    responses(
        (status = 200, description = "Per-row accept/reject result", body = IngestResponse),
        (status = 400, description = "Unsupported schema version"),
        (status = 401, description = "Unknown or revoked device token"),
        (status = 413, description = "Too many hours in one batch"),
        (status = 429, description = "Device rate limit"),
    ),
    security(("device_token" = [])),
    tag = "ingest"
)]
pub async fn ingest(
    device: Device,
    State(state): State<AppState>,
    Json(body): Json<IngestRequest>,
) -> Result<Json<IngestResponse>, ApiError> {
    if body.schema != SCHEMA_VERSION {
        return Err(ApiError::UnsupportedSchema(body.schema));
    }
    if body.hours.len() > MAX_HOURS {
        return Err(ApiError::PayloadTooLarge);
    }
    check_rate_limit(&state, device.id)?;

    let now = Utc::now();
    let horizon = now + Duration::hours(FUTURE_TOLERANCE_HOURS);
    let mut accepted = Vec::new();
    let mut rejected = Vec::new();
    let mut tx = state.pool.begin().await?;

    for hour in &body.hours {
        if hour.hour_start > horizon {
            rejected.push(Rejected {
                hour_start: hour.hour_start,
                reason: "hour_start is in the future".into(),
                permanent: true,
            });
            continue;
        }
        if hour.tz.parse::<chrono_tz::Tz>().is_err() {
            rejected.push(Rejected {
                hour_start: hour.hour_start,
                reason: format!("unknown timezone: {}", hour.tz),
                permanent: true,
            });
            continue;
        }

        // Replace-if-newer, never additive: with 30-minute sends against hourly
        // buckets the current hour always arrives at least twice.
        sqlx::query!(
            "INSERT INTO device_hours
               (device_id, hour_start, tz, screen_on_ms, unlock_count, computed_at)
             VALUES ($1, $2, $3, $4, $5, $6)
             ON CONFLICT (device_id, hour_start) DO UPDATE
               SET screen_on_ms = EXCLUDED.screen_on_ms,
                   unlock_count = EXCLUDED.unlock_count,
                   tz           = EXCLUDED.tz,
                   computed_at  = EXCLUDED.computed_at
             WHERE EXCLUDED.computed_at > device_hours.computed_at",
            device.id,
            hour.hour_start,
            hour.tz,
            hour.screen_on_ms,
            hour.unlock_count,
            hour.computed_at
        )
        .execute(&mut *tx)
        .await?;

        for app in &hour.apps {
            sqlx::query!(
                "INSERT INTO usage_hours
                   (device_id, package, hour_start, tz, foreground_ms, launch_count, computed_at)
                 VALUES ($1, $2, $3, $4, $5, $6, $7)
                 ON CONFLICT (device_id, package, hour_start) DO UPDATE
                   SET foreground_ms = EXCLUDED.foreground_ms,
                       launch_count  = EXCLUDED.launch_count,
                       tz            = EXCLUDED.tz,
                       computed_at   = EXCLUDED.computed_at
                 WHERE EXCLUDED.computed_at > usage_hours.computed_at",
                device.id,
                app.package,
                hour.hour_start,
                hour.tz,
                app.foreground_ms,
                app.launch_count,
                hour.computed_at
            )
            .execute(&mut *tx)
            .await?;

            // The server cannot know that com.zhiliaoapp.musically is TikTok;
            // the device tells us, last-write-wins per family.
            sqlx::query!(
                "INSERT INTO packages (family_id, package, label) VALUES ($1, $2, $3)
                 ON CONFLICT (family_id, package) DO UPDATE
                   SET label = EXCLUDED.label, last_seen = now()",
                device.family_id,
                app.package,
                app.label
            )
            .execute(&mut *tx)
            .await?;
        }

        accepted.push(hour.hour_start);
    }

    sqlx::query!(
        "UPDATE devices SET last_seen_at = $1 WHERE id = $2",
        now,
        device.id
    )
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;

    Ok(Json(IngestResponse { accepted, rejected }))
}

fn check_rate_limit(state: &AppState, device_id: uuid::Uuid) -> Result<(), ApiError> {
    let mut limits = state.ingest_limits.lock().expect("ingest limiter mutex");
    let now = Utc::now();
    let entry = limits.entry(device_id).or_insert((now, 0));
    if now - entry.0 > Duration::hours(1) {
        *entry = (now, 0);
    }
    entry.1 += 1;
    if entry.1 > MAX_REQUESTS_PER_HOUR {
        return Err(ApiError::RateLimited);
    }
    Ok(())
}
