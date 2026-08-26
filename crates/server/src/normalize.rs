//! The last word on what an error response looks like.
//!
//! Two jobs. First, fill in the reference: `ApiError::into_response` builds the
//! body but cannot reach the request, and threading an extractor through every
//! handler to carry one string would be a tax on every future route.
//!
//! Second, and this is the one that makes "every error carries a code" true
//! rather than aspirational: axum produces error responses nobody wrote. The
//! SPA fallback's 404 on an unknown `/v1` path, a 405, a body over the limit, a
//! JSON body that will not deserialise — all of those left the server as a bare
//! status with no body. A client could report nothing about them, which is
//! exactly when a parent most needs something to report.

use axum::extract::Request;
use axum::http::{StatusCode, header};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use http_body_util::BodyExt;
use schirmziit_core::codes::ErrorCode;

use crate::error::Problem;
use crate::request_id::RequestRef;

/// Only API paths. A browser following a deep link gets the dashboard, and
/// handing it `application/problem+json` would turn a working page into a
/// download prompt.
fn is_api_path(path: &str) -> bool {
    path.starts_with("/v1/") || path == "/healthz"
}

fn code_for(status: StatusCode) -> ErrorCode {
    match status {
        StatusCode::NOT_FOUND => ErrorCode::NotFound,
        StatusCode::UNAUTHORIZED => ErrorCode::Unauthenticated,
        StatusCode::PAYLOAD_TOO_LARGE => ErrorCode::PayloadTooLarge,
        StatusCode::TOO_MANY_REQUESTS => ErrorCode::RateLimited,
        s if s.is_client_error() => ErrorCode::ValidationFailed,
        _ => ErrorCode::Internal,
    }
}

pub async fn normalize(request: Request, next: Next) -> Response {
    let request_ref = request.extensions().get::<RequestRef>().cloned();
    let short = request_ref
        .as_ref()
        .map(RequestRef::short)
        .unwrap_or_default();
    let path = request.uri().path().to_string();

    let response = next.run(request).await;
    let status = response.status();
    if status.is_success() || status.is_redirection() || !is_api_path(&path) {
        return response;
    }

    let is_problem = response
        .headers()
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .is_some_and(|v| v.starts_with("application/problem+json"));

    let (parts, body) = response.into_parts();
    let bytes = match body.collect().await {
        Ok(collected) => collected.to_bytes(),
        // A body that cannot even be read is itself an internal error, and
        // returning the unreadable thing helps nobody.
        Err(_) => Default::default(),
    };

    let problem = if is_problem {
        match serde_json::from_slice::<Problem>(&bytes) {
            Ok(mut problem) => {
                problem.r#ref = short;
                problem
            }
            Err(_) => fallback(status, short),
        }
    } else {
        fallback(status, short)
    };

    let mut rebuilt = (parts.status, axum::Json(problem)).into_response();
    // Keep whatever the inner response set — a WWW-Authenticate, a CORS grant —
    // and only overwrite what the new body dictates.
    for (name, value) in parts.headers.iter() {
        if name != header::CONTENT_TYPE && name != header::CONTENT_LENGTH {
            rebuilt.headers_mut().insert(name.clone(), value.clone());
        }
    }
    rebuilt.headers_mut().insert(
        header::CONTENT_TYPE,
        axum::http::HeaderValue::from_static("application/problem+json"),
    );
    rebuilt
}

fn fallback(status: StatusCode, short: String) -> Problem {
    let code = code_for(status);
    Problem {
        r#type: format!("https://schirmziit.ch/problems/{}", code.as_str()),
        title: status
            .canonical_reason()
            .unwrap_or("error")
            .to_lowercase()
            .replace(' ', "-"),
        status: status.as_u16(),
        // Never the upstream body: a rejection message from a deserialiser can
        // quote the payload, and the payload is a family's data.
        detail: "request failed".to_string(),
        code,
        r#ref: short,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The integration test for this cannot catch a mistake here: in a checkout
    /// where `web/dist` is built, a deep link answers 200 and never reaches the
    /// path check at all. This is where the rule is actually pinned down.
    #[test]
    fn only_api_paths_are_normalised() {
        assert!(is_api_path("/v1/children"));
        assert!(is_api_path("/healthz"));
        assert!(!is_api_path("/children/some-id"));
        assert!(!is_api_path("/"));
        assert!(!is_api_path("/assets/index-abc123.js"));
    }

    /// A 404 on an API path is the catalog's not-found, not a validation
    /// failure — the client shows "that isn't here", not "check your input".
    #[test]
    fn statuses_map_to_the_codes_a_reader_would_expect() {
        assert_eq!(code_for(StatusCode::NOT_FOUND), ErrorCode::NotFound);
        assert_eq!(
            code_for(StatusCode::METHOD_NOT_ALLOWED),
            ErrorCode::ValidationFailed
        );
        assert_eq!(code_for(StatusCode::BAD_GATEWAY), ErrorCode::Internal);
    }
}
