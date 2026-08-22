use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use schirmziit_server::{AppState, app, config::Config};
use sqlx::PgPool;
use tower::ServiceExt;

#[sqlx::test]
async fn healthz_reports_ok(pool: PgPool) {
    let router = app(AppState::new(pool, Config::for_tests()));
    let response = router
        .oneshot(
            Request::builder()
                .uri("/healthz")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(json["status"], "ok");
    assert!(json["version"].is_string());
}

#[sqlx::test]
async fn migrations_created_the_usage_tables(pool: PgPool) {
    // Proves #[sqlx::test] applied ./migrations to the per-test database.
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM usage_hours")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
}
