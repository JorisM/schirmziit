use crate::AppState;
use crate::auth::Parent;
use crate::db::scope;
use crate::error::ApiError;
use axum::extract::{Path, Query, State};
use axum::{Json, Router, routing::get};
use chrono::{DateTime, Duration, NaiveDate, TimeZone, Utc};
use chrono_tz::Tz;
use std::collections::BTreeMap;
use uuid::Uuid;

/// Three missed 30-minute syncs.
const STALE_AFTER_MINUTES: i64 = 90;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/children/{id}/usage", get(usage))
        .route("/v1/children/{id}/summary", get(summary))
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct DeviceStatus {
    pub id: Uuid,
    pub label: String,
    pub last_seen_at: Option<DateTime<Utc>>,
    pub stale: bool,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct Point {
    /// RFC3339 instant for hourly buckets, `YYYY-MM-DD` for daily ones.
    pub start: String,
    pub foreground_ms: i64,
    pub launch_count: i32,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct Series {
    pub package: String,
    pub label: String,
    pub points: Vec<Point>,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct DeviceTotal {
    pub start: String,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct UsageResponse {
    pub child_id: Uuid,
    pub from: NaiveDate,
    pub to: NaiveDate,
    pub bucket: String,
    pub tz: String,
    pub devices: Vec<DeviceStatus>,
    pub series: Vec<Series>,
    pub device_totals: Vec<DeviceTotal>,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct TopApp {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct SummaryResponse {
    pub child_id: Uuid,
    pub date: NaiveDate,
    pub tz: String,
    pub total_ms: i64,
    pub unlock_count: i64,
    pub first_activity: Option<String>,
    pub last_activity: Option<String>,
    pub top_apps: Vec<TopApp>,
}

#[derive(serde::Deserialize, utoipa::IntoParams)]
pub struct UsageQuery {
    from: NaiveDate,
    to: NaiveDate,
    #[serde(default = "default_bucket")]
    bucket: String,
    tz: String,
}

fn default_bucket() -> String {
    "hour".into()
}

fn zone(tz: &str) -> Result<Tz, ApiError> {
    tz.parse()
        .map_err(|_| ApiError::Validation(format!("unknown timezone: {tz}")))
}

/// Local date range -> UTC instants. `to` is inclusive, so the upper bound is
/// the start of the following local day. The caller never does timezone
/// arithmetic; that is the whole point of storing `tz` per row.
fn bounds(
    from: NaiveDate,
    to: NaiveDate,
    tz: Tz,
) -> Result<(DateTime<Utc>, DateTime<Utc>), ApiError> {
    let start_local = from.and_hms_opt(0, 0, 0).expect("midnight exists");
    let end_local = (to + Duration::days(1))
        .and_hms_opt(0, 0, 0)
        .expect("midnight exists");
    let start = tz
        .from_local_datetime(&start_local)
        .earliest()
        .ok_or_else(|| ApiError::Validation("invalid start date for timezone".into()))?;
    let end = tz
        .from_local_datetime(&end_local)
        .earliest()
        .ok_or_else(|| ApiError::Validation("invalid end date for timezone".into()))?;
    Ok((start.with_timezone(&Utc), end.with_timezone(&Utc)))
}

fn device_status(
    id: Uuid,
    label: String,
    last_seen_at: Option<DateTime<Utc>>,
    now: DateTime<Utc>,
) -> DeviceStatus {
    DeviceStatus {
        id,
        label,
        last_seen_at,
        stale: last_seen_at.is_none_or(|seen| now - seen > Duration::minutes(STALE_AFTER_MINUTES)),
    }
}

#[utoipa::path(
    get, path = "/v1/children/{id}/usage",
    params(("id" = Uuid, Path, description = "Child id"), UsageQuery),
    responses(
        (status = 200, description = "Usage series for the requested local dates", body = UsageResponse),
        (status = 404, description = "No such child in this family"),
        (status = 422, description = "Unknown timezone"),
    ),
    tag = "usage"
)]
pub async fn usage(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Query(q): Query<UsageQuery>,
) -> Result<Json<UsageResponse>, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;
    Ok(Json(usage_for_child(&state.pool, child_id, &q).await?))
}

/// The one place usage is read. A parent and a child must never see different
/// numbers for the same day, and one query path is the cheapest way to promise
/// that.
pub(crate) async fn usage_for_child(
    pool: &sqlx::PgPool,
    child_id: Uuid,
    q: &UsageQuery,
) -> Result<UsageResponse, ApiError> {
    let tz = zone(&q.tz)?;
    let (start, end) = bounds(q.from, q.to, tz)?;

    // Revoked devices are deliberately excluded: one shows up as "not
    // reporting" forever otherwise, which reads as a problem rather than as a
    // decision someone made. GET /v1/devices still lists them with their flag.
    let devices = sqlx::query!(
        "SELECT id, label, last_seen_at FROM devices
         WHERE child_id = $1 AND revoked_at IS NULL ORDER BY created_at",
        child_id
    )
    .fetch_all(pool)
    .await?;

    let now = Utc::now();
    let devices_json: Vec<DeviceStatus> = devices
        .into_iter()
        .map(|d| device_status(d.id, d.label, d.last_seen_at, now))
        .collect();

    // Summed across all of a child's devices: one child has one screen-time
    // number, phone plus tablet.
    let mut series: BTreeMap<String, (String, Vec<Point>)> = BTreeMap::new();

    if q.bucket == "day" {
        let rows = sqlx::query!(
            r#"SELECT u.package,
                      COALESCE(p.label, u.package) AS "label!",
                      (u.hour_start AT TIME ZONE $4)::date AS "day!",
                      SUM(u.foreground_ms)::bigint AS "ms!",
                      SUM(u.launch_count)::int      AS "launches!"
               FROM usage_hours u
               JOIN devices d ON d.id = u.device_id
               LEFT JOIN packages p ON p.family_id = d.family_id AND p.package = u.package
               WHERE d.child_id = $1 AND u.hour_start >= $2 AND u.hour_start < $3
               GROUP BY u.package, "label!", 3
               ORDER BY u.package, 3"#,
            child_id,
            start,
            end,
            q.tz
        )
        .fetch_all(pool)
        .await?;

        for r in rows {
            series
                .entry(r.package.clone())
                .or_insert_with(|| (r.label.clone(), Vec::new()))
                .1
                .push(Point {
                    start: r.day.to_string(),
                    foreground_ms: r.ms,
                    launch_count: r.launches,
                });
        }
    } else {
        let rows = sqlx::query!(
            r#"SELECT u.package,
                      COALESCE(p.label, u.package) AS "label!",
                      u.hour_start,
                      SUM(u.foreground_ms)::bigint AS "ms!",
                      SUM(u.launch_count)::int      AS "launches!"
               FROM usage_hours u
               JOIN devices d ON d.id = u.device_id
               LEFT JOIN packages p ON p.family_id = d.family_id AND p.package = u.package
               WHERE d.child_id = $1 AND u.hour_start >= $2 AND u.hour_start < $3
               GROUP BY u.package, "label!", u.hour_start
               ORDER BY u.package, u.hour_start"#,
            child_id,
            start,
            end
        )
        .fetch_all(pool)
        .await?;

        for r in rows {
            series
                .entry(r.package.clone())
                .or_insert_with(|| (r.label.clone(), Vec::new()))
                .1
                .push(Point {
                    start: r.hour_start.with_timezone(&tz).to_rfc3339(),
                    foreground_ms: r.ms,
                    launch_count: r.launches,
                });
        }
    }

    // Daily totals must actually be daily: grouping by hour_start regardless
    // of bucket would hand a 14-day view 336 rows nobody asked for.
    let device_totals = if q.bucket == "day" {
        let rows = sqlx::query!(
            r#"SELECT (h.hour_start AT TIME ZONE $4)::date AS "day!",
                      SUM(h.screen_on_ms)::bigint AS "screen_on_ms!",
                      SUM(h.unlock_count)::int    AS "unlock_count!"
               FROM device_hours h
               JOIN devices d ON d.id = h.device_id
               WHERE d.child_id = $1 AND h.hour_start >= $2 AND h.hour_start < $3
               GROUP BY 1 ORDER BY 1"#,
            child_id,
            start,
            end,
            q.tz
        )
        .fetch_all(pool)
        .await?;
        rows.iter()
            .map(|t| DeviceTotal {
                start: t.day.to_string(),
                screen_on_ms: t.screen_on_ms,
                unlock_count: t.unlock_count,
            })
            .collect()
    } else {
        let rows = sqlx::query!(
            r#"SELECT h.hour_start,
                      SUM(h.screen_on_ms)::bigint AS "screen_on_ms!",
                      SUM(h.unlock_count)::int    AS "unlock_count!"
               FROM device_hours h
               JOIN devices d ON d.id = h.device_id
               WHERE d.child_id = $1 AND h.hour_start >= $2 AND h.hour_start < $3
               GROUP BY h.hour_start ORDER BY h.hour_start"#,
            child_id,
            start,
            end
        )
        .fetch_all(pool)
        .await?;
        rows.iter()
            .map(|t| DeviceTotal {
                start: t.hour_start.with_timezone(&tz).to_rfc3339(),
                screen_on_ms: t.screen_on_ms,
                unlock_count: t.unlock_count,
            })
            .collect()
    };

    Ok(UsageResponse {
        child_id,
        from: q.from,
        to: q.to,
        bucket: q.bucket.clone(),
        tz: q.tz.clone(),
        devices: devices_json,
        series: series
            .into_iter()
            .map(|(package, (label, points))| Series {
                package,
                label,
                points,
            })
            .collect(),
        device_totals,
    })
}

#[derive(serde::Deserialize, utoipa::IntoParams)]
pub struct SummaryQuery {
    date: NaiveDate,
    tz: String,
}

#[utoipa::path(
    get, path = "/v1/children/{id}/summary",
    params(("id" = Uuid, Path, description = "Child id"), SummaryQuery),
    responses(
        (status = 200, description = "One local day, summarised", body = SummaryResponse),
        (status = 404, description = "No such child in this family"),
        (status = 422, description = "Unknown timezone"),
    ),
    tag = "usage"
)]
pub async fn summary(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Query(q): Query<SummaryQuery>,
) -> Result<Json<SummaryResponse>, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;
    let tz = zone(&q.tz)?;
    let (start, end) = bounds(q.date, q.date, tz)?;

    let rows = sqlx::query!(
        r#"SELECT u.package,
                  COALESCE(p.label, u.package) AS "label!",
                  SUM(u.foreground_ms)::bigint AS "ms!",
                  MIN(u.hour_start) AS "first!",
                  MAX(u.hour_start) AS "last!"
           FROM usage_hours u
           JOIN devices d ON d.id = u.device_id
           LEFT JOIN packages p ON p.family_id = d.family_id AND p.package = u.package
           WHERE d.child_id = $1 AND u.hour_start >= $2 AND u.hour_start < $3
           GROUP BY u.package, "label!"
           ORDER BY "ms!" DESC"#,
        child_id,
        start,
        end
    )
    .fetch_all(&state.pool)
    .await?;

    let unlocks: Option<i64> = sqlx::query_scalar!(
        r#"SELECT SUM(h.unlock_count)::bigint FROM device_hours h
           JOIN devices d ON d.id = h.device_id
           WHERE d.child_id = $1 AND h.hour_start >= $2 AND h.hour_start < $3"#,
        child_id,
        start,
        end
    )
    .fetch_one(&state.pool)
    .await?;

    Ok(Json(SummaryResponse {
        child_id,
        date: q.date,
        tz: q.tz,
        total_ms: rows.iter().map(|r| r.ms).sum::<i64>(),
        unlock_count: unlocks.unwrap_or(0),
        first_activity: rows
            .iter()
            .map(|r| r.first)
            .min()
            .map(|t| t.with_timezone(&tz).to_rfc3339()),
        last_activity: rows
            .iter()
            .map(|r| r.last)
            .max()
            .map(|t| t.with_timezone(&tz).to_rfc3339()),
        top_apps: rows
            .iter()
            .take(10)
            .map(|r| TopApp {
                package: r.package.clone(),
                label: r.label.clone(),
                foreground_ms: r.ms,
            })
            .collect(),
    }))
}
