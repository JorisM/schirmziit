//! One id per request, so an error a parent photographs can be found in the log.
//!
//! The header carries the whole uuid; the on-screen reference is its first six
//! characters. Six is enough to grep a family's own server — this is a search
//! key inside one log, not a globally unique identifier — and it is short
//! enough to read off a screenshot without transcription errors.
//!
//! The server does not trust an inbound `x-request-id`: `SetRequestIdLayer`
//! only fills the header when it is absent, and nothing in front of this
//! service sets one. If that ever changes, the log line prints the id in the
//! extension, which is the one this layer generated.

use axum::http::{HeaderName, Request};
use tower_http::request_id::{MakeRequestId, RequestId};

pub const HEADER: HeaderName = HeaderName::from_static("x-request-id");

/// Inserted into the request extensions so handlers and layers can reach it.
#[derive(Debug, Clone)]
pub struct RequestRef(pub String);

impl RequestRef {
    pub fn short(&self) -> String {
        self.0.chars().take(6).collect()
    }
}

#[derive(Clone, Default)]
pub struct MakeUuid;

impl MakeRequestId for MakeUuid {
    fn make_request_id<B>(&mut self, _request: &Request<B>) -> Option<RequestId> {
        let id = uuid::Uuid::new_v4().to_string();
        id.parse().ok().map(RequestId::new)
    }
}
