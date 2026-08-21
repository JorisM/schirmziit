mod helpers;
use axum::http::StatusCode;
use chrono::{Duration, TimeZone, Utc};
use helpers::TestApp;
use sqlx::PgPool;

struct Enrolled {
    app: TestApp,
    token: String,
    child_id: String,
}

async fn enrolled(pool: PgPool) -> Enrolled {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let (_, token) = app.enroll_device(&child_id).await;
    Enrolled {
        app,
        token,
        child_id,
    }
}

/// One hour of usage. `computed_offset_minutes` moves `computed_at`, which is
/// what the upsert guard compares.
fn payload(hour: u32, ms: i64, computed_offset_minutes: i64) -> serde_json::Value {
    let hour_start = Utc.with_ymd_and_hms(2026, 8, 20, hour, 0, 0).unwrap();
    serde_json::json!({
        "schema": 1,
        "device_time": Utc::now(),
        "hours": [{
            "hour_start": hour_start,
            "tz": "Europe/Zurich",
            "computed_at": hour_start + Duration::minutes(computed_offset_minutes),
            "screen_on_ms": ms,
            "unlock_count": 3,
            "apps": [{
                "package": "com.a", "label": "App A",
                "foreground_ms": ms, "launch_count": 2
            }]
        }]
    })
}

