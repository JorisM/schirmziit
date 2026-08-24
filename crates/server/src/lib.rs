pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod openapi;
pub mod retention;
pub mod routes;
pub mod static_files;

use axum::{Json, Router, routing::get};
use chrono::{DateTime, Utc};
use sqlx::PgPool;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tower_governor::GovernorLayer;
use tower_governor::governor::GovernorConfigBuilder;
use tower_governor::key_extractor::SmartIpKeyExtractor;
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
            .layer(GovernorLayer { config: strict })
            .merge(auth::routes::session_router().layer(GovernorLayer { config: loose }))
    } else {
        auth::routes::router()
    };

    Router::new()
        .route("/healthz", get(healthz))
        .merge(auth_routes)
        .merge(routes::children::router())
        .merge(routes::enroll::router())
        .merge(routes::ingest::router())
        .merge(routes::usage::router())
        .merge(routes::purge::router())
        .fallback(static_files::handler)
        .with_state(state)
}

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok", "version": env!("CARGO_PKG_VERSION") }))
}
