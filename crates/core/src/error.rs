#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum CoreError {
    #[error("unknown timezone: {0}")]
    UnknownTimezone(String),
}
