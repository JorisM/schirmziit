use crate::AppState;
use crate::auth::{hash_token, random_token};
use crate::error::ApiError;
use axum::extract::{FromRequestParts, State};
use axum::http::StatusCode;
use axum::http::request::Parts;
use axum::response::IntoResponse;
use axum::{Json, Router, routing::post};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/enroll", post(enroll))
}

#[derive(serde::Deserialize, utoipa::ToSchema)]
pub struct EnrollRequest {
    pub code: String,
    pub platform: String,
    pub model: String,
    pub label: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct EnrolledResponse {
    pub device_id: Uuid,
    /// Long-lived, write-only. Shown once; only its hash is stored.
    pub token: String,
}

#[utoipa::path(
    post, path = "/v1/enroll", request_body = EnrollRequest,
    responses(
        (status = 201, description = "Device enrolled", body = EnrolledResponse),
        (status = 404, description = "Unknown, expired or already-used code"),
    ),
    tag = "devices"
)]
pub async fn enroll(
    State(state): State<AppState>,
    Json(body): Json<EnrollRequest>,
) -> Result<impl IntoResponse, ApiError> {
    let mut tx = state.pool.begin().await?;

    // Validate and consume in one statement so two phones racing the same code
    // cannot both win.
    let enrollment = sqlx::query!(
        "UPDATE enrollments SET consumed_at = now()
         WHERE code_hash = $1 AND consumed_at IS NULL AND expires_at > now()
         RETURNING family_id, child_id",
        hash_token(&body.code.to_uppercase())
    )
    .fetch_optional(&mut *tx)
    .await?
    .ok_or(ApiError::NotFound)?;

    let device_id = Uuid::new_v4();
    let token = random_token();
    sqlx::query!(
        "INSERT INTO devices (id, family_id, child_id, platform, model, label, token_hash)
         VALUES ($1, $2, $3, $4, $5, $6, $7)",
        device_id,
        enrollment.family_id,
        enrollment.child_id,
        body.platform,
        body.model,
        body.label,
        hash_token(&token)
    )
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;
    Ok((
        StatusCode::CREATED,
        Json(EnrolledResponse { device_id, token }),
    ))
}

/// A device identity. Write-only by construction: no parent route accepts this
/// extractor, so a leaked device token cannot read a family's data.
#[derive(Debug, Clone)]
pub struct Device {
    pub id: Uuid,
    pub family_id: Uuid,
    pub child_id: Uuid,
}

impl FromRequestParts<AppState> for Device {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let token = parts
            .headers
            .get(axum::http::header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer "))
            .ok_or(ApiError::Unauthenticated)?;

        let row = sqlx::query!(
            "SELECT id, family_id, child_id FROM devices
             WHERE token_hash = $1 AND revoked_at IS NULL",
            hash_token(token)
        )
        .fetch_optional(&state.pool)
        .await?
        .ok_or(ApiError::Unauthenticated)?;

        Ok(Device {
            id: row.id,
            family_id: row.family_id,
            child_id: row.child_id,
        })
    }
}
