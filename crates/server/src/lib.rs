pub mod config;
pub mod db;
pub mod error;

use axum::{Json, Router, routing::get};
use chrono::{DateTime, Utc};
use sqlx::PgPool;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
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

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/healthz", get(healthz))
        .with_state(state)
}

async fn healthz() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "ok", "version": env!("CARGO_PKG_VERSION") }))
}
