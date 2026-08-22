use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

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
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, kind) = self.parts();
        if status == StatusCode::INTERNAL_SERVER_ERROR {
            tracing::error!(error = %self, "internal error");
        }
        let detail = match self {
            // Never leak database internals to a client.
            Self::Database(_) => "internal error".to_string(),
            other => other.to_string(),
        };
        let body = serde_json::json!({
            "type": format!("https://schirmziit.ch/problems/{kind}"),
            "title": kind,
            "status": status.as_u16(),
            "detail": detail,
        });
        let mut response = (status, axum::Json(body)).into_response();
        response.headers_mut().insert(
            axum::http::header::CONTENT_TYPE,
            axum::http::HeaderValue::from_static("application/problem+json"),
        );
        response
    }
}
