use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use nestling_server::config::{Config, Registration};
use nestling_server::{AppState, app};
use sqlx::PgPool;
use tower::ServiceExt;

fn state(pool: PgPool, registration: Registration) -> AppState {
    let mut config = Config::for_tests();
    config.allow_registration = registration;
    AppState::new(pool, config)
}

fn post(uri: &str, body: serde_json::Value) -> Request<Body> {
    Request::builder()
        .method("POST")
        .uri(uri)
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(body.to_string()))
        .unwrap()
}

fn credentials(email: &str) -> serde_json::Value {
    serde_json::json!({ "email": email, "password": "correct horse battery staple" })
}

#[sqlx::test]
async fn register_creates_a_family_and_sets_a_session_cookie(pool: PgPool) {
    let router = app(state(pool, Registration::Open));
    let response = router
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::CREATED);
    let cookie = response
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap();
    assert!(cookie.contains("nestling_session="));
    assert!(
        cookie.contains("HttpOnly"),
        "session cookie must not be readable by JS"
    );
    assert!(cookie.contains("SameSite=Lax"));
    assert!(cookie.contains("Secure"));
}

#[sqlx::test]
async fn password_is_not_stored_in_plaintext(pool: PgPool) {
    let router = app(state(pool.clone(), Registration::Open));
    router
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();

    let hash: String = sqlx::query_scalar("SELECT password_hash FROM parents")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert!(
        hash.starts_with("$argon2id$"),
        "expected argon2id, got {hash}"
    );
}

#[sqlx::test]
async fn second_registration_is_refused_in_first_user_only_mode(pool: PgPool) {
    let router = app(state(pool.clone(), Registration::FirstUserOnly));
    let first = router
        .clone()
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();
    assert_eq!(first.status(), StatusCode::CREATED);

    let second = router
        .oneshot(post("/v1/auth/register", credentials("b@example.com")))
        .await
        .unwrap();
    assert_eq!(second.status(), StatusCode::FORBIDDEN);
    assert_eq!(
        second.headers().get(header::CONTENT_TYPE).unwrap(),
        "application/problem+json"
    );
}

#[sqlx::test]
async fn login_with_a_wrong_password_is_401(pool: PgPool) {
    let router = app(state(pool, Registration::Open));
    router
        .clone()
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();

    let response = router
        .oneshot(post(
            "/v1/auth/login",
            serde_json::json!({ "email": "a@example.com", "password": "wrong" }),
        ))
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}

#[sqlx::test]
async fn me_requires_a_session(pool: PgPool) {
    let router = app(state(pool, Registration::Open));
    let response = router
        .oneshot(
            Request::builder()
                .uri("/v1/me")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::UNAUTHORIZED);
}

#[sqlx::test]
async fn me_returns_the_logged_in_parent(pool: PgPool) {
    let router = app(state(pool, Registration::Open));
    let registered = router
        .clone()
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();
    let cookie = registered
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .clone();

    let response = router
        .oneshot(
            Request::builder()
                .uri("/v1/me")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();

    assert_eq!(response.status(), StatusCode::OK);
    let body = response.into_body().collect().await.unwrap().to_bytes();
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(json["email"], "a@example.com");
    assert!(json["family_id"].is_string());
}

#[sqlx::test]
async fn session_token_is_stored_hashed_not_raw(pool: PgPool) {
    let router = app(state(pool.clone(), Registration::Open));
    let registered = router
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();

    let cookie = registered
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap();
    let raw = cookie
        .split("nestling_session=")
        .nth(1)
        .unwrap()
        .split(';')
        .next()
        .unwrap();

    let stored: String = sqlx::query_scalar("SELECT token_hash FROM sessions")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_ne!(stored, raw, "the raw token must never be stored");
    assert_eq!(stored.len(), 64, "sha256 hex");
}

#[sqlx::test]
async fn the_deployed_router_rate_limits_auth_attempts_per_ip(pool: PgPool) {
    // app_with_rate_limits is what main.rs serves; app() (used by every other
    // test) has no limiter so tests are not accidentally throttled.
    let router = nestling_server::app_with_rate_limits(state(pool, Registration::Open));

    let mut statuses = Vec::new();
    for _ in 0..25 {
        let request = Request::builder()
            .method("POST")
            .uri("/v1/auth/login")
            .header(header::CONTENT_TYPE, "application/json")
            .header("x-forwarded-for", "203.0.113.7")
            .body(Body::from(credentials("a@example.com").to_string()))
            .unwrap();
        statuses.push(router.clone().oneshot(request).await.unwrap().status());
    }

    assert!(
        statuses.contains(&StatusCode::TOO_MANY_REQUESTS),
        "expected a 429 within 25 rapid attempts, got {statuses:?}"
    );
}

#[sqlx::test]
async fn the_session_cookie_drops_secure_when_the_instance_is_not_on_tls(pool: PgPool) {
    // Otherwise local development and http-only self-hosting cannot log in at
    // all: the browser stores the cookie and refuses to send it back.
    let mut config = Config::for_tests();
    config.public_url = "http://localhost:8099".into();
    let router = app(AppState::new(pool, config));

    let response = router
        .oneshot(post("/v1/auth/register", credentials("a@example.com")))
        .await
        .unwrap();

    let cookie = response
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap();
    assert!(!cookie.contains("Secure"), "got {cookie}");
    assert!(cookie.contains("HttpOnly"), "HttpOnly must not be affected");
}
