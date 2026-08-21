use super::{Parent, SESSION_COOKIE, hash_password, hash_token, random_token, verify_password};
use crate::AppState;
use crate::config::Registration;
use crate::error::ApiError;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::{
    Json, Router,
    routing::{get, post},
};
use axum_extra::extract::CookieJar;
use axum_extra::extract::cookie::{Cookie, SameSite};
use chrono::{Duration, Utc};
use uuid::Uuid;

#[derive(serde::Deserialize, utoipa::ToSchema)]
pub struct Credentials {
    pub email: String,
    pub password: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct RegisteredResponse {
    pub family_id: Uuid,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct MeResponse {
    pub id: Uuid,
    pub email: String,
    pub family_id: Uuid,
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/auth/register", post(register))
        .route("/v1/auth/login", post(login))
        .route("/v1/auth/logout", post(logout))
        .route("/v1/me", get(me))
}

#[utoipa::path(
    post, path = "/v1/auth/register", request_body = Credentials,
    responses(
        (status = 201, description = "Family and first parent created", body = RegisteredResponse),
        (status = 403, description = "Registration disabled"),
        (status = 409, description = "Email already registered"),
        (status = 422, description = "Password too short"),
    ),
    tag = "auth"
)]
pub async fn register(
    State(state): State<AppState>,
    Json(body): Json<Credentials>,
) -> Result<impl IntoResponse, ApiError> {
    if body.password.len() < 12 {
        return Err(ApiError::Validation(
            "password must be at least 12 characters".into(),
        ));
    }

    let existing: i64 = sqlx::query_scalar!("SELECT count(*) FROM parents")
        .fetch_one(&state.pool)
        .await?
        .unwrap_or(0);

    match state.config.allow_registration {
        Registration::Off => return Err(ApiError::RegistrationDisabled),
        Registration::FirstUserOnly if existing > 0 => {
            return Err(ApiError::RegistrationDisabled);
        }
        _ => {}
    }

    let family_id = Uuid::new_v4();
    let parent_id = Uuid::new_v4();
    let mut tx = state.pool.begin().await?;

    sqlx::query!(
        "INSERT INTO families (id, name) VALUES ($1, $2)",
        family_id,
        "Family"
    )
    .execute(&mut *tx)
    .await?;

    let insert = sqlx::query!(
        "INSERT INTO parents (id, family_id, email, password_hash) VALUES ($1, $2, $3, $4)",
        parent_id,
        family_id,
        body.email,
        hash_password(&body.password)?
    )
    .execute(&mut *tx)
    .await;

    if let Err(sqlx::Error::Database(err)) = &insert
        && err.is_unique_violation()
    {
        return Err(ApiError::EmailTaken);
    }
    insert?;
    tx.commit().await?;

    let jar = issue_session(&state, parent_id).await?;
    Ok((
        StatusCode::CREATED,
        jar,
        Json(RegisteredResponse { family_id }),
    ))
}

#[utoipa::path(
    post, path = "/v1/auth/login", request_body = Credentials,
    responses(
        (status = 200, description = "Session cookie issued"),
        (status = 401, description = "Invalid credentials"),
        (status = 429, description = "Too many attempts from this IP"),
    ),
    tag = "auth"
)]
pub async fn login(
    State(state): State<AppState>,
    Json(body): Json<Credentials>,
) -> Result<impl IntoResponse, ApiError> {
    let parent = sqlx::query!(
        "SELECT id, password_hash FROM parents WHERE email = $1",
        body.email
    )
    .fetch_optional(&state.pool)
    .await?
    .ok_or(ApiError::InvalidCredentials)?;

    if !verify_password(&body.password, &parent.password_hash) {
        return Err(ApiError::InvalidCredentials);
    }

    let jar = issue_session(&state, parent.id).await?;
    Ok((StatusCode::OK, jar, Json(serde_json::json!({ "ok": true }))))
}

#[utoipa::path(
    post, path = "/v1/auth/logout",
    responses((status = 204, description = "Session deleted")),
    tag = "auth"
)]
pub async fn logout(State(state): State<AppState>, jar: CookieJar) -> Result<StatusCode, ApiError> {
    if let Some(raw) = jar.get(SESSION_COOKIE) {
        sqlx::query!(
            "DELETE FROM sessions WHERE token_hash = $1",
            hash_token(raw.value())
        )
        .execute(&state.pool)
        .await?;
    }
    Ok(StatusCode::NO_CONTENT)
}

#[utoipa::path(
    get, path = "/v1/me",
    responses(
        (status = 200, description = "The signed-in parent", body = MeResponse),
        (status = 401, description = "Not authenticated"),
    ),
    tag = "auth"
)]
pub async fn me(parent: Parent) -> Json<MeResponse> {
    Json(MeResponse {
        id: parent.id,
        email: parent.email,
        family_id: parent.family_id,
    })
}

async fn issue_session(state: &AppState, parent_id: Uuid) -> Result<CookieJar, ApiError> {
    let token = random_token();
    sqlx::query!(
        "INSERT INTO sessions (token_hash, parent_id, expires_at) VALUES ($1, $2, $3)",
        hash_token(&token),
        parent_id,
        Utc::now() + Duration::days(state.config.session_ttl_days)
    )
    .execute(&state.pool)
    .await?;

    // Secure only when the instance is actually served over TLS. Hard-coding
    // `true` means a self-hoster on http://localhost (or anyone doing local dev)
    // can never log in: the browser stores the cookie and then refuses to send
    // it back. Production sets PUBLIC_URL to https, so this stays Secure there.
    let over_tls = state.config.public_url.starts_with("https://");
    let cookie = Cookie::build((SESSION_COOKIE, token))
        .http_only(true)
        .secure(over_tls)
        .same_site(SameSite::Lax)
        .path("/")
        .max_age(time::Duration::days(state.config.session_ttl_days))
        .build();

    Ok(CookieJar::new().add(cookie))
}
