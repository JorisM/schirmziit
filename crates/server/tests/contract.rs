mod helpers;
use helpers::TestApp;
use sqlx::PgPool;
use utoipa::OpenApi;

/// Validate a real response body against the schema its own annotation claims.
///
/// `#[utoipa::path]` annotations are hand-written and can lie about what a
/// handler returns. This is the test that makes the committed openapi.json - and
/// therefore the dashboard's generated TypeScript - trustworthy.
fn assert_matches_schema(component: &str, instance: &serde_json::Value) {
    let doc = serde_json::to_value(nestling_server::openapi::ApiDoc::openapi()).unwrap();
    let schemas = &doc["components"]["schemas"];
    let mut schema = schemas[component].clone();
    assert!(
        !schema.is_null(),
        "no schema named {component} in the OpenAPI document"
    );

    // utoipa emits `#/components/schemas/X` refs; give the validator a document
    // where those resolve.
    schema["components"] = doc["components"].clone();

    let validator = jsonschema::validator_for(&schema).expect("compile schema");
    let errors: Vec<String> = validator
        .iter_errors(instance)
        .map(|e| e.to_string())
        .collect();
    assert!(
        errors.is_empty(),
        "{component} response does not match its schema: {errors:?}\nbody: {instance}"
    );
}

#[sqlx::test]
async fn child_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let created = app
        .post_json("/v1/children", serde_json::json!({ "display_name": "Kid" }))
        .await;
    assert_matches_schema("ChildResponse", &created.json);
}

#[sqlx::test]
async fn me_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let me = app.get("/v1/me").await;
    assert_matches_schema("MeResponse", &me.json);
}

#[sqlx::test]
async fn enrollment_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let enrollment = app
        .post_json(
            &format!("/v1/children/{child_id}/enrollments"),
            serde_json::json!({}),
        )
        .await;
    assert_matches_schema("EnrollmentResponse", &enrollment.json);
}

#[sqlx::test]
async fn device_list_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    app.enroll_device(&child_id).await;
    let devices = app.get("/v1/devices").await;
    assert_matches_schema("DeviceResponse", &devices.json[0]);
}

#[sqlx::test]
async fn usage_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    app.enroll_device(&child_id).await;
    let usage = app
        .get(&format!(
            "/v1/children/{child_id}/usage?from=2026-08-20&to=2026-08-20&bucket=hour&tz=Europe/Zurich"
        ))
        .await;
    assert_matches_schema("UsageResponse", &usage.json);
}

#[sqlx::test]
async fn summary_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let summary = app
        .get(&format!(
            "/v1/children/{child_id}/summary?date=2026-08-20&tz=Europe/Zurich"
        ))
        .await;
    assert_matches_schema("SummaryResponse", &summary.json);
}

#[sqlx::test]
async fn ingest_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let (_, token) = app.enroll_device(&child_id).await;

    let hour = chrono::Utc::now() - chrono::Duration::hours(2);
    let response = app
        .post_as_device(
            "/v1/ingest",
            &token,
            serde_json::json!({
                "schema": 1,
                "device_time": chrono::Utc::now(),
                "hours": [{
                    "hour_start": hour, "tz": "Europe/Zurich", "computed_at": chrono::Utc::now(),
                    "screen_on_ms": 1000, "unlock_count": 1,
                    "apps": [{ "package": "com.a", "label": "A",
                               "foreground_ms": 1000, "launch_count": 1 }]
                }]
            }),
        )
        .await;
    assert_matches_schema("IngestResponse", &response.json);
}

#[sqlx::test]
async fn purge_response_matches_its_schema(pool: PgPool) {
    let app = TestApp::registered(pool).await;
    let child_id = app.create_child("Kid").await;
    let purged = app.delete(&format!("/v1/children/{child_id}/data")).await;
    assert_matches_schema("PurgeResponse", &purged.json);
}
