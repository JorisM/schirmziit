mod helpers;
use axum::http::StatusCode;
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

    let listed = app.get("/v1/children").await;
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
        app.get("/v1/children").await.json.as_array().unwrap().len(),
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
