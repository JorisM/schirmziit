//! The error catalog. One enum for the whole product: the server, both apps and
//! the dashboard name a failure with the same code, so a screenshot identifies
//! it without anyone having to ask which screen it came from.
//!
//! `SZ-Ennn`, grouped by family so the range alone is informative:
//!
//! | Range | Family |
//! |---|---|
//! | 1xx | auth / session |
//! | 2xx | scope / existence |
//! | 3xx | request shape |
//! | 5xx | transport, client-only |
//! | 6xx | platform permission, client-only |
//! | 7xx | local storage / decode, client-only |
//! | 9xx | server-side |
//!
//! 4xx is deliberately empty: `SZ-E401` next to an HTTP status would read as
//! "unauthorised" to everyone who has ever seen a 401, and it would mean
//! "internal server error". Server-side codes are 9xx instead.
//!
//! Client-only codes live here rather than in each app so a client cannot
//! invent a code the catalog does not know, and so `copy/errors.toml` has one
//! list to satisfy.

/// Numbers that once meant something else. Never re-issue one: an old
/// screenshot would then describe a different failure than it did on the day it
/// was taken.
pub const RETIRED: &[u16] = &[];

#[derive(
    Debug, Clone, Copy, PartialEq, Eq, Hash, serde::Serialize, serde::Deserialize, uniffi::Enum,
)]
#[cfg_attr(feature = "schema", derive(utoipa::ToSchema))]
pub enum ErrorCode {
    // 1xx — auth / session
    #[serde(rename = "SZ-E101")]
    InvalidCredentials,
    #[serde(rename = "SZ-E102")]
    Unauthenticated,
    #[serde(rename = "SZ-E103")]
    SessionExpired,
    #[serde(rename = "SZ-E104")]
    RegistrationDisabled,
    #[serde(rename = "SZ-E105")]
    EmailTaken,
    #[serde(rename = "SZ-E106")]
    WrongParentPassword,

    // 2xx — scope / existence
    #[serde(rename = "SZ-E201")]
    NotFound,
    #[serde(rename = "SZ-E202")]
    ChildNotFound,
    #[serde(rename = "SZ-E203")]
    DeviceNotEnrolled,
    #[serde(rename = "SZ-E204")]
    PairingCodeInvalid,
    #[serde(rename = "SZ-E205")]
    PairingCodeExpired,

    // 3xx — request shape
    #[serde(rename = "SZ-E301")]
    ValidationFailed,
    #[serde(rename = "SZ-E302")]
    PayloadTooLarge,
    #[serde(rename = "SZ-E303")]
    UnsupportedSchema,
    #[serde(rename = "SZ-E304")]
    RateLimited,

    // 5xx — transport, client-only
    #[serde(rename = "SZ-E501")]
    Offline,
    #[serde(rename = "SZ-E502")]
    Timeout,
    #[serde(rename = "SZ-E503")]
    TlsFailed,
    #[serde(rename = "SZ-E504")]
    BadResponseBody,
    #[serde(rename = "SZ-E505")]
    ServerUnreachable,
    #[serde(rename = "SZ-E506")]
    BaseUrlNotConfigured,

    // 6xx — platform permission, client-only
    #[serde(rename = "SZ-E601")]
    UsageAccessRevoked,
    #[serde(rename = "SZ-E602")]
    NotificationPermissionMissing,
    #[serde(rename = "SZ-E603")]
    MediaNotificationAccessMissing,
    #[serde(rename = "SZ-E604")]
    ScreenTimeAuthorisationDenied,
    #[serde(rename = "SZ-E605")]
    BackgroundRefreshDisabled,

    // 7xx — local storage / decode, client-only
    #[serde(rename = "SZ-E701")]
    KeychainReadFailed,
    #[serde(rename = "SZ-E702")]
    KeychainWriteFailed,
    #[serde(rename = "SZ-E703")]
    LocalDecodeFailed,
    #[serde(rename = "SZ-E704")]
    QueueWriteFailed,
    #[serde(rename = "SZ-E705")]
    CoreUnknownTimezone,
    #[serde(rename = "SZ-E706")]
    CoreMalformedJson,
    #[serde(rename = "SZ-E707")]
    UnexpectedClientError,

