use crate::AppState;
use crate::auth::{Parent, hash_token, random_token};
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
use rand::RngExt;
use uuid::Uuid;

/// No 0/O/1/I/L: parents read these codes aloud and type them on a phone.
pub const ENROLL_ALPHABET: &str = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
/// Six over this alphabet is 30^6 ≈ 7.3·10^8 codes, ~900× fewer than eight.
/// It stays out of guessing range only because a code is single-use and lives
/// fifteen minutes — so the rate limit in front of `POST /v1/enroll` is part of
/// this number, not an optional extra: a guessed code is a device token for
/// another family's child.
const ENROLL_LEN: usize = 6;
const ENROLL_TTL_MINUTES: i64 = 15;
/// Three missed 30-minute syncs.
const STALE_AFTER_MINUTES: i64 = 90;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/children", post(create).get(list))
        .route("/v1/children/{id}", delete(soft_delete))
        .route("/v1/children/{id}/enrollments", post(mint_enrollment))
        .route("/v1/children/{id}/devices", post(claim_device))
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
    /// Foreground milliseconds so far in the caller's local today. Zero, never
    /// null: a quiet day is a real number and an absent one renders as a hole.
    pub today_ms: i64,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct EnrollmentResponse {
    pub code: String,
    pub expires_at: chrono::DateTime<Utc>,
    pub qr_payload: String,
}

#[derive(serde::Deserialize, utoipa::ToSchema)]
pub struct ClaimDevice {
    pub platform: String,
    pub model: String,
    pub label: String,
}

#[derive(serde::Serialize, utoipa::ToSchema)]
pub struct ClaimedDeviceResponse {
    pub device_id: Uuid,
    /// Long-lived, write-only. Shown once; only its hash is stored.
    pub token: String,
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
            today_ms: 0,
        }),
    ))
}

/// `tz` is required, not defaulted: "today" is a local question, and a server
/// guessing UTC would report the wrong day for the first hours after midnight
/// in Zurich — the window a teenager is most likely being looked at in.
#[derive(serde::Deserialize, utoipa::IntoParams)]
pub struct ListQuery {
    tz: String,
}

#[utoipa::path(
    get, path = "/v1/children",
    params(ListQuery),
    responses(
        (status = 200, description = "Children in this family", body = Vec<ChildResponse>),
        (status = 401, description = "Not authenticated"),
        (status = 422, description = "Unknown timezone"),
    ),
    tag = "children"
)]
pub async fn list(
    parent: Parent,
    State(state): State<AppState>,
    axum::extract::Query(q): axum::extract::Query<ListQuery>,
) -> Result<Json<Vec<ChildResponse>>, ApiError> {
    let tz = crate::routes::usage::zone(&q.tz)?;
    let today = Utc::now().with_timezone(&tz).date_naive();
    let (start, end) = crate::routes::usage::bounds(today, today, tz)?;

    // One query, not one per child: the list is the first screen after sign-in
    // and N+1 round trips there is what the fan-out alternative was rejected for.
    let rows = sqlx::query!(
        r#"SELECT c.id, c.display_name,
                  COALESCE(SUM(u.foreground_ms), 0)::bigint AS "today_ms!"
           FROM children c
           LEFT JOIN devices d ON d.child_id = c.id
           LEFT JOIN usage_hours u
             ON u.device_id = d.id AND u.hour_start >= $2 AND u.hour_start < $3
           WHERE c.family_id = $1 AND c.deleted_at IS NULL
           GROUP BY c.id, c.display_name, c.created_at
           ORDER BY c.created_at"#,
        parent.family_id,
        start,
        end
    )
    .fetch_all(&state.pool)
    .await?;

    Ok(Json(
        rows.into_iter()
            .map(|r| ChildResponse {
                id: r.id,
                display_name: r.display_name,
                today_ms: r.today_ms,
            })
            .collect(),
    ))
}

/// Hides the child and stops their phones, in one transaction.
///
/// The row itself survives — historical usage stays attributable, and the
/// dedicated `DELETE /v1/children/{id}/data` is what actually erases figures,
/// deliberately separate so "delete my child's data" is its own reported act.
///
/// Revoking the devices is not tidying-up: `Device` authorizes on
/// `devices.revoked_at` alone, so without this a phone whose child is gone from
/// every parent screen keeps uploading hours that nothing can read back. A
/// parent removing a child means "and stop reporting".
#[utoipa::path(
    delete, path = "/v1/children/{id}",
    params(("id" = Uuid, Path, description = "Child id")),
    responses(
        (status = 204, description = "Hidden, and its devices revoked"),
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
    let mut tx = state.pool.begin().await?;

    sqlx::query!(
        "UPDATE children SET deleted_at = now() WHERE id = $1",
        child_id
    )
    .execute(&mut *tx)
    .await?;

    // `revoked_at IS NULL` so a device revoked earlier keeps the time it was
    // actually revoked at, rather than being restamped by an unrelated act.
    sqlx::query!(
        "UPDATE devices SET revoked_at = now()
         WHERE child_id = $1 AND revoked_at IS NULL",
        child_id
    )
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;
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

/// Enrol the phone the parent is holding, without a pairing code.
///
/// This is what makes one app work for both roles: a parent signs in on the
/// child's phone, picks the child, and the app trades that session for a device
/// token — then deletes the session, so no parent credentials and no parent
/// session stay behind on a child's phone. Codes remain for the case where the
/// parent is not there to sign in.
#[utoipa::path(
    post, path = "/v1/children/{id}/devices", request_body = ClaimDevice,
    params(("id" = Uuid, Path, description = "Child id")),
    responses(
        (status = 201, description = "Device enrolled", body = ClaimedDeviceResponse),
        (status = 401, description = "Not authenticated"),
        (status = 404, description = "No such child in this family"),
        (status = 422, description = "Empty platform, model or label"),
    ),
    tag = "devices"
)]
pub async fn claim_device(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
    Json(body): Json<ClaimDevice>,
) -> Result<impl IntoResponse, ApiError> {
    // Tenant check first: a parent may only claim a device for their own child,
    // and an unknown id must not be distinguishable from another family's.
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;

    let platform = body.platform.trim();
    let model = body.model.trim();
    let label = body.label.trim();
    if platform.is_empty() || model.is_empty() || label.is_empty() {
        return Err(ApiError::Validation(
            "platform, model and label must not be empty".into(),
        ));
    }

    let device_id = Uuid::new_v4();
    let token = random_token();
    sqlx::query!(
        "INSERT INTO devices (id, family_id, child_id, platform, model, label, token_hash)
         VALUES ($1, $2, $3, $4, $5, $6, $7)",
        device_id,
        parent.family_id,
        child_id,
        platform,
        model,
        label,
        hash_token(&token)
    )
    .execute(&state.pool)
    .await?;

    Ok((
        StatusCode::CREATED,
        Json(ClaimedDeviceResponse { device_id, token }),
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
