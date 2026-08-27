package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode

/**
 * The catalog's `SZ-Ennn` strings, both ways.
 *
 * UniFFI hands Kotlin the enum but not the wire form, and both directions are
 * needed: the server sends the string, the generated `error_copy.xml` is keyed
 * by it, and the mono line a parent photographs shows it.
 *
 * The `when` is exhaustive on purpose — adding a code to `crates/core` fails to
 * compile here until it is named, which is the only reliable reminder. Same
 * arrangement as `ios/Sources/Api/ErrorCodeWire.swift`.
 */
val ErrorCode.wire: String
    get() = when (this) {
        ErrorCode.INVALID_CREDENTIALS -> "SZ-E101"
        ErrorCode.UNAUTHENTICATED -> "SZ-E102"
        ErrorCode.SESSION_EXPIRED -> "SZ-E103"
        ErrorCode.REGISTRATION_DISABLED -> "SZ-E104"
        ErrorCode.EMAIL_TAKEN -> "SZ-E105"
        ErrorCode.WRONG_PARENT_PASSWORD -> "SZ-E106"
        ErrorCode.NOT_FOUND -> "SZ-E201"
        ErrorCode.CHILD_NOT_FOUND -> "SZ-E202"
        ErrorCode.DEVICE_NOT_ENROLLED -> "SZ-E203"
        ErrorCode.PAIRING_CODE_INVALID -> "SZ-E204"
        ErrorCode.PAIRING_CODE_EXPIRED -> "SZ-E205"
        ErrorCode.VALIDATION_FAILED -> "SZ-E301"
        ErrorCode.PAYLOAD_TOO_LARGE -> "SZ-E302"
        ErrorCode.UNSUPPORTED_SCHEMA -> "SZ-E303"
        ErrorCode.RATE_LIMITED -> "SZ-E304"
        ErrorCode.OFFLINE -> "SZ-E501"
        ErrorCode.TIMEOUT -> "SZ-E502"
        ErrorCode.TLS_FAILED -> "SZ-E503"
        ErrorCode.BAD_RESPONSE_BODY -> "SZ-E504"
        ErrorCode.SERVER_UNREACHABLE -> "SZ-E505"
        ErrorCode.BASE_URL_NOT_CONFIGURED -> "SZ-E506"
        ErrorCode.USAGE_ACCESS_REVOKED -> "SZ-E601"
        ErrorCode.NOTIFICATION_PERMISSION_MISSING -> "SZ-E602"
        ErrorCode.MEDIA_NOTIFICATION_ACCESS_MISSING -> "SZ-E603"
        ErrorCode.SCREEN_TIME_AUTHORISATION_DENIED -> "SZ-E604"
        ErrorCode.BACKGROUND_REFRESH_DISABLED -> "SZ-E605"
        ErrorCode.KEYCHAIN_READ_FAILED -> "SZ-E701"
        ErrorCode.KEYCHAIN_WRITE_FAILED -> "SZ-E702"
        ErrorCode.LOCAL_DECODE_FAILED -> "SZ-E703"
        ErrorCode.QUEUE_WRITE_FAILED -> "SZ-E704"
        ErrorCode.CORE_UNKNOWN_TIMEZONE -> "SZ-E705"
        ErrorCode.CORE_MALFORMED_JSON -> "SZ-E706"
        ErrorCode.UNEXPECTED_CLIENT_ERROR -> "SZ-E707"
        ErrorCode.INTERNAL -> "SZ-E901"
        ErrorCode.DATABASE_UNAVAILABLE -> "SZ-E902"
    }

/** Null for a code this app has never heard of — a server newer than the app. */
fun errorCodeOf(wire: String): ErrorCode? =
    ErrorCode.entries.firstOrNull { it.wire == wire }

/**
 * Not every failure deserves red. An offline phone in a Swiss valley painting
 * the screen red teaches a parent to ignore the colour that means something
 * actually broke.
 *
 * Must agree with `weight` in `copy/errors.toml`; `ErrorCopyWeightTest` asserts
 * exactly that, because two sources of truth for one fact is how they drift.
 */
val ErrorCode.isUrgent: Boolean
    get() = when (this) {
        ErrorCode.UNAUTHENTICATED,
        ErrorCode.SESSION_EXPIRED,
        ErrorCode.REGISTRATION_DISABLED,
        ErrorCode.NOT_FOUND,
        ErrorCode.CHILD_NOT_FOUND,
        ErrorCode.PAIRING_CODE_EXPIRED,
        ErrorCode.RATE_LIMITED,
        ErrorCode.OFFLINE,
        ErrorCode.TIMEOUT,
        ErrorCode.NOTIFICATION_PERMISSION_MISSING,
        ErrorCode.MEDIA_NOTIFICATION_ACCESS_MISSING,
        ErrorCode.BACKGROUND_REFRESH_DISABLED,
        -> false

        else -> true
    }

/**
 * The resource name `error_copy.xml` uses for this code — `SZ-E101` becomes
 * `error_SZ_E101_title`. Resolved by name rather than through `R`, because a
 * code the catalog reaches only on other platforms has no `R` field here and
 * would not compile; [ch.jorisda.schirmziit.agent.ui.parent.errorCopy] falls
 * back to SZ-E901 for exactly that case.
 */
fun errorCopyResource(code: ErrorCode, part: String): String =
    "error_${code.wire.replace('-', '_')}_$part"
