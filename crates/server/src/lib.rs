pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod normalize;
pub mod openapi;
pub mod request_id;
pub mod retention;
pub mod routes;
pub mod static_files;

use axum::http::{HeaderValue, Method, header};
use axum::{Json, Router, routing::get};
use chrono::{DateTime, Utc};
use sqlx::PgPool;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tower_governor::GovernorLayer;
use tower_governor::governor::GovernorConfigBuilder;
use tower_governor::key_extractor::SmartIpKeyExtractor;
use tower_http::cors::{AllowOrigin, CorsLayer};
use tower_http::request_id::{PropagateRequestIdLayer, SetRequestIdLayer};
use uuid::Uuid;

/// Per-device ingest counters, kept in memory. A restart forgets them, which is
/// acceptable: the cap exists to stop a runaway client, not to bill anyone.
pub type IngestLimits = Arc<Mutex<HashMap<Uuid, (DateTime<Utc>, u32)>>>;

#[derive(Clone)]
pub struct AppState {
    pub pool: PgPool,
    pub config: config::Config,
    pub ingest_limits: IngestLimits,
    /// Same shape, separate budget: a child's screen refreshing on foreground
    /// must not eat into the ingest allowance, or a busy day stops reporting.
    pub read_limits: IngestLimits,
}

impl AppState {
    pub fn new(pool: PgPool, config: config::Config) -> Self {
        Self {
            pool,
            config,
            ingest_limits: Default::default(),
            read_limits: Default::default(),
        }
    }
}

/// Router without rate limiting. This is what tests drive.
pub fn app(state: AppState) -> Router {
    build(state, false)
}

/// Router as deployed: adds a per-IP limiter on the auth routes only.
/// Deliberately not on `/v1/ingest` — a family behind one NAT would throttle
/// itself, and device tokens are already capped per device.
pub fn app_with_rate_limits(state: AppState) -> Router {
    build(state, true)
}

fn build(state: AppState, rate_limit: bool) -> Router {
    let auth_routes = if rate_limit {
        // Two limiters, because these routes are not the same kind of thing.
        //
        // Password guessing gets the tight one: one attempt every 5 seconds
        // after a burst of 5, so a single IP manages ~17k tries a day instead of
        // ~170k. A parent typing their password wrong twice never notices.
        //
        // /v1/me and /v1/auth/logout get the loose one: the dashboard calls
        // /v1/me on every page load and on every tab focus, and throttling that
        // logs a parent out of a working session.
        let strict = Arc::new(
            GovernorConfigBuilder::default()
                .period(std::time::Duration::from_secs(5))
                .burst_size(5)
                .key_extractor(SmartIpKeyExtractor)
                .finish()
                .expect("valid governor config"),
        );
        let loose = Arc::new(
            GovernorConfigBuilder::default()
                .per_second(2)
                .burst_size(10)
                .key_extractor(SmartIpKeyExtractor)
                .finish()
                .expect("valid governor config"),
        );
        auth::routes::credential_router()
            .layer(GovernorLayer::new(strict))
            .merge(auth::routes::session_router().layer(GovernorLayer::new(loose)))
    } else {
        auth::routes::router()
    };

    // The waiting list is the only write on this API that no account owns, so a
    // per-IP limiter is the only thing standing between it and a script. One
    // address every 20 seconds after a burst of five: a rejected typo spends a
    // token too, and somebody correcting their address twice must not be locked
    // out for a minute. Three a minute sustained is still useless for filling a
    // table.
    let waitlist_routes = if rate_limit {
        let public = Arc::new(
            GovernorConfigBuilder::default()
                .period(std::time::Duration::from_secs(20))
                .burst_size(5)
                .key_extractor(SmartIpKeyExtractor)
                .finish()
                .expect("valid governor config"),
        );
        routes::waitlist::router().layer(GovernorLayer::new(public))
    } else {
        routes::waitlist::router()
    };

    // Everything a family's data flows through, behind the credentialed
    // allow-list. `Router::layer` wraps only what is already on the router, so
    // the waiting list merged afterwards keeps its own, different grant — and
    // gets exactly one, instead of two `Access-Control-Allow-Origin` headers
    // that a browser reads as none.
    let family_routes = Router::new()
        .route("/healthz", get(healthz))
        .merge(auth_routes)
        .merge(routes::children::router())
        .merge(routes::enroll::router())
        .merge(routes::ingest::router())
        .merge(routes::usage::router())
        .merge(routes::purge::router())
        .fallback(static_files::handler)
        .layer(cors_layer(&state.config.dashboard_origins));

    // Outermost, and in this order: the id must exist before anything reads it,
    // the extension is what everything downstream actually uses, and the
    // propagate layer copies the id onto the response once the handler is done.
    family_routes
        .merge(waitlist_routes.layer(waitlist_cors_layer()))
        .layer(PropagateRequestIdLayer::new(request_id::HEADER))
        .layer(axum::middleware::from_fn(normalize::normalize))
        .layer(axum::middleware::from_fn(insert_request_ref))
        .layer(SetRequestIdLayer::new(
            request_id::HEADER,
            request_id::MakeUuid,
        ))
        .with_state(state)
}

/// `SetRequestIdLayer` puts the id in the headers; everything downstream wants
/// it as a value, not a header lookup.
async fn insert_request_ref(
    mut request: axum::extract::Request,
    next: axum::middleware::Next,
) -> axum::response::Response {
    if let Some(id) = request
        .headers()
        .get(request_id::HEADER)
        .and_then(|v| v.to_str().ok())
        .map(|v| request_id::RequestRef(v.to_string()))
    {
        request.extensions_mut().insert(id);
    }
    next.run(request).await
}

/// The cross-origin grant for the hosted `app.` / `api.` split.
///
/// With no configured origins this is a permissive-to-nobody layer: it adds no
/// `Access-Control-Allow-Origin`, which is exactly right for a same-origin
/// self-hosted instance. `allow_credentials` is what makes the session cookie
/// travel, and it is also why the origin list can never be `Any` — the browser
/// rejects that pairing, and a wildcard here would let any page on the internet
/// read a signed-in parent's family data.
fn cors_layer(origins: &[String]) -> CorsLayer {
    let allowed: Vec<HeaderValue> = origins
        .iter()
        .filter_map(|o| HeaderValue::from_str(o).ok())
        .collect();

    CorsLayer::new()
        .allow_origin(AllowOrigin::list(allowed))
        .allow_credentials(true)
        .allow_methods([Method::GET, Method::POST, Method::DELETE, Method::OPTIONS])
        .allow_headers([header::CONTENT_TYPE, header::AUTHORIZATION])
        // Without this the dashboard can read the reference out of an error
        // body but not off a successful response, so a slow-but-working request
        // is untraceable from the browser.
        .expose_headers([request_id::HEADER])
}

/// The grant for the public waiting list, and only for it.
///
/// `Any` is safe here precisely because there are no credentials: the route
/// reads no cookie, no bearer token, and returns nothing about anyone. The
/// alternative — putting the marketing site into `DASHBOARD_ORIGINS` — would
/// hand a page that shows nobody's data a credentialed read of every family's,
/// which is a bad trade for one form.
fn waitlist_cors_layer() -> CorsLayer {
    CorsLayer::new()
        .allow_origin(AllowOrigin::any())
        .allow_methods([Method::POST, Method::OPTIONS])
        .allow_headers([header::CONTENT_TYPE])
}

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok", "version": env!("CARGO_PKG_VERSION") }))
}
