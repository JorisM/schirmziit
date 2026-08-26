mod helpers;
use axum::http::StatusCode;
use chrono::{DurationRound, Utc};
use helpers::TestApp;
use sqlx::PgPool;

#[sqlx::test]
async fn creates_and_lists_children(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let created = app
        .post_json("/v1/children", serde_json::json!({ "display_name": "Kid" }))
        .await;
    assert_eq!(created.status, StatusCode::CREATED);
    assert_eq!(created.json["display_name"], "Kid");

    let listed = app.get("/v1/children?tz=Europe/Zurich").await;
    assert_eq!(listed.json.as_array().unwrap().len(), 1);
}

#[sqlx::test]
async fn an_empty_display_name_is_refused(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let created = app
        .post_json("/v1/children", serde_json::json!({ "display_name": "   " }))
        .await;
    assert_eq!(created.status, StatusCode::UNPROCESSABLE_ENTITY);
}

#[sqlx::test]
async fn enrollment_code_is_human_typable_and_carries_the_public_url(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;

    let enrollment = app
        .post_json(
            &format!("/v1/children/{child_id}/enrollments"),
            serde_json::json!({}),
        )
        .await;
    assert_eq!(enrollment.status, StatusCode::CREATED);

    let code = enrollment.json["code"].as_str().unwrap();
    assert_eq!(code.len(), 8);
    assert!(
        code.chars()
            .all(|c| schirmziit_server::routes::children::ENROLL_ALPHABET.contains(c)),
        "code {code} contains ambiguous characters"
    );

    let qr = enrollment.json["qr_payload"].as_str().unwrap();
    assert_eq!(
        qr,
        format!("schirmziit://enroll?url=https://schirmziit.test&code={code}")
    );
    assert!(enrollment.json["expires_at"].is_string());
}

#[sqlx::test]
async fn enrollment_code_is_stored_hashed(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;
    let code = app.mint_code(&child_id).await;

    let stored: String = sqlx::query_scalar("SELECT code_hash FROM enrollments")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_ne!(stored, code);
}

#[sqlx::test]
async fn deleting_a_child_soft_deletes_and_hides_it(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;

    assert_eq!(
        app.delete(&format!("/v1/children/{child_id}")).await.status,
        StatusCode::NO_CONTENT
    );
    assert_eq!(
        app.get("/v1/children?tz=Europe/Zurich")
            .await
            .json
            .as_array()
            .unwrap()
            .len(),
        0
    );

    let deleted_at: Option<chrono::DateTime<chrono::Utc>> =
        sqlx::query_scalar("SELECT deleted_at FROM children")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert!(
        deleted_at.is_some(),
        "row must survive so historical usage stays attributable"
    );
}

#[sqlx::test]
async fn devices_list_reports_stale_and_revoked(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let (device_id, _) = app.enroll_device(&child_id).await;

    let devices = app.get("/v1/devices").await;
    assert_eq!(devices.json[0]["id"], device_id);
    assert_eq!(
        devices.json[0]["stale"], true,
        "a device that never synced is stale"
    );
    assert_eq!(devices.json[0]["revoked"], false);

    assert_eq!(
        app.delete(&format!("/v1/devices/{device_id}")).await.status,
        StatusCode::NO_CONTENT
    );
    let after = app.get("/v1/devices").await;
    assert_eq!(after.json[0]["revoked"], true);
}

#[sqlx::test]
async fn children_require_authentication(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    assert_eq!(
        app.get_anonymous("/v1/children").await.status,
        StatusCode::UNAUTHORIZED
    );
}

