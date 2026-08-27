package ch.jorisda.schirmziit.agent.parent

import android.net.Uri

/**
 * What the pairing card holds. `enrollment == null` is "no code minted yet",
 * which is the state the card opens in — nothing is minted on appearance.
 */
data class PairingState(
    val enrollment: Enrollment? = null,
    val failure: ApiFailure? = null,
    val busy: Boolean = false,
)

/**
 * A mint's outcome. A failure deliberately keeps the code already on screen: the
 * old one may well still be valid, and blanking it takes away the only thing the
 * parent can act on.
 */
fun mergeEnrollment(
    previous: PairingState,
    minted: Result<Enrollment>,
): PairingState = minted.fold(
    onSuccess = { PairingState(enrollment = it, failure = null, busy = false) },
    onFailure = {
        previous.copy(
            failure = ApiFailure.of(it, "/v1/children/enrollments"),
            busy = false,
        )
    },
)

/**
 * The server's window is exclusive (`expires_at > now()`), so the instant it
 * names is already refused.
 */
fun enrollmentExpired(expiresAtMillis: Long, nowMillis: Long): Boolean =
    expiresAtMillis <= nowMillis

/**
 * The server address out of the deep link, which is meant for a camera rather
 * than for a person.
 *
 * Shown next to the code because this is the half of the pairing whose failure
 * is silent: a phone enrolled against the wrong host enrols exactly once and
 * then never reports again. An unparseable payload falls back to the raw string
 * — the parent still needs something to compare against what they typed, and a
 * blank line beside the code is worse than a long one.
 */
fun enrollmentServerAddress(qrPayload: String): String =
    runCatching { Uri.parse(qrPayload).getQueryParameter("url") }.getOrNull()
        ?.takeIf { it.isNotEmpty() }
        ?: qrPayload
