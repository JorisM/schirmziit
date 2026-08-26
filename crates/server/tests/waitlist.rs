//! The public waiting list: the one write on this API that no account owns.
//!
//! Two properties matter more than the happy path. Signing up twice must look
//! exactly like signing up once — anything else turns the form into an oracle
//! for "is this address on the list". And nothing on the API may read the list
//! back, because there is no parent it belongs to and therefore no tenancy
//! check that could scope it.

mod helpers;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use helpers::TestApp;
use schirmziit_server::config::Config;
use schirmziit_server::{AppState, app};
use sqlx::PgPool;
use tower::ServiceExt;

fn signup(email: &str, locale: &str) -> serde_json::Value {
    serde_json::json!({ "email": email, "locale": locale })
}

#[sqlx::test]
async fn a_signup_is_stored_with_the_language_it_came_from(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    let response = app
        .post_json("/v1/waitlist", signup("parent@example.com", "fr"))
        .await;
    assert_eq!(response.status, StatusCode::CREATED, "{}", response.json);

    let (email, locale): (String, String) =
        sqlx::query_as("SELECT email, locale FROM waitlist_signups")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(email, "parent@example.com");
    assert_eq!(locale, "fr");
}

#[sqlx::test]
async fn the_address_is_normalised_before_it_is_stored(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    let response = app
        .post_json("/v1/waitlist", signup("  Parent@Example.COM  ", "de"))
        .await;
    assert_eq!(response.status, StatusCode::CREATED, "{}", response.json);

    let email: String = sqlx::query_scalar("SELECT email FROM waitlist_signups")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(email, "parent@example.com");
}

#[sqlx::test]
async fn signing_up_twice_looks_exactly_like_signing_up_once(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    let first = app
        .post_json("/v1/waitlist", signup("parent@example.com", "de"))
        .await;
    // Different casing on purpose: normalisation is what makes the second call
    // hit the same row instead of adding a second spelling of one address.
    let second = app
        .post_json("/v1/waitlist", signup("PARENT@example.com", "fr"))
        .await;

    assert_eq!(first.status, second.status);
    assert_eq!(first.json, second.json);

    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM waitlist_signups")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 1, "a second signup must not add a row");

    // The first language wins: the person read the site in French later, but
    // overwriting would let anyone flip a stranger's row by guessing the address.
    let locale: String = sqlx::query_scalar("SELECT locale FROM waitlist_signups")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(locale, "de");
}

#[sqlx::test]
async fn something_that_is_not_an_email_is_rejected(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    for bad in ["", "parent", "parent@", "@example.com", "a b@example.com"] {
        let response = app.post_json("/v1/waitlist", signup(bad, "de")).await;
        assert_eq!(
            response.status,
            StatusCode::UNPROCESSABLE_ENTITY,
            "accepted {bad:?}"
        );
    }
    let count: i64 = sqlx::query_scalar("SELECT count(*) FROM waitlist_signups")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(count, 0);
}

#[sqlx::test]
async fn a_language_the_site_does_not_speak_is_rejected(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    let response = app
        .post_json("/v1/waitlist", signup("parent@example.com", "es"))
        .await;
    assert_eq!(response.status, StatusCode::UNPROCESSABLE_ENTITY);
}

#[sqlx::test]
async fn all_four_languages_are_accepted(pool: PgPool) {
    let app = TestApp::new(pool.clone());
    for locale in ["de", "fr", "it", "en"] {
        let response = app
            .post_json(
                "/v1/waitlist",
                signup(&format!("{locale}@example.com"), locale),
            )
            .await;
        assert_eq!(response.status, StatusCode::CREATED, "{}", response.json);
    }
}

#[sqlx::test]
async fn no_signed_in_parent_can_read_the_list_back(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    app.post_json("/v1/waitlist", signup("parent@example.com", "de"))
        .await;

    // There is no route, so this falls through to the static handler. What must
    // never happen is a 200 carrying the addresses.
    let response = app.get("/v1/waitlist").await;
    assert_ne!(response.status, StatusCode::OK);
    assert!(
        !response.json.to_string().contains("parent@example.com"),
        "the waiting list must not be readable: {}",
        response.json
    );
}

#[sqlx::test]
async fn the_signup_needs_no_session(pool: PgPool) {
    // The whole point: a stranger on the public site, no cookie, no token.
    let router = app(AppState::new(pool.clone(), Config::for_tests()));
    let response = router
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/v1/waitlist")
                .header(header::CONTENT_TYPE, "application/json")
                .body(Body::from(signup("parent@example.com", "en").to_string()))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(response.status(), StatusCode::CREATED);
}
