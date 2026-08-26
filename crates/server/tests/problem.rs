//! The shape of every error the API returns.
//!
//! A parent reads the code off a screenshot and a self-hoster greps the
//! reference out of the log. Both of those only work if every error response
//! carries both — including the ones axum produced without asking us.

mod helpers;
use axum::http::StatusCode;
use helpers::TestApp;
use sqlx::PgPool;

#[sqlx::test]
async fn an_unauthenticated_request_carries_its_code(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app.get("/v1/children").await;

    assert_eq!(response.status, StatusCode::UNAUTHORIZED);
    assert_eq!(response.json["code"], "SZ-E102");
    assert_eq!(response.json["status"], 401);
    assert!(
        response.json["ref"].as_str().is_some_and(|r| r.len() == 6),
        "every problem carries a 6-character reference: {}",
        response.json
    );
}

#[sqlx::test]
async fn bad_credentials_carry_their_code(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app
        .post_json(
            "/v1/auth/login",
            serde_json::json!({ "email": "nobody@example.com", "password": "wrong" }),
        )
        .await;

    assert_eq!(response.status, StatusCode::UNAUTHORIZED);
    assert_eq!(response.json["code"], "SZ-E101");
}

#[sqlx::test]
async fn the_problem_content_type_is_unchanged(pool: PgPool) {
    // `type` and `title` are a stable contract older clients may match on.
    let app = TestApp::new(pool);
    let response = app.get("/v1/children").await;
    assert_eq!(
        response.json["type"],
        "https://schirmziit.ch/problems/unauthenticated"
    );
    assert_eq!(response.json["title"], "unauthenticated");
}

#[sqlx::test]
async fn every_response_carries_a_request_id_header(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let response = app.get_raw("/v1/me").await;

    assert!(response.status().is_success());
    let header = response
        .headers()
        .get("x-request-id")
        .expect("x-request-id on a successful response")
        .to_str()
        .unwrap()
        .to_string();
    // A successful-but-slow request has to be traceable too, which is why this
    // is not limited to errors.
    assert_eq!(header.len(), 36, "a uuid, not {header}");
}

#[sqlx::test]
async fn the_body_reference_is_the_head_of_the_request_id(pool: PgPool) {
    let app = TestApp::new(pool);
    let (status, json, headers) = app.get_with_headers("/v1/children").await;

    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let request_id = headers.get("x-request-id").unwrap().to_str().unwrap();
    let short = json["ref"].as_str().unwrap();
    assert_eq!(
        short,
        &request_id[..6],
        "grepping the on-screen reference must find the log line"
    );
}
