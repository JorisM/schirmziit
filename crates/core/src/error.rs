#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum CoreError {
    #[error("unknown timezone: {0}")]
    UnknownTimezone(String),
    #[error("malformed json: {0}")]
    BadJson(String),
}
