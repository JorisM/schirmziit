#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Registration {
    FirstUserOnly,
    Open,
    Off,
}

#[derive(Debug, Clone)]
pub struct Config {
    pub public_url: String,
    /// Origins allowed to call this API from a browser with credentials.
    ///
    /// Empty by default, and that default is load-bearing: a self-hosted
    /// instance serves the dashboard and the API from one origin and needs no
    /// grant at all. Hosted, the dashboard lives on `app.` and the API on
    /// `api.`, which are two origins to a browser.
    pub dashboard_origins: Vec<String>,
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
            dashboard_origins: parse_origins(
                &std::env::var("DASHBOARD_ORIGINS").unwrap_or_default(),
            )?,
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
            dashboard_origins: Vec::new(),
            allow_registration: Registration::Open,
            session_ttl_days: 30,
            retention_hourly_months: 13,
            retention_job_at: "04:00".into(),
        }
    }
}

/// Splits `DASHBOARD_ORIGINS` on commas and rejects anything that is not a bare
/// scheme+host origin. A value carrying a path or a trailing slash never
/// matches a browser's `Origin` header, so it would silently grant nothing —
/// failing at startup instead is the difference between a five-minute fix and
/// an afternoon of chasing a CORS error.
fn parse_origins(raw: &str) -> Result<Vec<String>, String> {
    raw.split(',')
        .map(str::trim)
        .filter(|o| !o.is_empty())
        .map(|o| {
            let rest = o
                .strip_prefix("https://")
                .or_else(|| o.strip_prefix("http://"))
                .ok_or_else(|| format!("DASHBOARD_ORIGINS entry needs a scheme: {o}"))?;
            if rest.is_empty() || rest.contains('/') {
                return Err(format!(
                    "DASHBOARD_ORIGINS entry must be scheme://host[:port] with no path: {o}"
                ));
            }
            Ok(o.to_string())
        })
        .collect()
}

fn parse_env(key: &str, default: i64) -> Result<i64, String> {
    match std::env::var(key) {
        Ok(v) => v.parse().map_err(|_| format!("invalid {key}: {v}")),
        Err(_) => Ok(default),
    }
}
