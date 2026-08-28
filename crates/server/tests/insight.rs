mod helpers;
use axum::http::StatusCode;
use chrono::{TimeZone, Utc};
use helpers::TestApp;
use sqlx::PgPool;

/// The day the tests ask about. Everything else is counted backwards from it,
/// the way the route counts: the recent week is 13–19 August, the one before
/// it 6–12 August, and the 20th itself belongs to neither.
const TODAY: &str = "2026-08-20";

/// Insert one measured hour, bypassing ingest: these tests are about reading.
/// `hour` is UTC, as a device reports it.
async fn seed(pool: &PgPool, device_id: &str, day: u32, hour: u32, package: &str, ms: i64) {
    let hour_start = Utc.with_ymd_and_hms(2026, 8, day, hour, 0, 0).unwrap();
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
    measured(pool, device_id, day, hour, ms).await;
}

/// A device reported this hour at all — with or without an app in it.
async fn measured(pool: &PgPool, device_id: &str, day: u32, hour: u32, ms: i64) {
    let hour_start = Utc.with_ymd_and_hms(2026, 8, day, hour, 0, 0).unwrap();
    let id: uuid::Uuid = device_id.parse().unwrap();
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

fn insight_url(child_id: &str) -> String {
    format!("/v1/children/{child_id}/insight?date={TODAY}&tz=Europe/Zurich")
}

#[sqlx::test]
async fn a_week_is_compared_with_the_one_before_it(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 19, 10, "com.a", 600_000).await;
    seed(&pool, &device_id, 14, 10, "com.a", 300_000).await;
    seed(&pool, &device_id, 12, 10, "com.a", 200_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(response.status, StatusCode::OK, "{}", response.json);
    let week = &response.json["week"];
    assert_eq!(week["from"], "2026-08-13");
    assert_eq!(week["to"], "2026-08-19");
    assert_eq!(week["previous_from"], "2026-08-06");
    assert_eq!(week["previous_to"], "2026-08-12");
    assert_eq!(week["total_ms"], 900_000);
    assert_eq!(week["previous_total_ms"], 200_000);
}

#[sqlx::test]
async fn today_is_left_out_because_it_is_not_over(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 20, 10, "com.a", 900_000).await;
    seed(&pool, &device_id, 19, 10, "com.a", 60_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(
        response.json["week"]["total_ms"], 60_000,
        "an hour lived today must not shorten the week it is measured against"
    );
}

#[sqlx::test]
async fn an_evening_is_counted_by_the_childs_clock(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    // 19:00 UTC is 21:00 in Zurich in August, 17:00 UTC is 19:00 there.
    seed(&pool, &device_id, 19, 19, "com.a", 120_000).await;
    seed(&pool, &device_id, 19, 17, "com.a", 300_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    let week = &response.json["week"];
    assert_eq!(week["evening_ms"], 120_000);
    assert_eq!(week["total_ms"], 420_000);
    assert_eq!(week["evening_from_hour"], 21);
}

#[sqlx::test]
async fn the_apps_that_moved_come_back_ranked(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 19, 10, "com.up", 1_800_000).await;
    seed(&pool, &device_id, 12, 10, "com.up", 600_000).await;
    seed(&pool, &device_id, 19, 10, "com.down", 60_000).await;
    seed(&pool, &device_id, 12, 10, "com.down", 3_600_000).await;
    seed(&pool, &device_id, 19, 11, "com.steady", 120_000).await;
    seed(&pool, &device_id, 12, 11, "com.steady", 120_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    let movers = response.json["week"]["movers"].as_array().unwrap();
    assert_eq!(movers.len(), 2, "an app that did not move is not a mover");
    assert_eq!(movers[0]["package"], "com.down");
    assert_eq!(movers[0]["foreground_ms"], 60_000);
    assert_eq!(movers[0]["previous_foreground_ms"], 3_600_000);
    assert_eq!(movers[1]["package"], "com.up");
}

#[sqlx::test]
async fn a_renamed_app_is_named_the_way_the_family_named_it(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 19, 10, "com.a", 900_000).await;
    sqlx::query(
        "INSERT INTO packages (family_id, package, label)
         SELECT d.family_id, 'com.a', 'Games' FROM devices d WHERE d.id = $1",
    )
    .bind(device_id.parse::<uuid::Uuid>().unwrap())
    .execute(&pool)
    .await
    .unwrap();

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(response.json["week"]["movers"][0]["label"], "Games");
}

#[sqlx::test]
async fn a_first_week_has_nothing_to_compare_with(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 19, 10, "com.a", 900_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(
        response.json["week"]["previous_measured"], false,
        "no phone reported that week: a comparison would be against silence"
    );
}

#[sqlx::test]
async fn a_quiet_week_is_not_a_missing_one(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    seed(&pool, &device_id, 19, 10, "com.a", 900_000).await;
    // The phone reported that hour and measured nothing in it — which is a
    // genuine zero, and must not read like a week nobody measured.
    measured(&pool, &device_id, 12, 10, 0).await;

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(response.json["week"]["previous_measured"], true);
    assert_eq!(response.json["week"]["previous_total_ms"], 0);
}

#[sqlx::test]
async fn another_childs_hours_are_not_in_this_childs_week(pool: PgPool) {
    let (app, child_id, device_id) = setup(pool.clone()).await;
    let other = app.create_child("Sibling").await;
    let (other_device, _) = app.enroll_device(&other).await;
    seed(&pool, &device_id, 19, 10, "com.a", 60_000).await;
    seed(&pool, &other_device, 19, 10, "com.a", 3_600_000).await;

    let response = app.get(&insight_url(&child_id)).await;

    assert_eq!(response.json["week"]["total_ms"], 60_000);
}

#[sqlx::test]
async fn an_unknown_timezone_is_refused(pool: PgPool) {
    let (app, child_id, _) = setup(pool).await;
    let response = app
        .get(&format!(
            "/v1/children/{child_id}/insight?date={TODAY}&tz=Mars/Olympus"
        ))
        .await;
    assert_eq!(response.status, StatusCode::UNPROCESSABLE_ENTITY);
}
