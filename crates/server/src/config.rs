#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Registration {
    FirstUserOnly,
    Open,
    Off,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub public_url: String,
    pub allow_registration: Registration,
    pub session_ttl_days: i64,
    pub retention_hourly_months: i64,
    pub retention_job_at: String,
}

impl Config {
    /// Reads the environment. `PUBLIC_URL` is required: it is baked into every
    /// enrollment QR, so a wrong value produces devices that pair once and then
    /// never sync again.
    pub fn from_env() -> Result<Self, String> {
        Ok(Self {
            public_url: std::env::var("PUBLIC_URL").map_err(|_| "PUBLIC_URL is required")?,
            allow_registration: match std::env::var("ALLOW_REGISTRATION").as_deref() {
                Ok("open") => Registration::Open,
                Ok("off") => Registration::Off,
                Ok("first-user-only") | Err(_) => Registration::FirstUserOnly,
                Ok(other) => return Err(format!("invalid ALLOW_REGISTRATION: {other}")),
            },
            session_ttl_days: parse_env("SESSION_TTL_DAYS", 30)?,
            retention_hourly_months: parse_env("RETENTION_HOURLY_MONTHS", 13)?,
            retention_job_at: std::env::var("RETENTION_JOB_AT").unwrap_or_else(|_| "04:00".into()),
        })
    }

    /// Test default: open registration, everything else as production.
    pub fn for_tests() -> Self {
        Self {
            public_url: "https://schirmziit.test".into(),
            allow_registration: Registration::Open,
            session_ttl_days: 30,
            retention_hourly_months: 13,
            retention_job_at: "04:00".into(),
        }
    }
}

fn parse_env(key: &str, default: i64) -> Result<i64, String> {
    match std::env::var(key) {
        Ok(v) => v.parse().map_err(|_| format!("invalid {key}: {v}")),
        Err(_) => Ok(default),
    }
}
