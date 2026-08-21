package ch.jorisda.nestling.agent.sync

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.jar's org.json is a stub whose methods return null.
@RunWith(RobolectricTestRunner::class)
class NestlingClientTest {
    @Test
    fun `enroll exchanges a code for a token`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(201).setBody("""{"device_id":"d1","token":"t1"}"""))
            start()
        }
        val result = NestlingClient(server.url("/").toString(), OkHttpClient())
            .enroll("ABCD1234", "android", "FP4", "Kid's phone")

        assertEquals("d1", result.deviceId)
        assertEquals("t1", result.token)
        assertEquals("/v1/enroll", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `ingest sends the bearer token`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""{"accepted":[],"rejected":[]}"""))
            start()
        }
        NestlingClient(server.url("/").toString(), OkHttpClient()).ingest("t1", """{"schema":1}""")

        val request = server.takeRequest()
        assertEquals("/v1/ingest", request.path)
        assertEquals("Bearer t1", request.getHeader("authorization"))
        server.shutdown()
    }

    @Test
    fun `a non-2xx ingest throws with the status`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(413))
            start()
        }
        val client = NestlingClient(server.url("/").toString(), OkHttpClient())
        val thrown = assertThrows(IngestFailure::class.java) { client.ingest("t1", "{}") }
        assertEquals(413, thrown.status)
        server.shutdown()
    }
}
