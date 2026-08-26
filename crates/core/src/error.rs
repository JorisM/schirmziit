use crate::codes::ErrorCode;

#[derive(Debug, thiserror::Error, PartialEq, Eq)]
pub enum CoreError {
    #[error("unknown timezone: {0}")]
    UnknownTimezone(String),
    #[error("malformed json: {0}")]
    BadJson(String),
}

impl CoreError {
    pub fn code(&self) -> ErrorCode {
        match self {
            CoreError::UnknownTimezone(_) => ErrorCode::CoreUnknownTimezone,
            CoreError::BadJson(_) => ErrorCode::CoreMalformedJson,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn core_errors_carry_their_codes() {
        assert_eq!(
            CoreError::UnknownTimezone("Mars/Olympus".into()).code(),
            ErrorCode::CoreUnknownTimezone
        );
        assert_eq!(
            CoreError::BadJson("{".into()).code(),
            ErrorCode::CoreMalformedJson
        );
    }
}
