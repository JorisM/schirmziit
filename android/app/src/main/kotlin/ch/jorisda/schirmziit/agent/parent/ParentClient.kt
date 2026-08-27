package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ParentChild(val id: String, val displayName: String, val todayMs: Long)

/**
 * A minted one-shot enrolment code.
 *
 * `qrPayload` is the `schirmziit://enroll?url=…&code=…` deep link a camera is
 * meant to read. Android is the one place that could render it as a QR — zxing
 * is already a dependency here, for the child app's scanner — and does not yet;
 * see `docs/platform-matrix.md`.
 */
data class Enrollment(val code: String, val expiresAtMillis: Long, val qrPayload: String)

/**
 * What a purge actually removed, straight from the server's `rows_affected`.
 *
 * Counted rather than assumed: "deleted" with nothing behind it is exactly the
 * claim a family has no way to check, and a delete that matched nothing has to
 * be able to say zero instead of implying a purge.
 */
data class Purged(val usageHours: Long, val deviceHours: Long, val usageDays: Long)

data class ParentDevice(
    val id: String,
    val label: String,
    /** Null means this phone has never reported. Not the same as "long ago". */
    val lastSeenAtMillis: Long?,
    val stale: Boolean,
)

/**
 * The parent side of the API, held open by a session cookie.
 *
 * Deliberately a second client next to [ch.jorisda.schirmziit.agent.sync.SchirmziitClient]
 * rather than more methods on it. That one belongs to the child agent and
 * carries a device token; this one carries a parent session. Keeping them apart
 * is the on-device shape of the rule `crates/server/tests/tenancy.rs` proves on
 * the server: a device token reads nothing but `GET /v1/me/usage`, and a parent
 * session never rides on a child's phone.
 *
 * Every failure leaves here as an [ApiException] carrying an [ApiFailure], so no
 * screen ever has to invent a sentence for one.
 */
