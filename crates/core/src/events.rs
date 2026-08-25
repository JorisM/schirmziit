use chrono::{DateTime, Utc};

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum EventKind {
    Resumed {
        package: String,
    },
    Paused {
        package: String,
    },
    ScreenOff,
    /// The screen became interactive. Foreground sessions ignore this — a
    /// RESUMED always follows — but a background listening stretch ends here.
    ScreenOn,
    Unlock,
    /// A media session for this package started playing. Package and instant
    /// only — no title, no artist, no artwork ever crosses this boundary.
    PlaybackStarted {
        package: String,
    },
    PlaybackStopped {
        package: String,
    },
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct RawEvent {
    pub at: DateTime<Utc>,
    pub kind: EventKind,
}
