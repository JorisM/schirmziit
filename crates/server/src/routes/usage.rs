use crate::AppState;
use crate::auth::Parent;
use crate::db::scope;
use crate::error::ApiError;
use crate::routes::enroll::Device;
use axum::extract::{Path, Query, State};
use axum::{Json, Router, routing::get};
use chrono::{DateTime, Duration, NaiveDate, TimeZone, Utc};
use chrono_tz::Tz;
use schirmziit_core::insight::{self, HourPoint, WeekComparison};
use std::collections::BTreeMap;
use uuid::Uuid;

/// Three missed 30-minute syncs.
const STALE_AFTER_MINUTES: i64 = 90;

/// Generous: a child may open the app repeatedly, and this is one indexed
/// read. It exists to stop a loop, not to ration a family.
const MAX_READS_PER_HOUR: u32 = 240;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/children/{id}/usage", get(usage))
        .route("/v1/children/{id}/insight", get(insight))
        // Same `/v1/me` prefix as `auth::routes`' `/v1/me` (the parent
        // session), but a different identity: this one is `my_usage`, read by
        // the calling *device* over its bearer token. Do not let a future
        // `/v1/me/*` route inherit whichever extractor the copy-paste source
        // happened to use — check which identity that route actually needs.
        .route("/v1/me/usage", get(my_usage))
}

