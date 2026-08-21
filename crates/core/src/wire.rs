use chrono::{DateTime, Utc};

pub const SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestApp {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
    pub launch_count: i32,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestHour {
    pub hour_start: DateTime<Utc>,
    pub tz: String,
    pub computed_at: DateTime<Utc>,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
    pub apps: Vec<IngestApp>,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestRequest {
    pub schema: u32,
    pub device_time: DateTime<Utc>,
    pub hours: Vec<IngestHour>,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct Rejected {
    pub hour_start: DateTime<Utc>,
    pub reason: String,
    pub permanent: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestResponse {
    pub accepted: Vec<DateTime<Utc>>,
    pub rejected: Vec<Rejected>,
}