class ParentClient(
    baseUrl: String,
    private val client: OkHttpClient,
    private val session: ParentSessionStore,
) {
    private val base = baseUrl.trimEnd('/')
    private val json = "application/json".toMediaType()

    /**
     * Signs in and stores the cookie. Returns nothing: what the caller needs to
     * know is whether it threw.
     */
    fun signIn(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val path = "/v1/auth/login"
        val cookie = execute(
            Request.Builder().url(base + path).post(body.toRequestBody(json)).build(),
            path,
        ).use { response ->
            // "schirmziit_session=…" — the attributes are a browser's business,
            // and the value is all that goes back up.
            response.headers("set-cookie")
                .firstOrNull { it.startsWith("schirmziit_session=") }
                ?.substringBefore(';')
        }
        // A 200 with no cookie is not a session. Named rather than shrugged at:
        // it is the shape a proxy that strips Set-Cookie takes, and signing in
        // "successfully" into a dashboard that then 401s on every read is worse
        // than being told the sign-in failed.
            ?: throw ApiException(ApiFailure.local(ErrorCode.BAD_RESPONSE_BODY, path))
        session.cookie = cookie
        session.baseUrl = base
    }

    /**
     * Whether the stored cookie is still a session. iOS asks the same question
     * the same way, on launch, before it shows the dashboard: a cookie that
     * expired while the app was closed must land on the sign-in form, not on an
     * empty children list that looks like a family with no children.
     */
    fun me(): Boolean = runCatching { get("/v1/me") }.isSuccess

    /**
     * Ends the session on the server, then locally. Best effort on the wire and
     * unconditional on the phone: a parent who tapped sign out must end up
     * signed out even if the logout call blips, and an abandoned session expires
     * on its own.
     */
    fun signOut() {
        session.cookie?.let { held ->
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url("$base/v1/auth/logout")
                        .header("cookie", held)
                        .post("".toRequestBody(json))
                        .build(),
                ).execute().close()
            }
        }
        session.clear()
    }

    fun children(): List<ParentChild> {
        val body = get("/v1/children", "tz" to timeZone())
        val array = runCatching { JSONArray(body) }
            .getOrElse { throw ApiException(ApiFailure.badResponseBody("/v1/children", 200)) }
        return (0 until array.length()).map { index ->
            val child = array.getJSONObject(index)
            ParentChild(
                id = child.getString("id"),
                displayName = child.getString("display_name"),
                // Zero, never absent: a quiet day is a real number, and an
                // absent one renders as a hole.
                todayMs = child.optLong("today_ms", 0L),
            )
        }
    }

    fun createChild(displayName: String) {
        val path = "/v1/children"
        execute(
            Request.Builder()
                .url(base + path)
                .header("cookie", cookie(path))
                .post(JSONObject().put("display_name", displayName).toString().toRequestBody(json))
                .build(),
            path,
        ).close()
    }

    /**
     * Removes a child, and with them the devices reporting for them — the server
     * does both in one transaction. Without that the phone keeps uploading,
     * because a device token is authorised against `devices.revoked_at` alone.
     */
    fun removeChild(childId: String) = delete("/v1/children/$childId")

    /**
     * Disconnects one phone, by its own id. Never through the child's route:
     * `DELETE /v1/children/{id}` removes the child, which is a different and
     * much larger act than dropping one of their phones.
     */
    fun revokeDevice(deviceId: String) = delete("/v1/devices/$deviceId")

    /**
     * Deletes a child's stored figures and reports what went. The child and
     * their phones stay connected and keep reporting — only the numbers
     * collected so far are gone, which is a different and much smaller act than
     * [removeChild].
     *
     * Unlike every other delete here this one answers with a body, and the body
     * is the point: it is read strictly, so a captcha page with a 200 on it
     * throws rather than being shown to a parent as a completed purge.
     */
    fun purgeData(childId: String): Purged {
        val path = "/v1/children/$childId/data"
        val body = execute(
            Request.Builder().url(base + path).header("cookie", cookie(path)).delete().build(),
            path,
        ).use { it.body?.string().orEmpty() }

        val parsed = runCatching { JSONObject(body) }.getOrNull()
            ?: throw ApiException(ApiFailure.badResponseBody(path, 200))
        // `getLong`, not `optLong`: a body missing a count is not a purge of
        // zero rows, it is a body this app cannot read.
        return runCatching {
            Purged(
                usageHours = parsed.getLong("deleted_usage_hours"),
                deviceHours = parsed.getLong("deleted_device_hours"),
                usageDays = parsed.getLong("deleted_usage_days"),
            )
        }.getOrElse { throw ApiException(ApiFailure.badResponseBody(path, 200)) }
    }

    /**
     * The raw usage body. Returned unparsed on purpose: the numbers are read by
     * `crates/core` (`parseDayStrip`/`parseDayDetail`), the same two functions
     * the child's own screen uses, so the parent and the child cannot end up
     * disagreeing about what a day means. Only the device metadata, which the
     * core ignores, is parsed here — see [devices].
     */
    fun usage(childId: String, from: String, to: String, bucket: String): String =
        get(
            "/v1/children/$childId/usage",
            "from" to from,
            "to" to to,
            "bucket" to bucket,
            "tz" to timeZone(),
        )

    /**
     * Mints the code a child's phone is enrolled with. `POST`, so it is called
     * on a press and never on appearance: a code lives fifteen minutes and can
     * be claimed once, so a screen that mints when a parent opens it hands out
     * — and burns — a code nobody asked for.
     */
    fun mintEnrollment(childId: String): Enrollment {
        val path = "/v1/children/$childId/enrollments"
        val body = execute(
            Request.Builder()
                .url(base + path)
                .header("cookie", cookie(path))
                .post("".toRequestBody(json))
                .build(),
            path,
        ).use { it.body?.string().orEmpty() }

        val parsed = runCatching { JSONObject(body) }.getOrNull()
            ?: throw ApiException(ApiFailure.badResponseBody(path, 201))
        return Enrollment(
            code = parsed.optString("code").takeIf { it.isNotEmpty() }
                ?: throw ApiException(ApiFailure.badResponseBody(path, 201)),
            expiresAtMillis = parsed.optString("expires_at")
                .takeIf { it.isNotEmpty() }
                ?.let { stamp ->
                    runCatching {
                        java.time.OffsetDateTime.parse(stamp).toInstant().toEpochMilli()
                    }.getOrNull()
                }
                // A code whose expiry could not be read must not be shown as
                // valid forever: treat it as already gone rather than guess.
                ?: 0L,
            qrPayload = parsed.optString("qr_payload"),
        )
    }

    private fun cookie(path: String): String =
        session.cookie ?: throw ApiException(ApiFailure.local(ErrorCode.UNAUTHENTICATED, path))

    private fun get(path: String, vararg query: Pair<String, String>): String {
        val url = (base + path).toHttpUrlOrNull()?.newBuilder()
            ?.apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
            ?.build()
            ?: throw ApiException(ApiFailure.local(ErrorCode.BASE_URL_NOT_CONFIGURED, path))

        return execute(
            Request.Builder().url(url).header("cookie", cookie(path)).build(),
            path,
        ).use { it.body?.string().orEmpty() }
    }

    private fun delete(path: String) {
        execute(
            Request.Builder().url(base + path).header("cookie", cookie(path)).delete().build(),
            path,
        ).close()
    }

    /**
     * The one place a response becomes either a body or a typed failure. An
     * IOException is a transport failure; a non-2xx is read as problem+json and
     * falls back to SZ-E504 when it is not — a captcha page must never be read
     * as anything but "that did not come from your server".
     */
    private fun execute(request: Request, path: String): okhttp3.Response {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw ApiException(ApiFailure.ofTransport(error, path))
        }
        if (!response.isSuccessful) {
            val body = response.use { it.body?.string().orEmpty() }
            throw ApiException(ApiFailure.ofResponse(body, response.code, path))
        }
        return response
    }

    companion object {
        /**
         * The devices on a usage response. The core's parser ignores them — it
         * exists to answer "what happened on this day", and a phone's staleness
         * is not part of that — so this is the one part of the body Kotlin reads.
         */
        fun devices(usageJson: String): List<ParentDevice> {
            val array = runCatching { JSONObject(usageJson).getJSONArray("devices") }
                .getOrElse { throw ApiException(ApiFailure.badResponseBody(null, null)) }
            return (0 until array.length()).map { index ->
                val device = array.getJSONObject(index)
                ParentDevice(
                    id = device.getString("id"),
                    label = device.getString("label"),
                    lastSeenAtMillis = device.optString("last_seen_at")
                        .takeIf { it.isNotEmpty() && it != "null" }
                        ?.let { stamp ->
                            runCatching {
                                java.time.OffsetDateTime.parse(stamp).toInstant().toEpochMilli()
                            }.getOrNull()
                        },
                    stale = device.optBoolean("stale", false),
                )
            }
        }

        private fun timeZone(): String = java.util.TimeZone.getDefault().id
    }
}