#[sqlx::test]
async fn a_parent_can_enrol_the_phone_they_are_holding_without_a_code(pool: PgPool) {
    // The one-app flow: sign in on the child's phone, pick the child, get a
    // device token. No code to read aloud and type on a phone keyboard.
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Emma").await;

    let claimed = app
        .post_json(
            &format!("/v1/children/{child_id}/devices"),
            serde_json::json!({
                "platform": "android", "model": "FP4", "label": "Emmas Fairphone"
            }),
        )
        .await;

    assert_eq!(claimed.status, StatusCode::CREATED, "{}", claimed.json);
    let token = claimed.json["token"].as_str().expect("a device token");
    assert_eq!(token.len(), 64, "32 random bytes, hex encoded");

    // Stored hashed, never raw — same as a code-based enrolment.
    let hashed: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM devices WHERE token_hash = $1 AND revoked_at IS NULL",
    )
    .bind(schirmziit_server::auth::hash_token(token))
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(hashed, 1);

    let raw: i64 = sqlx::query_scalar("SELECT count(*) FROM devices WHERE token_hash = $1")
        .bind(token)
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(raw, 0, "the raw token must never be stored");

    // And it works as a device identity straight away.
    let ingest = app
        .post_as_device(
            "/v1/ingest",
            token,
            serde_json::json!({ "schema": 1, "device_time": "2026-08-22T10:00:00Z", "hours": [] }),
        )
        .await;
    assert_eq!(ingest.status, StatusCode::OK, "{}", ingest.json);
}

#[sqlx::test]
async fn claiming_a_device_for_another_familys_child_is_a_404(pool: PgPool) {
    let ours = TestApp::registered(pool.clone()).await;
    let theirs = TestApp::registered(pool).await;
    let their_child = theirs.create_child("Someone else").await;

    let response = ours
        .post_json(
            &format!("/v1/children/{their_child}/devices"),
            serde_json::json!({ "platform": "ios", "model": "iPhone15,3", "label": "x" }),
        )
        .await;

    assert_eq!(
        response.status,
        StatusCode::NOT_FOUND,
        "another family's child must not be enrollable, and must look absent"
    );
}

#[sqlx::test]
async fn claiming_a_device_needs_a_parent_session(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Emma").await;

    // A device token is deliberately not enough: a phone must not be able to
    // enrol more phones.
    let (_, device_token) = app.enroll_device(&child_id).await;
    let as_device = app
        .post_as_device(
            &format!("/v1/children/{child_id}/devices"),
            &device_token,
            serde_json::json!({ "platform": "ios", "model": "iPhone15,3", "label": "x" }),
        )
        .await;
    assert_eq!(as_device.status, StatusCode::UNAUTHORIZED);
}

#[sqlx::test]
async fn an_empty_label_is_refused_when_claiming(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Emma").await;

    let response = app
        .post_json(
            &format!("/v1/children/{child_id}/devices"),
            serde_json::json!({ "platform": "android", "model": "FP4", "label": "  " }),
        )
        .await;

    assert_eq!(response.status, StatusCode::UNPROCESSABLE_ENTITY);
}

#[sqlx::test]
async fn listing_children_reports_todays_total_in_the_callers_zone(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;
    let (device_id, _) = app.enroll_device(&child_id).await;

    // An hour that is unambiguously inside today in Zurich: the current hour,
    // truncated. A fixed date would age out of "today" the day after it is written.
    let hour = Utc::now()
        .duration_trunc(chrono::Duration::hours(1))
        .unwrap();
    sqlx::query(
        "INSERT INTO usage_hours
           (device_id, package, hour_start, tz, foreground_ms, launch_count, computed_at)
         VALUES ($1, 'com.a', $2, 'Europe/Zurich', 90000, 1, now())",
    )
    .bind(device_id.parse::<uuid::Uuid>().unwrap())
    .bind(hour)
    .execute(&pool)
    .await
    .unwrap();

    let listed = app.get("/v1/children?tz=Europe/Zurich").await;
    assert_eq!(listed.status, StatusCode::OK);
    assert_eq!(listed.json[0]["today_ms"], 90000);
}

#[sqlx::test]
async fn a_child_with_no_usage_today_reports_zero_not_null(pool: PgPool) {
    // A null would render as an empty hero on the list; a quiet day is a real
    // zero and must read as one.
    let app = TestApp::registered(pool).await;
    app.create_child("Kid").await;

    let listed = app.get("/v1/children?tz=Europe/Zurich").await;
    assert_eq!(listed.json[0]["today_ms"], 0);
}

#[sqlx::test]
async fn an_unknown_timezone_is_refused(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let listed = app.get("/v1/children?tz=Mars/Olympus").await;
    assert_eq!(listed.status, StatusCode::UNPROCESSABLE_ENTITY);
}
