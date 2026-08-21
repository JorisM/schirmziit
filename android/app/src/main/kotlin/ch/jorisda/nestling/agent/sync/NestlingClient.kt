package ch.jorisda.nestling.agent.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class EnrollResult(val deviceId: String, val token: String)

class IngestFailure(val status: Int) : Exception("ingest failed with HTTP $status")

class NestlingClient(baseUrl: String, private val client: OkHttpClient) {
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