    // 9xx — server-side
    #[serde(rename = "SZ-E901")]
    Internal,
    #[serde(rename = "SZ-E902")]
    DatabaseUnavailable,
}

impl ErrorCode {
    /// Every variant, in catalog order. The tests and `crates/copygen` walk
    /// this, so a new variant is covered by both the moment it is added.
    pub const ALL: &'static [ErrorCode] = &[
        ErrorCode::InvalidCredentials,
        ErrorCode::Unauthenticated,
        ErrorCode::SessionExpired,
        ErrorCode::RegistrationDisabled,
        ErrorCode::EmailTaken,
        ErrorCode::WrongParentPassword,
        ErrorCode::NotFound,
        ErrorCode::ChildNotFound,
        ErrorCode::DeviceNotEnrolled,
        ErrorCode::PairingCodeInvalid,
        ErrorCode::PairingCodeExpired,
        ErrorCode::ValidationFailed,
        ErrorCode::PayloadTooLarge,
        ErrorCode::UnsupportedSchema,
        ErrorCode::RateLimited,
        ErrorCode::Offline,
        ErrorCode::Timeout,
        ErrorCode::TlsFailed,
        ErrorCode::BadResponseBody,
        ErrorCode::ServerUnreachable,
        ErrorCode::BaseUrlNotConfigured,
        ErrorCode::UsageAccessRevoked,
        ErrorCode::NotificationPermissionMissing,
        ErrorCode::MediaNotificationAccessMissing,
        ErrorCode::ScreenTimeAuthorisationDenied,
        ErrorCode::BackgroundRefreshDisabled,
        ErrorCode::KeychainReadFailed,
        ErrorCode::KeychainWriteFailed,
        ErrorCode::LocalDecodeFailed,
        ErrorCode::QueueWriteFailed,
        ErrorCode::CoreUnknownTimezone,
        ErrorCode::CoreMalformedJson,
        ErrorCode::UnexpectedClientError,
        ErrorCode::Internal,
        ErrorCode::DatabaseUnavailable,
    ];

    pub const fn number(self) -> u16 {
        match self {
            ErrorCode::InvalidCredentials => 101,
            ErrorCode::Unauthenticated => 102,
            ErrorCode::SessionExpired => 103,
            ErrorCode::RegistrationDisabled => 104,
            ErrorCode::EmailTaken => 105,
            ErrorCode::WrongParentPassword => 106,
            ErrorCode::NotFound => 201,
            ErrorCode::ChildNotFound => 202,
            ErrorCode::DeviceNotEnrolled => 203,
            ErrorCode::PairingCodeInvalid => 204,
            ErrorCode::PairingCodeExpired => 205,
            ErrorCode::ValidationFailed => 301,
            ErrorCode::PayloadTooLarge => 302,
            ErrorCode::UnsupportedSchema => 303,
            ErrorCode::RateLimited => 304,
            ErrorCode::Offline => 501,
            ErrorCode::Timeout => 502,
            ErrorCode::TlsFailed => 503,
            ErrorCode::BadResponseBody => 504,
            ErrorCode::ServerUnreachable => 505,
            ErrorCode::BaseUrlNotConfigured => 506,
            ErrorCode::UsageAccessRevoked => 601,
            ErrorCode::NotificationPermissionMissing => 602,
            ErrorCode::MediaNotificationAccessMissing => 603,
            ErrorCode::ScreenTimeAuthorisationDenied => 604,
            ErrorCode::BackgroundRefreshDisabled => 605,
            ErrorCode::KeychainReadFailed => 701,
            ErrorCode::KeychainWriteFailed => 702,
            ErrorCode::LocalDecodeFailed => 703,
            ErrorCode::QueueWriteFailed => 704,
            ErrorCode::CoreUnknownTimezone => 705,
            ErrorCode::CoreMalformedJson => 706,
            ErrorCode::UnexpectedClientError => 707,
            ErrorCode::Internal => 901,
            ErrorCode::DatabaseUnavailable => 902,
        }
    }

    /// What a parent sees, and what the log is grepped by.
    pub const fn as_str(self) -> &'static str {
        match self {
            ErrorCode::InvalidCredentials => "SZ-E101",
            ErrorCode::Unauthenticated => "SZ-E102",
            ErrorCode::SessionExpired => "SZ-E103",
            ErrorCode::RegistrationDisabled => "SZ-E104",
            ErrorCode::EmailTaken => "SZ-E105",
            ErrorCode::WrongParentPassword => "SZ-E106",
            ErrorCode::NotFound => "SZ-E201",
            ErrorCode::ChildNotFound => "SZ-E202",
            ErrorCode::DeviceNotEnrolled => "SZ-E203",
            ErrorCode::PairingCodeInvalid => "SZ-E204",
            ErrorCode::PairingCodeExpired => "SZ-E205",
            ErrorCode::ValidationFailed => "SZ-E301",
            ErrorCode::PayloadTooLarge => "SZ-E302",
            ErrorCode::UnsupportedSchema => "SZ-E303",
            ErrorCode::RateLimited => "SZ-E304",
            ErrorCode::Offline => "SZ-E501",
            ErrorCode::Timeout => "SZ-E502",
            ErrorCode::TlsFailed => "SZ-E503",
            ErrorCode::BadResponseBody => "SZ-E504",
            ErrorCode::ServerUnreachable => "SZ-E505",
            ErrorCode::BaseUrlNotConfigured => "SZ-E506",
            ErrorCode::UsageAccessRevoked => "SZ-E601",
            ErrorCode::NotificationPermissionMissing => "SZ-E602",
            ErrorCode::MediaNotificationAccessMissing => "SZ-E603",
            ErrorCode::ScreenTimeAuthorisationDenied => "SZ-E604",
            ErrorCode::BackgroundRefreshDisabled => "SZ-E605",
            ErrorCode::KeychainReadFailed => "SZ-E701",
            ErrorCode::KeychainWriteFailed => "SZ-E702",
            ErrorCode::LocalDecodeFailed => "SZ-E703",
            ErrorCode::QueueWriteFailed => "SZ-E704",
            ErrorCode::CoreUnknownTimezone => "SZ-E705",
            ErrorCode::CoreMalformedJson => "SZ-E706",
            ErrorCode::UnexpectedClientError => "SZ-E707",
            ErrorCode::Internal => "SZ-E901",
            ErrorCode::DatabaseUnavailable => "SZ-E902",
        }
    }
}

