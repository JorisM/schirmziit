use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use schirmziit_core::codes::ErrorCode;

/// Every error the API can return. The `type` string is a stable contract:
/// clients match on it, so renaming one is a breaking API change.
#[derive(Debug, thiserror::Error)]
pub enum ApiError {
    #[error("not found")]
    NotFound,
    #[error("invalid credentials")]
    InvalidCredentials,
    #[error("authentication required")]
    Unauthenticated,
    #[error("registration is disabled")]
    RegistrationDisabled,
    #[error("email already registered")]
    EmailTaken,
    #[error("payload too large")]
    PayloadTooLarge,
    #[error("unsupported schema version: {0}")]
    UnsupportedSchema(u32),
    #[error("rate limit exceeded")]
    RateLimited,
    #[error("validation failed: {0}")]
    Validation(String),
    #[error(transparent)]
    Database(#[from] sqlx::Error),
}

impl ApiError {
    fn parts(&self) -> (StatusCode, &'static str) {
        match self {
            Self::NotFound => (StatusCode::NOT_FOUND, "not-found"),
            Self::InvalidCredentials => (StatusCode::UNAUTHORIZED, "invalid-credentials"),
            Self::Unauthenticated => (StatusCode::UNAUTHORIZED, "unauthenticated"),
            Self::RegistrationDisabled => (StatusCode::FORBIDDEN, "registration-disabled"),
            Self::EmailTaken => (StatusCode::CONFLICT, "email-taken"),
            Self::PayloadTooLarge => (StatusCode::PAYLOAD_TOO_LARGE, "payload-too-large"),
            Self::UnsupportedSchema(_) => (StatusCode::BAD_REQUEST, "unsupported-schema"),
            Self::RateLimited => (StatusCode::TOO_MANY_REQUESTS, "rate-limited"),
            Self::Validation(_) => (StatusCode::UNPROCESSABLE_ENTITY, "validation-failed"),
            Self::Database(_) => (StatusCode::INTERNAL_SERVER_ERROR, "internal"),
        }
    }

    /// The catalog code for this failure. The `type` slug above stays as it is
    /// — it is an older contract and clients may already match on it — so a
    /// response carries both.
    pub fn code(&self) -> ErrorCode {
        match self {
            Self::NotFound => ErrorCode::NotFound,
            Self::InvalidCredentials => ErrorCode::InvalidCredentials,
            Self::Unauthenticated => ErrorCode::Unauthenticated,
            Self::RegistrationDisabled => ErrorCode::RegistrationDisabled,
            Self::EmailTaken => ErrorCode::EmailTaken,
            Self::PayloadTooLarge => ErrorCode::PayloadTooLarge,
            Self::UnsupportedSchema(_) => ErrorCode::UnsupportedSchema,
            Self::RateLimited => ErrorCode::RateLimited,
            Self::Validation(_) => ErrorCode::ValidationFailed,
            Self::Database(_) => ErrorCode::Internal,
        }
    }
}

/// The body of every error response.
///
/// `detail` is for the log and the copy-details block a parent can send. It is
/// never rendered as the message a parent reads: it is English, and the app
/// speaks four languages. The client looks the copy up by `code`.
#[derive(Debug, serde::Serialize, serde::Deserialize, utoipa::ToSchema)]
pub struct Problem {
    #[serde(rename = "type")]
    pub r#type: String,
    pub title: String,
    pub status: u16,
    pub detail: String,
    pub code: ErrorCode,
    /// Six hex characters, the head of the request id. Filled in by the
    /// normalise layer, which is the only place with access to the request.
    #[serde(rename = "ref")]
    pub r#ref: String,
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, kind) = self.parts();
        // Not logged here: the normalise layer writes one line per failed
        // request, and unlike this spot it knows the reference and the path.
        let code = self.code();
        let detail = match self {
            // Never leak database internals to a client.
            Self::Database(_) => "internal error".to_string(),
            other => other.to_string(),
        };
        let body = Problem {
            r#type: format!("https://schirmziit.ch/problems/{kind}"),
            title: kind.to_string(),
            status: status.as_u16(),
            detail,
            code,
            // The normalise layer fills this in; empty here means that layer is
            // missing, which its own test catches.
            r#ref: String::new(),
        };
        let mut response = (status, axum::Json(body)).into_response();
        response.headers_mut().insert(
            axum::http::header::CONTENT_TYPE,
            axum::http::HeaderValue::from_static("application/problem+json"),
        );
        response
    }
}
