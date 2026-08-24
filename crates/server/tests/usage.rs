mod helpers;
use axum::http::StatusCode;
use chrono::{TimeZone, Utc};
use helpers::TestApp;
use sqlx::PgPool;

/// Insert usage directly, bypassing ingest: these tests are about reads.
async fn seed(pool: &PgPool, device_id: &str, hour: u32, package: &str, ms: i64) {
    let hour_start = Utc.with_ymd_and_hms(2026, 8, 20, hour, 0, 0).unwrap();
    let id: uuid::Uuid = device_id.parse().unwrap();
    sqlx::query(
        "INSERT INTO usage_hours
           (device_id, package, hour_start, tz, foreground_ms, launch_count, computed_at)
         VALUES ($1, $2, $3, 'Europe/Zurich', $4, 1, now())",
    )
    .bind(id)
    .bind(package)
    .bind(hour_start)
    .bind(ms)
    .execute(pool)
    .await
    .unwrap();
    sqlx::query(
        "INSERT INTO device_hours
           (device_id, hour_start, tz, screen_on_ms, unlock_count, computed_at)
         VALUES ($1, $2, 'Europe/Zurich', $3, 7, now())
         ON CONFLICT (device_id, hour_start) DO NOTHING",
    )
    .bind(id)
    .bind(hour_start)
    .bind(ms)
    .execute(pool)
    .await
    .unwrap();
}

async fn setup(pool: PgPool) -> (TestApp, String, String) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let (device_id, _) = app.enroll_device(&child_id).await;
    (app, child_id, device_id)
}

#[sqlx::test]
async fn hourly_series_groups_by_package(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 10, "com.a", 60_000).await;
    seed(&pool, &device_id, 11, "com.a", 30_000).await;
    seed(&pool, &device_id, 11, "com.b", 15_000).await;

    let response = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;

    assert_eq!(response.status, StatusCode::OK, "{}", response.json);
    let series = response.json["series"].as_array().unwrap();
    assert_eq!(series.len(), 2);
    let a = series.iter().find(|s| s["package"] == "com.a").unwrap();
    assert_eq!(a["points"].as_array().unwrap().len(), 2);
}

#[sqlx::test]
async fn daily_bucket_sums_the_local_day(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 10, "com.a", 60_000).await;
    seed(&pool, &device_id, 11, "com.a", 30_000).await;

    let response = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=day&tz=Europe/Zurich"
        ))
        .await;

    let points = response.json["series"][0]["points"].as_array().unwrap();
    assert_eq!(points.len(), 1);
    assert_eq!(points[0]["foreground_ms"], 90_000);
}

#[sqlx::test]
async fn late_utc_hours_belong_to_the_next_local_day(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    // 23:00 UTC on the 20th is 01:00 on the 21st in Zurich.
    seed(&pool, &device_id, 23, "com.a", 60_000).await;

    let same_day = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=day&tz=Europe/Zurich"
        ))
        .await;
    assert!(same_day.json["series"].as_array().unwrap().is_empty());

    let next_day = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-21&to=2026-08-21&bucket=day&tz=Europe/Zurich"
        ))
        .await;
    assert_eq!(
        next_day.json["series"][0]["points"][0]["foreground_ms"],
        60_000
    );
}

#[sqlx::test]
async fn usage_reports_device_status(pool: PgPool) {
    let (app, child_id, _) = setup(pool).await;
    let response = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;

    let devices = response.json["devices"].as_array().unwrap();
    assert_eq!(devices.len(), 1);
    assert_eq!(
        devices[0]["stale"], true,
        "a device that never synced is stale"
    );
}

#[sqlx::test]
async fn an_unknown_timezone_is_a_422(pool: PgPool) {
    let (app, child_id, _) = setup(pool).await;
    let response = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Mars/Olympus"
        ))
        .await;
    assert_eq!(response.status, StatusCode::UNPROCESSABLE_ENTITY);
}

#[sqlx::test]
async fn summary_returns_top_apps_and_first_last_activity(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 8, "com.a", 60_000).await;
    seed(&pool, &device_id, 20, "com.b", 120_000).await;

    let response = app
        .get(&format!(
            "/v1/children/{child_id}/summary?date=2026-08-20&tz=Europe/Zurich"
        ))
        .await;
    assert_eq!(response.status, StatusCode::OK, "{}", response.json);
    assert_eq!(response.json["total_ms"], 180_000);
    assert_eq!(
        response.json["top_apps"][0]["package"], "com.b",
        "sorted by time descending"
    );
    assert!(response.json["first_activity"].is_string());
    assert!(response.json["last_activity"].is_string());
    assert_eq!(response.json["unlock_count"], 14);
}

#[sqlx::test]
async fn daily_bucket_returns_one_device_total_per_day(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 10, "com.a", 60_000).await;
    seed(&pool, &device_id, 11, "com.a", 30_000).await;

    let response = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=day&tz=Europe/Zurich"
        ))
        .await;

    let totals = response.json["device_totals"].as_array().unwrap();
    assert_eq!(
        totals.len(),
        1,
        "a daily bucket means one row per day: {totals:?}"
    );
    assert_eq!(
        totals[0]["start"], "2026-08-20",
        "daily rows are YYYY-MM-DD"
    );
    assert_eq!(totals[0]["screen_on_ms"], 90_000);
    assert_eq!(
        totals[0]["unlock_count"], 14,
        "7 unlocks in each of two seeded hours"
    );
}

#[sqlx::test]
async fn a_revoked_device_disappears_from_the_usage_view(pool: PgPool) {
    // It would otherwise sit there as "not reporting" forever, reading as a
    // fault instead of a choice. The management list keeps it, with its flag.
    let (app, child_id, device_id) = setup(pool.clone()).await;
    let before = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;
    assert_eq!(before.json["devices"].as_array().unwrap().len(), 1);

    assert_eq!(
        app.delete(&format!("/v1/devices/{device_id}")).await.status,
        StatusCode::NO_CONTENT
    );

    let after = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;
    assert!(after.json["devices"].as_array().unwrap().is_empty());

    // Still visible where it is managed, marked revoked.
    let managed = app.get("/v1/devices").await;
    assert_eq!(managed.json[0]["revoked"], true);
}
