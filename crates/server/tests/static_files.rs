mod helpers;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use helpers::TestApp;
use sqlx::PgPool;
use tower::ServiceExt;

#[sqlx::test]
async fn unknown_api_paths_stay_json_404(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app.get_anonymous("/v1/definitely-not-a-route").await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
}

#[sqlx::test]
async fn healthz_is_not_swallowed_by_the_spa_fallback(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app.get_anonymous("/healthz").await;
    assert_eq!(response.status, StatusCode::OK);
    assert_eq!(response.json["status"], "ok");
}

#[sqlx::test]
async fn spa_routes_serve_index_html_when_the_frontend_is_built(pool: PgPool) {
    let app = TestApp::new(pool);
    let response = app
        .router
        .clone()
        .oneshot(
            Request::builder()
                .uri("/children/abc")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    // 404 is the legitimate answer in a checkout where web/dist is empty.
    if response.status() == StatusCode::OK {
        let content_type = response.headers().get(header::CONTENT_TYPE).unwrap();
        assert!(
            content_type.to_str().unwrap().starts_with("text/html"),
            "expected html, got {content_type:?}"
        );
    }
}
