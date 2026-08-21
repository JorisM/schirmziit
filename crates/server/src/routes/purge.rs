use crate::AppState;
use crate::auth::Parent;
use crate::db::scope;
use crate::error::ApiError;
use axum::extract::{Path, State};
use axum::{Json, Router, routing::delete};
use uuid::Uuid;

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/children/{id}/data", delete(purge))
}

/// Deletes for real and reports what went, so "delete my child's data" is
/// verifiable rather than a promise.
pub async fn purge(
    parent: Parent,
    State(state): State<AppState>,
    Path(id): Path<Uuid>,
) -> Result<Json<serde_json::Value>, ApiError> {
    let child_id = scope::child_of_family(&state.pool, parent.family_id, id).await?;
    let mut tx = state.pool.begin().await?;

    let usage_hours = sqlx::query!(
        "DELETE FROM usage_hours
         WHERE device_id IN (SELECT id FROM devices WHERE child_id = $1)",
        child_id
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    let device_hours = sqlx::query!(
        "DELETE FROM device_hours
         WHERE device_id IN (SELECT id FROM devices WHERE child_id = $1)",
        child_id
    )
    .execute(&mut *tx)
    .await?
    .rows_affected();

    let usage_days = sqlx::query!("DELETE FROM usage_days WHERE child_id = $1", child_id)
        .execute(&mut *tx)
        .await?
        .rows_affected();

    tx.commit().await?;
    Ok(Json(serde_json::json!({
        "deleted_usage_hours": usage_hours,
        "deleted_device_hours": device_hours,
        "deleted_usage_days": usage_days,
    })))
}
