use utoipa::OpenApi;

/// The document is generated from the real handlers and committed to
/// `api/openapi.json`, which the dashboard's TypeScript is generated from.
#[derive(OpenApi)]
#[openapi(
    info(title = "Nestling API", version = env!("CARGO_PKG_VERSION")),
    paths(
        crate::auth::routes::register,
        crate::auth::routes::login,
        crate::auth::routes::logout,
        crate::auth::routes::me,
        crate::routes::children::create,
        crate::routes::children::list,
        crate::routes::children::soft_delete,
        crate::routes::children::mint_enrollment,
        crate::routes::children::list_devices,
        crate::routes::children::revoke_device,
        crate::routes::enroll::enroll,
        crate::routes::ingest::ingest,
        crate::routes::usage::usage,
        crate::routes::usage::summary,
        crate::routes::purge::purge,
    ),
    components(schemas(
        crate::auth::routes::Credentials,
        crate::auth::routes::RegisteredResponse,
        crate::auth::routes::MeResponse,
        crate::routes::children::NewChild,
        crate::routes::children::ChildResponse,
        crate::routes::children::EnrollmentResponse,
        crate::routes::children::DeviceResponse,
        crate::routes::enroll::EnrollRequest,
        crate::routes::enroll::EnrolledResponse,
        crate::routes::purge::PurgeResponse,
        crate::routes::usage::UsageResponse,
        crate::routes::usage::SummaryResponse,
        crate::routes::usage::DeviceStatus,
        crate::routes::usage::Series,
        crate::routes::usage::Point,
        crate::routes::usage::DeviceTotal,
        crate::routes::usage::TopApp,
        nestling_core::wire::IngestRequest,
        nestling_core::wire::IngestResponse,
        nestling_core::wire::IngestHour,
        nestling_core::wire::IngestApp,
        nestling_core::wire::Rejected,
    ))
)]
pub struct ApiDoc;
