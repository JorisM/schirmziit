package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.random.Random
import org.json.JSONObject

/**
 * Every failure the parent screens can be shown, as one value.
 *
 * Built at the boundary — never in a composable — so a screen cannot render an
 * error without a code to put on screen and a reference to report. The
 * child agent's `IngestFailure(status)` is deliberately not reused: an HTTP
 * status is not something to show a parent, and `settings.lastError` holding a
 * raw exception message is the pattern this replaces on the parent side.
 *
 * Mirrors `ios/Sources/Api/AppError.swift`.
 */
data class ApiFailure(
    val code: ErrorCode,
    /** Six hex characters: the head of the server's request id, or a local one. */
    val ref: String,
    /** A path, never a full URL — see [pathOnly]. */
    val endpoint: String?,
    val httpStatus: Int? = null,
) {
    val wire: String get() = code.wire
    val isUrgent: Boolean get() = code.isUrgent

    companion object {
        /**
         * A response the server refused with. `problem+json` (RFC 9457) carries
         * the code and the reference; anything else did not come from our server
         * at all.
         */
        fun ofResponse(body: String, httpStatus: Int, endpoint: String?): ApiFailure {
            val problem = runCatching { JSONObject(body) }.getOrNull()
            // `type` and `title` are what an RFC 9457 body always has. A guest
            // network's login page parses as neither, and a bare `{}` from some
            // proxy must not be read as a well-formed problem with fields
            // missing.
            if (problem == null || !problem.has("type") || !problem.has("title")) {
                return badResponseBody(endpoint, httpStatus)
            }
            // Both fields are absent on a server older than the catalog, and an
            // unknown code means the server is newer than this app. Neither is
            // worth dropping the error over: the status still says what
            // happened, and a locally made reference still identifies this
            // occurrence in the parent's own screenshot.
            val code = problem.optString("code", "").takeIf { it.isNotEmpty() }
                ?.let(::errorCodeOf)
                ?: ErrorCode.INTERNAL
            val ref = problem.optString("ref", "").takeIf { it.isNotEmpty() } ?: makeRef()
            return ApiFailure(code, ref, pathOnly(endpoint), problem.optInt("status", httpStatus))
        }

        /**
         * A request that never produced a response.
         *
         * The four cases a parent can act on differently — no network, a slow
         * server, a certificate, a wrong address. A browser reports all four as
         * one opaque failure, which is why the dashboard has no TLS code and
         * this does.
         */
        fun ofTransport(error: IOException, endpoint: String?): ApiFailure {
            val code = when {
                error is SocketTimeoutException -> ErrorCode.TIMEOUT
                error is SSLException -> ErrorCode.TLS_FAILED
                // No DNS at all is the shape "the wifi is off" takes on Android;
                // OkHttp does not distinguish it from a genuinely wrong host,
                // and the copy for SZ-E501 covers both readings.
                error is UnknownHostException -> ErrorCode.OFFLINE
                else -> ErrorCode.SERVER_UNREACHABLE
            }
            return ApiFailure(code, makeRef(), pathOnly(endpoint))
        }

        /** Something answered in the server's place — a captcha, a proxy page. */
        fun badResponseBody(endpoint: String?, httpStatus: Int?): ApiFailure =
            ApiFailure(ErrorCode.BAD_RESPONSE_BODY, makeRef(), pathOnly(endpoint), httpStatus)

        /** Anything that failed on the phone itself and never touched the network. */
        fun local(code: ErrorCode, endpoint: String? = null): ApiFailure =
            ApiFailure(code, makeRef(), pathOnly(endpoint))

        /**
         * Whatever came back from a suspend block, as a failure with a code.
         * Written once so a new parent-side call cannot quietly swallow its
         * error by forgetting a branch.
         */
        fun of(error: Throwable, endpoint: String?): ApiFailure = when (error) {
            is ApiException -> error.failure
            is IOException -> ofTransport(error, endpoint)
            // A parse that blew up on a 200 body: named, so the panel can say
            // "update the app" rather than shrugging.
            else -> local(ErrorCode.LOCAL_DECODE_FAILED, endpoint)
        }

        private fun makeRef(): String =
            (0 until 3).joinToString("") { "%02x".format(Random.nextInt(256)) }

        /**
         * A self-hoster photographing this panel for a public issue must not
         * publish the address of the machine in their flat.
         */
        private fun pathOnly(endpoint: String?): String? {
            if (endpoint == null) return null
            val path = runCatching { java.net.URI(endpoint).path }.getOrNull()
            return if (path.isNullOrEmpty()) endpoint else path
        }
    }
}

/** Thrown by [ParentClient] so every caller lands on one typed failure. */
class ApiException(val failure: ApiFailure) : Exception(failure.wire)

/**
 * The block behind "copy details": what a maintainer needs, and nothing that
 * describes a family — no email, no child name, no request or response body,
 * and the endpoint as a path.
 */
fun ApiFailure.copyDetails(appVersion: String, androidRelease: String, model: String): String {
    val lines = mutableListOf(
        "$wire · $ref",
        "schirmziit $appVersion · android $androidRelease · $model",
    )
    // The path with no method in front of it: this panel is reached from reads
    // and from deletes alike, and iOS's hardcoded "GET " is wrong on the latter.
    endpoint?.let { path ->
        lines.add(path + (httpStatus?.let { " → $it" } ?: ""))
    }
    return lines.joinToString("\n")
}
