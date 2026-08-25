mod helpers;
use axum::http::StatusCode;
use chrono::{DateTime, Duration, TimeZone, Utc};
use helpers::TestApp;
use schirmziit_server::retention;
use sqlx::PgPool;

async fn insert_hour(pool: &PgPool, device_id: &str, at: DateTime<Utc>, ms: i64) {
    let id: uuid::Uuid = device_id.parse().unwrap();
    sqlx::query(
        "INSERT INTO usage_hours
           (device_id, package, hour_start, tz, foreground_ms, launch_count, computed_at)
         VALUES ($1, 'com.a', $2, 'Europe/Zurich', $3, 1, now())",
    )
    .bind(id)
    .bind(at)
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
async fn folds_expired_hours_into_days_and_deletes_them(pool: PgPool) {
    let (_, _, device_id) = setup(pool.clone()).await;

    let old = Utc.with_ymd_and_hms(2026, 8, 20, 10, 0, 0).unwrap();
    insert_hour(&pool, &device_id, old, 60_000).await;
    insert_hour(&pool, &device_id, old + Duration::hours(1), 30_000).await;

    // Pretend it is 14 months later. `now` is a parameter precisely so this is
    // testable without waiting 13 months.
    let report = retention::run_once(&pool, 13, old + Duration::days(430))
        .await
        .unwrap();
    assert_eq!(report.deleted_hours, 2);

    let remaining: i64 = sqlx::query_scalar("SELECT count(*) FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(remaining, 0);

    let (day, ms): (chrono::NaiveDate, i64) =
        sqlx::query_as("SELECT day, foreground_ms FROM usage_days")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(
        day,
        chrono::NaiveDate::from_ymd_opt(2026, 8, 20).unwrap(),
        "local date, not UTC date"
    );
    assert_eq!(ms, 90_000);
}

#[sqlx::test]
async fn leaves_recent_hours_alone(pool: PgPool) {
    let (_, _, device_id) = setup(pool.clone()).await;
    insert_hour(&pool, &device_id, Utc::now() - Duration::days(30), 60_000).await;

    let report = retention::run_once(&pool, 13, Utc::now()).await.unwrap();
    assert_eq!(report.deleted_hours, 0);
    let remaining: i64 = sqlx::query_scalar("SELECT count(*) FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(remaining, 1);
}

#[sqlx::test]
async fn running_twice_does_not_double_the_daily_total(pool: PgPool) {
    let (_, _, device_id) = setup(pool.clone()).await;
    let old = Utc.with_ymd_and_hms(2026, 8, 20, 10, 0, 0).unwrap();
    insert_hour(&pool, &device_id, old, 60_000).await;

    let now = old + Duration::days(430);
    retention::run_once(&pool, 13, now).await.unwrap();
    retention::run_once(&pool, 13, now).await.unwrap();

    let ms: i64 = sqlx::query_scalar("SELECT foreground_ms FROM usage_days")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(ms, 60_000);
}

#[sqlx::test]
async fn expired_sessions_are_swept(pool: PgPool) {
    let (_, _, _) = setup(pool.clone()).await;
    sqlx::query("UPDATE sessions SET expires_at = now() - interval '1 day'")
        .execute(&pool)
        .await
        .unwrap();

    retention::run_once(&pool, 13, Utc::now()).await.unwrap();
    let left: i64 = sqlx::query_scalar("SELECT count(*) FROM sessions")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(left, 0);
}

#[sqlx::test]
async fn purge_deletes_everything_for_one_child_and_reports_counts(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    insert_hour(&pool, &device_id, Utc::now() - Duration::hours(3), 60_000).await;

    let response = app.delete(&format!("/v1/children/{child_id}/data")).await;
    assert_eq!(response.status, StatusCode::OK, "{}", response.json);
    assert_eq!(response.json["deleted_usage_hours"], 1);

    let remaining: i64 = sqlx::query_scalar("SELECT count(*) FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(remaining, 0);
}

async fn insert_background_hour(pool: &PgPool, device_id: &str, at: DateTime<Utc>, ms: i64) {
    let id: uuid::Uuid = device_id.parse().unwrap();
    sqlx::query(
        "INSERT INTO usage_hours
           (device_id, package, hour_start, tz, foreground_ms, launch_count, computed_at,
            background_ms)
         VALUES ($1, 'com.abs', $2, 'Europe/Zurich', 0, 0, now(), $3)",
    )
    .bind(id)
    .bind(at)
    .bind(ms)
    .execute(pool)
    .await
    .unwrap();
}

#[sqlx::test]
async fn the_fold_keeps_background_ms(pool: PgPool) {
    // A rollup that dropped this column would make old background listening
    // vanish while old screen time survived — a silent hole in the history.
    let (_, _, device_id) = setup(pool.clone()).await;

    let old = Utc.with_ymd_and_hms(2026, 8, 20, 22, 0, 0).unwrap();
    insert_background_hour(&pool, &device_id, old, 1_800_000).await;
    insert_background_hour(&pool, &device_id, old + Duration::hours(1), 600_000).await;

    retention::run_once(&pool, 13, old + Duration::days(430))
        .await
        .unwrap();

    let (foreground, background): (i64, i64) =
        sqlx::query_as("SELECT foreground_ms, background_ms FROM usage_days")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(background, 2_400_000);
    assert_eq!(
        foreground, 0,
        "background time is not folded into screen time"
    );
}
