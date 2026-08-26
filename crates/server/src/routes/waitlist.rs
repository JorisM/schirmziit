use crate::AppState;
use crate::auth::{is_plausible_email, normalise_email};
use crate::error::ApiError;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::{Json, Router, routing::post};

/// The languages the site speaks. Stored per signup so the "it is out" mail can
/// be written in the language the person actually read the page in — an address
/// with no language is an address that gets a German mail by default, which is
/// wrong for three quarters of Switzerland.
const LOCALES: [&str; 4] = ["de", "fr", "it", "en"];

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/waitlist", post(join))
}

#[derive(serde::Deserialize, utoipa::ToSchema)]
pub struct WaitlistRequest {
    pub email: String,
    /// One of `de`, `fr`, `it`, `en`.
    pub locale: String,
}

#[utoipa::path(
    post, path = "/v1/waitlist", request_body = WaitlistRequest,
    responses(
        (status = 201, description = "On the list — also the answer when the address was already on it"),
        (status = 422, description = "Not an email address, or a language the site does not speak"),
    ),
    tag = "waitlist"
)]
pub async fn join(
    State(state): State<AppState>,
    Json(body): Json<WaitlistRequest>,
) -> Result<impl IntoResponse, ApiError> {
    let email = normalise_email(&body.email);
    if !is_plausible_email(&email) {
        return Err(ApiError::Validation("that is not an email address".into()));
    }
    if !LOCALES.contains(&body.locale.as_str()) {
        return Err(ApiError::Validation("unknown language".into()));
    }

    // `DO NOTHING`, not an upsert and not a conflict error. A second signup has
    // to be indistinguishable from the first, or the form answers the question
    // "is this address on the list" for anyone who asks — and keeping the first
    // locale stops a stranger flipping someone else's language by guessing
    // their address.
    sqlx::query!(
        "INSERT INTO waitlist_signups (email, locale) VALUES ($1, $2)
         ON CONFLICT (email) DO NOTHING",
        email,
        body.locale
    )
    .execute(&state.pool)
    .await?;

    Ok(StatusCode::CREATED)
}