#[sqlx::test]
async fn accepts_a_batch_and_stores_it(pool: PgPool) {
    let e = enrolled(pool.clone()).await;
    let response = e
        .app
        .post_as_device("/v1/ingest", &e.token, payload(10, 60_000, 60))
        .await;

    assert_eq!(response.status, StatusCode::OK);
    assert_eq!(response.json["accepted"].as_array().unwrap().len(), 1);
    assert!(response.json["rejected"].as_array().unwrap().is_empty());

    let ms: i64 = sqlx::query_scalar("SELECT foreground_ms FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(ms, 60_000);
}

#[sqlx::test]
async fn resending_the_same_hour_replaces_and_does_not_double(pool: PgPool) {
    let e = enrolled(pool.clone()).await;
    e.app
        .post_as_device("/v1/ingest", &e.token, payload(10, 60_000, 30))
        .await;
    e.app
        .post_as_device("/v1/ingest", &e.token, payload(10, 90_000, 60))
        .await;

    let rows: i64 = sqlx::query_scalar("SELECT count(*) FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    let ms: i64 = sqlx::query_scalar("SELECT foreground_ms FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(rows, 1);
    assert_eq!(ms, 90_000, "the newer computation replaces the partial one");
}

#[sqlx::test]
async fn an_older_computation_cannot_clobber_a_newer_one(pool: PgPool) {
    let e = enrolled(pool.clone()).await;
    e.app
        .post_as_device("/v1/ingest", &e.token, payload(10, 90_000, 60))
        .await;
    // A retry of an earlier computation arrives late.
    e.app
        .post_as_device("/v1/ingest", &e.token, payload(10, 60_000, 30))
        .await;

    let ms: i64 = sqlx::query_scalar("SELECT foreground_ms FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(ms, 90_000, "computed_at guard must reject the stale write");
}

#[sqlx::test]
async fn a_future_hour_is_rejected_permanently_while_the_rest_is_accepted(pool: PgPool) {
    let e = enrolled(pool).await;
    let good = Utc::now() - Duration::hours(2);
    let bad = Utc::now() + Duration::hours(5);
    let body = serde_json::json!({
        "schema": 1,
        "device_time": Utc::now(),
        "hours": [
            { "hour_start": good, "tz": "Europe/Zurich", "computed_at": Utc::now(),
              "screen_on_ms": 1000, "unlock_count": 1,
              "apps": [{ "package": "com.a", "label": "A",
                         "foreground_ms": 1000, "launch_count": 1 }] },
            { "hour_start": bad, "tz": "Europe/Zurich", "computed_at": Utc::now(),
              "screen_on_ms": 1000, "unlock_count": 1, "apps": [] }
        ]
    });

    let response = e.app.post_as_device("/v1/ingest", &e.token, body).await;
    assert_eq!(
        response.status,
        StatusCode::OK,
        "one bad row must not fail the batch"
    );
    assert_eq!(response.json["accepted"].as_array().unwrap().len(), 1);
    let rejected = &response.json["rejected"].as_array().unwrap()[0];
    assert_eq!(
        rejected["permanent"], true,
        "the agent must drop this, not retry forever"
    );
}

#[sqlx::test]
async fn an_unknown_timezone_is_rejected_permanently(pool: PgPool) {
    let e = enrolled(pool).await;
    let mut body = payload(10, 1000, 60);
    body["hours"][0]["tz"] = serde_json::json!("Mars/Olympus");

    let response = e.app.post_as_device("/v1/ingest", &e.token, body).await;
    assert!(response.json["accepted"].as_array().unwrap().is_empty());
    assert_eq!(
        response.json["rejected"].as_array().unwrap()[0]["permanent"],
        true
    );
}

#[sqlx::test]
async fn an_unsupported_schema_version_is_a_400(pool: PgPool) {
    let e = enrolled(pool).await;
    let mut body = payload(10, 1000, 60);
    body["schema"] = serde_json::json!(99);
    assert_eq!(
        e.app
            .post_as_device("/v1/ingest", &e.token, body)
            .await
            .status,
        StatusCode::BAD_REQUEST
    );
}

#[sqlx::test]
async fn too_many_hours_is_a_413(pool: PgPool) {
    let e = enrolled(pool).await;
    let hours: Vec<serde_json::Value> = (0..501)
        .map(|i| {
            serde_json::json!({
                "hour_start": Utc::now() - Duration::hours(i + 1),
                "tz": "Europe/Zurich", "computed_at": Utc::now(),
                "screen_on_ms": 0, "unlock_count": 0, "apps": []
            })
        })
        .collect();
    let body = serde_json::json!({ "schema": 1, "device_time": Utc::now(), "hours": hours });
    assert_eq!(
        e.app
            .post_as_device("/v1/ingest", &e.token, body)
            .await
            .status,
        StatusCode::PAYLOAD_TOO_LARGE
    );
}

#[sqlx::test]
async fn ingest_updates_last_seen_and_package_labels(pool: PgPool) {
    let e = enrolled(pool.clone()).await;
    e.app
        .post_as_device("/v1/ingest", &e.token, payload(10, 1000, 60))
        .await;

    let last_seen: Option<chrono::DateTime<Utc>> =
        sqlx::query_scalar("SELECT last_seen_at FROM devices")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert!(last_seen.is_some());

    let label: String = sqlx::query_scalar("SELECT label FROM packages WHERE package = 'com.a'")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(label, "App A");
}

#[sqlx::test]
async fn ingest_without_a_valid_token_is_401(pool: PgPool) {
    let e = enrolled(pool).await;
    assert_eq!(
        e.app
            .post_as_device("/v1/ingest", "not-a-real-token", payload(10, 1000, 60))
            .await
            .status,
        StatusCode::UNAUTHORIZED
    );
}

#[sqlx::test]
async fn a_revoked_device_cannot_ingest(pool: PgPool) {
    let e = enrolled(pool).await;
    let devices = e.app.get("/v1/devices").await;
    let device_id = devices.json[0]["id"].as_str().unwrap().to_string();
    assert_eq!(
        e.app
            .delete(&format!("/v1/devices/{device_id}"))
            .await
            .status,
        StatusCode::NO_CONTENT
    );

    assert_eq!(
        e.app
            .post_as_device("/v1/ingest", &e.token, payload(10, 1000, 60))
            .await
            .status,
        StatusCode::UNAUTHORIZED
    );
    let _ = e.child_id;
}

#[sqlx::test]
async fn a_device_hits_its_own_rate_limit(pool: PgPool) {
    let e = enrolled(pool).await;
    let mut statuses = Vec::new();
    for i in 0..130 {
        let response = e
            .app
            .post_as_device("/v1/ingest", &e.token, payload(10, 1000, 60 + i))
            .await;
        statuses.push(response.status);
    }
    assert!(
        statuses.contains(&StatusCode::TOO_MANY_REQUESTS),
        "expected a 429 within 130 requests"
    );
}
