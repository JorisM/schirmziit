//! Cross-origin rules for the hosted split.
//!
//! Hosted, the dashboard is served from `app.schirmziit.ch` and the API answers
//! on `api.schirmziit.ch`. Those are two origins, so the browser will not send
//! the session cookie — or even make the request — unless the API names the
//! dashboard's origin and allows credentials. A self-hosted instance serves
//! both from one origin and must keep getting no CORS headers at all: an
//! allow-list that defaults to "something" would be a way to hand a stranger's
//! page a credentialed read of a family's data.

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use schirmziit_server::config::Config;
use schirmziit_server::{AppState, app};
use sqlx::PgPool;
use tower::ServiceExt;

const DASHBOARD: &str = "https://app.schirmziit.ch";

fn state(pool: PgPool, origins: &[&str]) -> AppState {
    let mut config = Config::for_tests();
    config.dashboard_origins = origins.iter().map(|o| (*o).to_string()).collect();
    AppState::new(pool, config)
}

fn preflight(origin: &str) -> Request<Body> {
    Request::builder()
        .method("OPTIONS")
        .uri("/v1/me")
        .header(header::ORIGIN, origin)
        .header(header::ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .header(header::ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
        .body(Body::empty())
        .unwrap()
}

fn get(uri: &str, origin: &str) -> Request<Body> {
    Request::builder()
        .method("GET")
        .uri(uri)
        .header(header::ORIGIN, origin)
        .body(Body::empty())
        .unwrap()
}

#[sqlx::test]
async fn preflight_from_the_dashboard_is_allowed_with_credentials(pool: PgPool) {
    let response = app(state(pool, &[DASHBOARD]))
        .oneshot(preflight(DASHBOARD))
        .await
        .unwrap();

    // The preflight is answered by the CORS layer, never by the route.
    assert!(response.status().is_success(), "{:?}", response.status());
    let headers = response.headers();
    assert_eq!(
        headers
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .map(|v| v.to_str().unwrap()),
        Some(DASHBOARD),
        "the dashboard origin must be echoed back verbatim, never `*`"
    );
    assert_eq!(
        headers
            .get(header::ACCESS_CONTROL_ALLOW_CREDENTIALS)
            .map(|v| v.to_str().unwrap()),
        Some("true"),
        "without this the browser drops the session cookie"
    );
    let methods = headers
        .get(header::ACCESS_CONTROL_ALLOW_METHODS)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    for method in ["GET", "POST", "DELETE"] {
        assert!(methods.contains(method), "{methods} is missing {method}");
    }
}

#[sqlx::test]
async fn preflight_from_an_unknown_origin_is_refused(pool: PgPool) {
    let response = app(state(pool, &[DASHBOARD]))
        .oneshot(preflight("https://evil.example"))
        .await
        .unwrap();

    assert!(
        response
            .headers()
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .is_none(),
        "an origin outside the allow-list must get no grant"
    );
}

#[sqlx::test]
async fn a_real_request_carries_the_grant(pool: PgPool) {
    let response = app(state(pool, &[DASHBOARD]))
        .oneshot(get("/v1/me", DASHBOARD))
        .await
        .unwrap();

    // Unauthenticated, so 401 — the point is that the browser is allowed to
    // *read* that 401 instead of seeing an opaque CORS failure.
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert_eq!(
        response
            .headers()
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .map(|v| v.to_str().unwrap()),
        Some(DASHBOARD),
    );
}

#[sqlx::test]
async fn a_self_hosted_instance_sends_no_cors_headers(pool: PgPool) {
    let response = app(state(pool, &[]))
        .oneshot(get("/v1/me", "https://evil.example"))
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
    assert!(
        response
            .headers()
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .is_none(),
        "no DASHBOARD_ORIGINS configured must mean no cross-origin grant to anyone"
    );
}

#[sqlx::test]
async fn more_than_one_origin_can_be_allowed(pool: PgPool) {
    // Cutover: the old host has to keep working while DNS and app builds catch
    // up, so the allow-list is a list.
    let old = "https://schirmziit.jorisda.ch";
    let response = app(state(pool, &[DASHBOARD, old]))
        .oneshot(get("/v1/me", old))
        .await
        .unwrap();

    assert_eq!(
        response
            .headers()
            .get(header::ACCESS_CONTROL_ALLOW_ORIGIN)
            .map(|v| v.to_str().unwrap()),
        Some(old),
    );
}
