use crate::error::ApiError;
use sqlx::PgPool;
use uuid::Uuid;

/// Resolve a child id inside a family. Returns `NotFound` - never `Forbidden` -
/// for a child that belongs to someone else, so the API cannot be used to probe
/// which ids exist in other families.
pub async fn child_of_family(
    pool: &PgPool,
    family_id: Uuid,
    child_id: Uuid,
) -> Result<Uuid, ApiError> {
    sqlx::query_scalar!(
        "SELECT id FROM children WHERE id = $1 AND family_id = $2 AND deleted_at IS NULL",
        child_id,
        family_id
    )
    .fetch_optional(pool)
    .await?
    .ok_or(ApiError::NotFound)
}

pub async fn device_of_family(
    pool: &PgPool,
    family_id: Uuid,
    device_id: Uuid,
) -> Result<Uuid, ApiError> {
    sqlx::query_scalar!(
        "SELECT id FROM devices WHERE id = $1 AND family_id = $2",
        device_id,
        family_id
    )
    .fetch_optional(pool)
    .await?
    .ok_or(ApiError::NotFound)
}
