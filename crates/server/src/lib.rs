pub mod auth;
pub mod config;
pub mod db;
pub mod error;
pub mod routes;

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
}

impl AppState {
    pub fn new(pool: PgPool, config: config::Config) -> Self {
        Self {
            pool,
            config,
            ingest_limits: Default::default(),
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
        let config = Arc::new(
            GovernorConfigBuilder::default()
                .per_second(2)
                .burst_size(10)
                .key_extractor(SmartIpKeyExtractor)
                .finish()
                .expect("valid governor config"),
        );
        auth::routes::router().layer(GovernorLayer { config })
    } else {
        auth::routes::router()
    };

    Router::new()
        .route("/healthz", get(healthz))
        .merge(auth_routes)
        .merge(routes::children::router())
        .merge(routes::enroll::router())
        .with_state(state)
}

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok", "version": env!("CARGO_PKG_VERSION") }))
}
