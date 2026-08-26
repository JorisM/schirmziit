use utoipa::OpenApi;

/// The document is generated from the real handlers and committed to
/// `api/openapi.json`, which the dashboard's TypeScript is generated from.
#[derive(OpenApi)]
#[openapi(
    info(title = "Schirmziit API", version = env!("CARGO_PKG_VERSION")),
    paths(
        crate::auth::routes::register,
        crate::auth::routes::login,
        crate::auth::routes::logout,
        crate::auth::routes::me,
        crate::routes::children::create,
        crate::routes::children::list,
        crate::routes::children::soft_delete,
        crate::routes::children::mint_enrollment,
        crate::routes::children::claim_device,
        crate::routes::children::list_devices,
        crate::routes::children::revoke_device,
        crate::routes::enroll::enroll,
        crate::routes::ingest::ingest,
        crate::routes::usage::usage,
        crate::routes::usage::summary,
        crate::routes::usage::my_usage,
        crate::routes::purge::purge,
        crate::routes::waitlist::join,
    ),
    components(schemas(
        crate::error::Problem,
        schirmziit_core::codes::ErrorCode,
        crate::auth::routes::Credentials,
        crate::auth::routes::RegisteredResponse,
        crate::auth::routes::MeResponse,
        crate::routes::children::NewChild,
        crate::routes::children::ChildResponse,
        crate::routes::children::EnrollmentResponse,
        crate::routes::children::ClaimDevice,
        crate::routes::children::ClaimedDeviceResponse,
        crate::routes::children::DeviceResponse,
        crate::routes::enroll::EnrollRequest,
        crate::routes::enroll::EnrolledResponse,
        crate::routes::purge::PurgeResponse,
        crate::routes::waitlist::WaitlistRequest,
        crate::routes::usage::UsageResponse,
        crate::routes::usage::SummaryResponse,
        crate::routes::usage::DeviceStatus,
        crate::routes::usage::Series,
        crate::routes::usage::Point,
        crate::routes::usage::DeviceTotal,
        crate::routes::usage::TopApp,
        schirmziit_core::wire::IngestRequest,
        schirmziit_core::wire::IngestResponse,
        schirmziit_core::wire::IngestHour,
        schirmziit_core::wire::IngestApp,
        schirmziit_core::wire::Rejected,
    ))
)]
pub struct ApiDoc;
