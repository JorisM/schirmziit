mod helpers;
use axum::http::StatusCode;
use helpers::TestApp;
use sqlx::PgPool;

fn body(code: &str) -> serde_json::Value {
    serde_json::json!({ "code": code, "platform": "android", "model": "FP4", "label": "phone" })
}

#[sqlx::test]
async fn enrolling_returns_a_device_token(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let code = app.mint_code(&child_id).await;

    let response = app.post_as_device("/v1/enroll", "", body(&code)).await;

    assert_eq!(response.status, StatusCode::CREATED);
    assert_eq!(response.json["token"].as_str().unwrap().len(), 64);
    assert!(response.json["device_id"].is_string());
}

#[sqlx::test]
async fn a_code_works_exactly_once(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let code = app.mint_code(&child_id).await;

    assert_eq!(
        app.post_as_device("/v1/enroll", "", body(&code))
            .await
            .status,
        StatusCode::CREATED
    );
    assert_eq!(
        app.post_as_device("/v1/enroll", "", body(&code))
            .await
            .status,
        StatusCode::NOT_FOUND
    );
}

#[sqlx::test]
async fn an_expired_code_is_refused(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;
    let code = app.mint_code(&child_id).await;
    sqlx::query("UPDATE enrollments SET expires_at = now() - interval '1 minute'")
        .execute(&pool)
        .await
        .unwrap();

    assert_eq!(
        app.post_as_device("/v1/enroll", "", body(&code))
            .await
            .status,
        StatusCode::NOT_FOUND
    );
}

#[sqlx::test]
async fn a_wrong_code_is_refused(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    app.mint_code(&child_id).await;

    assert_eq!(
        app.post_as_device("/v1/enroll", "", body("WRONGCOD"))
            .await
            .status,
        StatusCode::NOT_FOUND
    );
}

#[sqlx::test]
async fn a_lowercase_code_still_works(pool: PgPool) {
    // Parents type these by hand; phone keyboards love to lowercase them.
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let code = app.mint_code(&child_id).await;

    assert_eq!(
        app.post_as_device("/v1/enroll", "", body(&code.to_lowercase()))
            .await
            .status,
        StatusCode::CREATED
    );
}

#[sqlx::test]
async fn the_device_token_is_stored_hashed(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;
    let (_, token) = app.enroll_device(&child_id).await;

    let stored: String = sqlx::query_scalar("SELECT token_hash FROM devices")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_ne!(stored, token);
    assert_eq!(stored.len(), 64);
}