impl std::fmt::Display for ErrorCode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Two codes sharing a number would make a reference ambiguous in a log.
    #[test]
    fn every_number_is_unique() {
        let mut seen = std::collections::HashSet::new();
        for code in ErrorCode::ALL {
            assert!(
                seen.insert(code.number()),
                "{} reuses number {}",
                code.as_str(),
                code.number()
            );
        }
    }

    /// The rendered string is what a parent reads off a screenshot; it must
    /// agree with the number a log line is grepped by.
    #[test]
    fn the_string_and_the_number_agree() {
        for code in ErrorCode::ALL {
            assert_eq!(code.as_str(), format!("SZ-E{}", code.number()));
        }
    }

    /// A retired number must never come back meaning something else: an old
    /// screenshot would then describe a different failure.
    #[test]
    fn no_live_code_uses_a_retired_number() {
        for code in ErrorCode::ALL {
            assert!(
                !RETIRED.contains(&code.number()),
                "{} uses retired number {}",
                code.as_str(),
                code.number()
            );
        }
    }

    /// 4xx is reserved so SZ-E401 can never be read as HTTP 401.
    #[test]
    fn the_four_hundreds_are_never_used() {
        for code in ErrorCode::ALL {
            assert!(
                !(400..500).contains(&code.number()),
                "{} sits in the reserved 4xx range",
                code.as_str()
            );
        }
    }

    #[test]
    fn it_serialises_as_its_wire_string() {
        let json = serde_json::to_string(&ErrorCode::Offline).unwrap();
        assert_eq!(json, "\"SZ-E501\"");
    }
}
