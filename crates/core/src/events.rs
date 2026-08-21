use chrono::{DateTime, Utc};

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum EventKind {
    Resumed { package: String },
    Paused { package: String },
    ScreenOff,
    Unlock,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct RawEvent {
    pub at: DateTime<Utc>,
    pub kind: EventKind,
}
