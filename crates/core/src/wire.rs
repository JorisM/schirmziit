use chrono::{DateTime, Utc};

pub const SCHEMA_VERSION: u32 = 1;

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestApp {
    pub package: String,
    pub label: String,
    pub foreground_ms: i64,
    pub launch_count: i32,
    /// Media playing with the screen off. Separate from `foreground_ms` in
    /// every consumer; never add the two together.
    #[serde(default)]
    pub background_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub struct IngestHour {
    pub hour_start: DateTime<Utc>,
    pub tz: String,
    pub computed_at: DateTime<Utc>,
    pub screen_on_ms: i64,
    pub unlock_count: i32,
    /// Whether this device could observe background playback at all for this
    /// hour. `false` is NOT "nothing played" — it is "we do not know": an
    /// iPhone, or an Android phone whose family declined the grant.
    #[serde(default)]
    pub background_measured: bool,
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_older_agents_body_still_parses_and_reads_as_not_measured() {
        // Agents in the field predate these fields. Their hours must keep
        // arriving, and must not claim a measured zero.
        let json = r#"{"schema":1,"device_time":"2026-08-21T12:00:00Z","hours":[
            {"hour_start":"2026-08-21T12:00:00Z","tz":"Europe/Zurich",
             "computed_at":"2026-08-21T12:30:00Z","screen_on_ms":1000,"unlock_count":1,
             "apps":[{"package":"com.a","label":"A","foreground_ms":1000,"launch_count":1}]}]}"#;
        let body: IngestRequest = serde_json::from_str(json).expect("old body still parses");
        assert!(!body.hours[0].background_measured);
        assert_eq!(body.hours[0].apps[0].background_ms, 0);
    }

    #[test]
    fn schema_version_does_not_move_for_an_additive_field() {
        // Bumping it would turn every deployed agent's body into a 400.
        assert_eq!(SCHEMA_VERSION, 1);
    }
}
