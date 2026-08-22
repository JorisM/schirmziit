use crate::AppState;
use crate::auth::{Parent, hash_token};
use crate::db::scope;
use crate::error::ApiError;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::{
    Json, Router,
    routing::{delete, get, post},
};
use chrono::{Duration, Utc};
use rand::Rng;
use uuid::Uuid;

/// No 0/O/1/I/L: parents read these codes aloud and type them on a phone.
pub const ENROLL_ALPHABET: &str = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
const ENROLL_LEN: usize = 8;
const ENROLL_TTL_MINUTES: i64 = 15;
/// Three missed 30-minute syncs.
const STALE_AFTER_MINUTES: i64 = 90;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/children", post(create).get(list))
        .route("/v1/children/{id}", delete(soft_delete))
        .route("/v1/children/{id}/enrollments", post(mint_enrollment))
        .route("/v1/devices", get(list_devices))
        .route("/v1/devices/{id}", delete(revoke_device))
}

#[derive(serde::Deserialize, utoipa::ToSchema)]
pub struct NewChild {
    pub display_name: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct ChildResponse {
    pub id: Uuid,
    pub display_name: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct EnrollmentResponse {
    pub code: String,
    pub expires_at: chrono::DateTime<Utc>,
    pub qr_payload: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct DeviceResponse {
    pub id: Uuid,
    pub child_id: Uuid,
    pub platform: String,
    pub model: String,
    pub label: String,
    pub last_seen_at: Option<chrono::DateTime<Utc>>,
    pub revoked: bool,
    pub stale: bool,
}

#[utoipa::path(
    post, path = "/v1/children", request_body = NewChild,
    responses(
        (status = 201, description = "Child created", body = ChildResponse),
        (status = 401, description = "Not authenticated"),
        (status = 422, description = "Empty display name"),
    ),
    tag = "children"
)]
pub async fn create(
    parent: Parent,
    State(state): State<AppState>,
    Json(body): Json<NewChild>,
) -> Result<impl IntoResponse, ApiError> {
    if body.display_name.trim().is_empty() {
        return Err(ApiError::Validation(
            "display_name must not be empty".into(),
        ));
    }
    let id = Uuid::new_v4();
    sqlx::query!(
        "INSERT INTO children (id, family_id, display_name) VALUES ($1, $2, $3)",
        id,
        parent.family_id,
        body.display_name
    )
    .execute(&state.pool)
    .await?;

    Ok((
        StatusCode::CREATED,
        Json(ChildResponse {
            id,
            display_name: body.display_name,
        }),
    ))
}

#[utoipa::path(
    get, path = "/v1/children",
    responses(
        (status = 200, description = "Children in this family", body = Vec<ChildResponse>),
        (status = 401, description = "Not authenticated"),
    ),
    tag = "children"
)]
pub async fn list(
    parent: Parent,
    State(state): State<AppState>,
) -> Result<Json<Vec<ChildResponse>>, ApiError> {
    let rows = sqlx::query!(
        "SELECT id, display_name FROM children
         WHERE family_id = $1 AND deleted_at IS NULL ORDER BY created_at",
        parent.family_id
    )
    .fetch_all(&state.pool)
    .await?;

    Ok(Json(
        rows.into_iter()
            .map(|r| ChildResponse {
                id: r.id,
                display_name: r.display_name,
            })
            .collect(),
    ))
}

#[utoipa::path(
    delete, path = "/v1/children/{id}",
    params(("id" = Uuid, Path, description = "Child id")),
    responses(
        (status = 204, description = "Soft deleted"),
        (status = 404, description = "No such child in this family"),
    ),
    tag = "children"
)]
pub async fn soft_delete(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;
    sqlx::query!(
        "UPDATE children SET deleted_at = now() WHERE id = $1",
        child_id
    )
    .execute(&state.pool)
    .await?;
    Ok(StatusCode::NO_CONTENT)
}

#[utoipa::path(
    post, path = "/v1/children/{id}/enrollments",
    params(("id" = Uuid, Path, description = "Child id")),
    responses(
        (status = 201, description = "One-time enrollment code", body = EnrollmentResponse),
        (status = 404, description = "No such child in this family"),
    ),
    tag = "devices"
)]
pub async fn mint_enrollment(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<impl IntoResponse, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;

    let code: String = {
        let mut rng = rand::rng();
        let chars: Vec<char> = ENROLL_ALPHABET.chars().collect();
        (0..ENROLL_LEN)
            .map(|_| chars[rng.random_range(0..chars.len())])
            .collect()
    };
    let expires_at = Utc::now() + Duration::minutes(ENROLL_TTL_MINUTES);

    sqlx::query!(
        "INSERT INTO enrollments (id, family_id, child_id, code_hash, expires_at)
         VALUES ($1, $2, $3, $4, $5)",
        Uuid::new_v4(),
        parent.family_id,
        child_id,
        hash_token(&code),
        expires_at
    )
    .execute(&state.pool)
    .await?;

    // The URL travels in the QR: the app never ships a hardcoded backend, so a
    // self-hoster gets the same one-scan pairing as a hosted instance.
    let qr_payload = format!(
        "schirmziit://enroll?url={}&code={}",
        state.config.public_url, code
    );

    Ok((
        StatusCode::CREATED,
        Json(EnrollmentResponse {
            code,
            expires_at,
            qr_payload,
        }),
    ))
}

#[utoipa::path(
    get, path = "/v1/devices",
    responses(
        (status = 200, description = "Devices in this family", body = Vec<DeviceResponse>),
        (status = 401, description = "Not authenticated"),
    ),
    tag = "devices"
)]
pub async fn list_devices(
    parent: Parent,
    State(state): State<AppState>,
) -> Result<Json<Vec<DeviceResponse>>, ApiError> {
    let rows = sqlx::query!(
        "SELECT id, child_id, platform, model, label, last_seen_at, revoked_at
         FROM devices WHERE family_id = $1 ORDER BY created_at",
        parent.family_id
    )
    .fetch_all(&state.pool)
    .await?;

    let now = Utc::now();
    Ok(Json(
        rows.into_iter()
            .map(|r| DeviceResponse {
                id: r.id,
                child_id: r.child_id,
                platform: r.platform,
                model: r.model,
                label: r.label,
                last_seen_at: r.last_seen_at,
                revoked: r.revoked_at.is_some(),
                // A silent agent is indistinguishable from an unused phone
                // unless the API says so, so staleness is a first-class field.
                stale: r
                    .last_seen_at
                    .is_none_or(|seen| now - seen > Duration::minutes(STALE_AFTER_MINUTES)),
            })
            .collect(),
    ))
}

#[utoipa::path(
    delete, path = "/v1/devices/{id}",
    params(("id" = Uuid, Path, description = "Device id")),
    responses(
        (status = 204, description = "Token revoked"),
        (status = 404, description = "No such device in this family"),
    ),
    tag = "devices"
)]
pub async fn revoke_device(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let device_id = scope::device_of_family(&state.pool, parent.family_id, id).await?;
    sqlx::query!(
        "UPDATE devices SET revoked_at = now() WHERE id = $1",
        device_id
    )
    .execute(&state.pool)
    .await?;
    Ok(StatusCode::NO_CONTENT)
}
