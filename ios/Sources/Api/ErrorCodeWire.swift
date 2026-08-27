
/// The catalog's `SZ-Ennn` strings.
///
/// UniFFI hands Swift the enum but not the wire form, and both directions are
/// needed: the server sends the string, the generated copy table is keyed by it,
/// and the mono line a parent photographs shows it. The switch is exhaustive on
/// purpose — adding a code to `crates/core` fails to compile here until it is
/// named, which is the only reliable reminder.
extension ErrorCode {
    var wire: String {
        switch self {
        case .invalidCredentials: "SZ-E101"
        case .unauthenticated: "SZ-E102"
        case .sessionExpired: "SZ-E103"
        case .registrationDisabled: "SZ-E104"
        case .emailTaken: "SZ-E105"
        case .wrongParentPassword: "SZ-E106"
        case .notFound: "SZ-E201"
        case .childNotFound: "SZ-E202"
        case .deviceNotEnrolled: "SZ-E203"
        case .pairingCodeInvalid: "SZ-E204"
        case .pairingCodeExpired: "SZ-E205"
        case .validationFailed: "SZ-E301"
        case .payloadTooLarge: "SZ-E302"
        case .unsupportedSchema: "SZ-E303"
        case .rateLimited: "SZ-E304"
        case .offline: "SZ-E501"
        case .timeout: "SZ-E502"
        case .tlsFailed: "SZ-E503"
        case .badResponseBody: "SZ-E504"
        case .serverUnreachable: "SZ-E505"
        case .baseUrlNotConfigured: "SZ-E506"
        case .usageAccessRevoked: "SZ-E601"
        case .notificationPermissionMissing: "SZ-E602"
        case .mediaNotificationAccessMissing: "SZ-E603"
        case .screenTimeAuthorisationDenied: "SZ-E604"
        case .backgroundRefreshDisabled: "SZ-E605"
        case .keychainReadFailed: "SZ-E701"
        case .keychainWriteFailed: "SZ-E702"
        case .localDecodeFailed: "SZ-E703"
        case .queueWriteFailed: "SZ-E704"
        case .coreUnknownTimezone: "SZ-E705"
        case .coreMalformedJson: "SZ-E706"
        case .unexpectedClientError: "SZ-E707"
        case .internal: "SZ-E901"
        case .databaseUnavailable: "SZ-E902"
        }
    }

    init?(wire: String) {
        guard let match = ErrorCode.everyCase.first(where: { $0.wire == wire }) else { return nil }
        self = match
    }

    /// UniFFI does not derive `CaseIterable`, and the tests and the copy lookup
    /// both need to walk the catalog.
    static let everyCase: [ErrorCode] = [
        .invalidCredentials, .unauthenticated, .sessionExpired, .registrationDisabled,
        .emailTaken, .wrongParentPassword, .notFound, .childNotFound, .deviceNotEnrolled,
        .pairingCodeInvalid, .pairingCodeExpired, .validationFailed, .payloadTooLarge,
        .unsupportedSchema, .rateLimited, .offline, .timeout, .tlsFailed, .badResponseBody,
        .serverUnreachable, .baseUrlNotConfigured, .usageAccessRevoked,
        .notificationPermissionMissing, .mediaNotificationAccessMissing,
        .screenTimeAuthorisationDenied, .backgroundRefreshDisabled, .keychainReadFailed,
        .keychainWriteFailed, .localDecodeFailed, .queueWriteFailed, .coreUnknownTimezone,
        .coreMalformedJson, .unexpectedClientError, .internal, .databaseUnavailable,
    ]
}
