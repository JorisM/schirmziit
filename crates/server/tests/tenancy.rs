mod helpers;
use axum::http::StatusCode;
use helpers::TestApp;
use sqlx::PgPool;

/// Two families in one database, each with its own parent session.
async fn two_families(pool: PgPool) -> (TestApp, String, TestApp) {
    let a = TestApp::registered(pool.clone()).await;
    let child_id = a.create_child("A's kid").await;
    let b = TestApp::registered(pool).await;
    (a, child_id, b)
}

#[sqlx::test]
async fn another_family_cannot_read_usage(pool: PgPool) {
    let (_, child_id, b) = two_families(pool).await;
    let response = b
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;
    assert_eq!(
        response.status,
        StatusCode::NOT_FOUND,
        "must be 404, not 403 - a 403 confirms the id exists"
    );
}

#[sqlx::test]
async fn another_family_cannot_read_a_summary(pool: PgPool) {
    let (_, child_id, b) = two_families(pool).await;
    let response = b
        .get(&format!(
            "/v1/children/{child_id}/summary?date=2026-08-20&tz=Europe/Zurich"
        ))
        .await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
}

#[sqlx::test]
async fn another_family_cannot_mint_an_enrollment_code(pool: PgPool) {
    let (_, child_id, b) = two_families(pool).await;
    let response = b
        .post_json(
            &format!("/v1/children/{child_id}/enrollments"),
            serde_json::json!({}),
        )
        .await;
    assert_eq!(response.status, StatusCode::NOT_FOUND);
}

#[sqlx::test]
async fn another_family_cannot_delete_a_child(pool: PgPool) {
    let (_, child_id, b) = two_families(pool).await;
    assert_eq!(
        b.delete(&format!("/v1/children/{child_id}")).await.status,
        StatusCode::NOT_FOUND
    );
}

#[sqlx::test]
async fn another_family_cannot_see_or_revoke_a_device(pool: PgPool) {
    let (a, child_id, b) = two_families(pool).await;
    let (device_id, _) = a.enroll_device(&child_id).await;

    assert_eq!(
        b.delete(&format!("/v1/devices/{device_id}")).await.status,
        StatusCode::NOT_FOUND
    );
    assert!(
        b.get("/v1/devices")
            .await
            .json
            .as_array()
            .unwrap()
            .is_empty(),
        "the device list is family-scoped"
    );
}

#[sqlx::test]
async fn another_family_cannot_purge_data(pool: PgPool) {
    let (_, child_id, b) = two_families(pool).await;
    assert_eq!(
        b.delete(&format!("/v1/children/{child_id}/data"))
            .await
            .status,
        StatusCode::NOT_FOUND
    );
}

#[sqlx::test]
async fn a_device_token_cannot_read_anything(pool: PgPool) {
    let (a, child_id, _) = two_families(pool).await;
    let (_, token) = a.enroll_device(&child_id).await;

    let usage = a
        .get_as_device(
            &format!(
                "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=UTC"
            ),
            &token,
        )
        .await;
    assert_eq!(
        usage.status,
        StatusCode::UNAUTHORIZED,
        "a leaked device token must not read family data"
    );

    let children = a.get_as_device("/v1/children", &token).await;
    assert_eq!(children.status, StatusCode::UNAUTHORIZED);
}

#[sqlx::test]
async fn a_revoked_device_cannot_read_its_child(pool: PgPool) {
    let app = TestApp::registered(pool.clone()).await;
    let child_id = app.create_child("Kid").await;
    let (device_id, token) = app.enroll_device(&child_id).await;

    assert_eq!(
        app.delete(&format!("/v1/devices/{device_id}")).await.status,
        StatusCode::NO_CONTENT
    );

    let response = app
        .get_as_device(
            "/v1/me/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich",
            &token,
        )
        .await;
    assert_eq!(
        response.status,
        StatusCode::UNAUTHORIZED,
        "a revoked device keeps no read either"
    );
}