fn check_read_limit(state: &AppState, device_id: Uuid) -> Result<(), ApiError> {
    let mut limits = state.read_limits.lock().expect("read limiter mutex");
    let now = Utc::now();
    let entry = limits.entry(device_id).or_insert((now, 0));
    if now - entry.0 > Duration::hours(1) {
        *entry = (now, 0);
    }
    entry.1 += 1;
    if entry.1 > MAX_READS_PER_HOUR {
        return Err(ApiError::RateLimited);
    }
    Ok(())
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
    /// Media playing with the screen off. A separate measure: never add it to
    /// `foreground_ms`, on any surface.
    pub background_ms: i64,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct Series {
    pub package: String,
    pub label: String,
    pub points: Vec<Point>,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct DeviceTotal {
    /// RFC3339 instant for hourly buckets, `YYYY-MM-DD` for daily ones — same
    /// dual format as `Point.start`, and the exact field a wrong `bucket` on
    /// the client side answers wrong for (`crates/core/src/selfusage.rs`).
    pub start: String,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
    /// False means none of the devices reporting this bucket could observe
    /// background playback — not that nothing played. Rendering the two alike
    /// is the silent zero this product exists not to tell.
    pub background_measured: bool,
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

pub(crate) fn zone(tz: &str) -> Result<Tz, ApiError> {
    tz.parse()
        .map_err(|_| ApiError::Validation(format!("unknown timezone: {tz}")))
}

/// Local date range -> UTC instants. `to` is inclusive, so the upper bound is
/// the start of the following local day. The caller never does timezone
/// arithmetic; that is the whole point of storing `tz` per row.
pub(crate) fn bounds(
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

#[utoipa::path(
    get, path = "/v1/me/usage",
    params(UsageQuery),
    responses(
        (status = 200, description = "This device's own child, for the requested local dates", body = UsageResponse),
        (status = 401, description = "Unknown or revoked device token"),
        (status = 422, description = "Unknown timezone"),
        (status = 429, description = "Device read limit"),
    ),
    security(("device_token" = [])),
    tag = "usage"
)]
/// The one read a device token buys, and it takes no id: a device sees the child
/// it was enrolled for and has no way to name another. `/v1/children` and every
/// other parent route still refuse a device token — there is a test for it.
pub async fn my_usage(
    device: Device,
    State(state): State<AppState>,
    Query(q): Query<UsageQuery>,
) -> Result<Json<UsageResponse>, ApiError> {
    check_read_limit(&state, device.id)?;
    Ok(Json(
        usage_for_child(&state.pool, device.child_id, &q).await?,
    ))
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
                      SUM(u.launch_count)::int      AS "launches!",
                      SUM(u.background_ms)::bigint  AS "background_ms!"
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
                    background_ms: r.background_ms,
                });
        }
    } else {
        let rows = sqlx::query!(
            r#"SELECT u.package,
                      COALESCE(p.label, u.package) AS "label!",
                      u.hour_start,
                      SUM(u.foreground_ms)::bigint AS "ms!",
                      SUM(u.launch_count)::int      AS "launches!",
                      SUM(u.background_ms)::bigint  AS "background_ms!"
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
                    background_ms: r.background_ms,
                });
        }
    }

    // Daily totals must actually be daily: grouping by hour_start regardless
    // of bucket would hand a 14-day view 336 rows nobody asked for.
    let device_totals = if q.bucket == "day" {
        let rows = sqlx::query!(
            r#"SELECT (h.hour_start AT TIME ZONE $4)::date AS "day!",
                      SUM(h.screen_on_ms)::bigint AS "screen_on_ms!",
                      SUM(h.unlock_count)::int    AS "unlock_count!",
                      bool_or(h.background_measured) AS "background_measured!"
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
                // bool_or, not bool_and: one Android phone alongside an iPad
                // still makes the day observable.
                background_measured: t.background_measured,
            })
            .collect()
    } else {
        let rows = sqlx::query!(
            r#"SELECT h.hour_start,
                      SUM(h.screen_on_ms)::bigint AS "screen_on_ms!",
                      SUM(h.unlock_count)::int    AS "unlock_count!",
                      bool_or(h.background_measured) AS "background_measured!"
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
                background_measured: t.background_measured,
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
pub struct InsightQuery {
    /// The local date the parent is looking at. Today is part of neither week
    /// — the comparison ends yesterday — but it decides where the two weeks
    /// fall, and only the client knows which day it is where the family lives.
    date: NaiveDate,
    tz: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct InsightResponse {
    pub child_id: Uuid,
    pub tz: String,
    pub week: WeekComparison,
}

#[utoipa::path(
    get, path = "/v1/children/{id}/insight",
    params(("id" = Uuid, Path, description = "Child id"), InsightQuery),
    responses(
        (status = 200, description = "The last full week against the one before it", body = InsightResponse),
        (status = 404, description = "No such child in this family"),
        (status = 422, description = "Unknown timezone"),
    ),
    tag = "usage"
)]
pub async fn insight(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Query(q): Query<InsightQuery>,
) -> Result<Json<InsightResponse>, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;
    // Validated here so an unknown zone is the same 422 every other read
    // answers with, rather than a core error surfacing one layer down.
    zone(&q.tz)?;
    let weeks = insight::weeks(&q.tz, q.date).map_err(|e| ApiError::Validation(e.to_string()))?;

    let rows = sqlx::query!(
        r#"SELECT u.hour_start,
                  u.package,
                  COALESCE(p.label, u.package) AS "label!",
                  SUM(u.foreground_ms)::bigint AS "ms!"
           FROM usage_hours u
           JOIN devices d ON d.id = u.device_id
           LEFT JOIN packages p ON p.family_id = d.family_id AND p.package = u.package
           WHERE d.child_id = $1 AND u.hour_start >= $2 AND u.hour_start < $3
           GROUP BY u.hour_start, u.package, "label!""#,
        child_id,
        weeks.start,
        weeks.end
    )
    .fetch_all(&state.pool)
    .await?;

    // Whether the earlier week was measured at all, which the sum above cannot
    // answer: a week of zeros and a week no phone reported add up the same and
    // mean opposite things.
    let previous_measured = sqlx::query_scalar!(
        r#"SELECT EXISTS(
             SELECT 1 FROM device_hours h
             JOIN devices d ON d.id = h.device_id
             WHERE d.child_id = $1 AND h.hour_start >= $2 AND h.hour_start < $3
           ) AS "measured!""#,
        child_id,
        weeks.start,
        weeks.previous_end
    )
    .fetch_one(&state.pool)
    .await?;

    let points: Vec<HourPoint> = rows
        .into_iter()
        .map(|r| HourPoint {
            hour_start: r.hour_start,
            package: r.package,
            label: r.label,
            foreground_ms: r.ms,
        })
        .collect();

    let week = insight::compare(&q.tz, q.date, &points, previous_measured)
        .map_err(|e| ApiError::Validation(e.to_string()))?;

    Ok(Json(InsightResponse {
        child_id,
        tz: q.tz,
        week,
    }))
}
