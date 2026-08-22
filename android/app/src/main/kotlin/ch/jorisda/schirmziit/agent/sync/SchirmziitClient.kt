package ch.jorisda.schirmziit.agent.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class EnrollResult(val deviceId: String, val token: String)

data class SetupChild(val id: String, val displayName: String)

/** A parent session, held for the length of a setup and never stored. */
data class ParentSession(val cookie: String)

class IngestFailure(val status: Int) : Exception("ingest failed with HTTP $status")

class SchirmziitClient(baseUrl: String, private val client: OkHttpClient) {
    private val base = baseUrl.trimEnd('/')
    private val json = "application/json".toMediaType()

    fun enroll(code: String, platform: String, model: String, label: String): EnrollResult {
        val body = JSONObject()
            .put("code", code)
            .put("platform", platform)
            .put("model", model)
            .put("label", label)
            .toString()

        val request = Request.Builder()
            .url("$base/v1/enroll")
            .post(body.toRequestBody(json))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IngestFailure(response.code)
            val parsed = JSONObject(payload)
            return EnrollResult(parsed.getString("device_id"), parsed.getString("token"))
        }
    }

    /**
     * Signs a parent in for the length of a setup. The cookie is returned rather
     * than kept: a child's phone must not hold a parent session, so the caller
     * uses it for the next two calls and then ends it.
     */
    fun signIn(email: String, password: String): ParentSession? {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val request = Request.Builder()
            .url("$base/v1/auth/login")
            .post(body.toRequestBody(json))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            // "schirmziit_session=…" — the attributes are the browser's business.
            val cookie = response.header("set-cookie")?.substringBefore(';') ?: return null
            return ParentSession(cookie)
        }
    }

    fun children(session: ParentSession): List<SetupChild> {
        val request = Request.Builder()
            .url("$base/v1/children")
            .header("cookie", session.cookie)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IngestFailure(response.code)
            val array = JSONArray(payload)
            return (0 until array.length()).map { index ->
                val child = array.getJSONObject(index)
                SetupChild(child.getString("id"), child.getString("display_name"))
            }
        }
    }

    /** Enrols this phone for a child the signed-in parent owns. No code needed. */
    fun claimDevice(
        session: ParentSession,
        childId: String,
        platform: String,
        model: String,
        label: String,
    ): EnrollResult {
        val body = JSONObject()
            .put("platform", platform)
            .put("model", model)
            .put("label", label)
            .toString()

        val request = Request.Builder()
            .url("$base/v1/children/$childId/devices")
            .header("cookie", session.cookie)
            .post(body.toRequestBody(json))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IngestFailure(response.code)
            val parsed = JSONObject(payload)
            return EnrollResult(parsed.getString("device_id"), parsed.getString("token"))
        }
    }

    /**
     * Best effort. The device token is already stored by the time this runs, and
     * an abandoned session expires on its own — so a failure here must not fail
     * a setup that otherwise worked.
     */
    fun signOut(session: ParentSession) {
        val request = Request.Builder()
            .url("$base/v1/auth/logout")
            .header("cookie", session.cookie)
            .post("".toRequestBody(json))
            .build()
        runCatching { client.newCall(request).execute().close() }
    }

    /** Returns the raw response body; the core parses it, not us. */
    fun ingest(token: String, body: String): String {
        val request = Request.Builder()
            .url("$base/v1/ingest")
            .header("authorization", "Bearer $token")
            .post(body.toRequestBody(json))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IngestFailure(response.code)
            return payload
        }
    }
}
