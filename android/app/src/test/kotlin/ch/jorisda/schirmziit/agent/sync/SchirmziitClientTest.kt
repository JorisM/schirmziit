package ch.jorisda.schirmziit.agent.sync

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
class SchirmziitClientTest {
    @Test
    fun `enroll exchanges a code for a token`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(201).setBody("""{"device_id":"d1","token":"t1"}"""))
            start()
        }
        val result = SchirmziitClient(server.url("/").toString(), OkHttpClient())
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
        SchirmziitClient(server.url("/").toString(), OkHttpClient()).ingest("t1", """{"schema":1}""")

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
        val client = SchirmziitClient(server.url("/").toString(), OkHttpClient())
        val thrown = assertThrows(IngestFailure::class.java) { client.ingest("t1", "{}") }
        assertEquals(413, thrown.status)
        server.shutdown()
    }

    @Test
    fun `signIn returns the session cookie without its attributes`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("set-cookie", "schirmziit_session=abc123; Path=/; HttpOnly; SameSite=Lax")
                    .setBody("""{"ok":true}""")
            )
            start()
        }

        val session = SchirmziitClient(server.url("/").toString(), OkHttpClient())
            .signIn("anna@example.ch", "a long password")

        assertEquals("schirmziit_session=abc123", session?.cookie)
        assertEquals("/v1/auth/login", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `a wrong parent password is null, not an exception`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401))
            start()
        }

        val session = SchirmziitClient(server.url("/").toString(), OkHttpClient())
            .signIn("anna@example.ch", "wrong password")

        assertEquals(null, session)
        server.shutdown()
    }

    @Test
    fun `children are read as the signed-in parent`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{"id":"c1","display_name":"Emma"},{"id":"c2","display_name":"Noah"}]"""
                )
            )
            start()
        }

        val children = SchirmziitClient(server.url("/").toString(), OkHttpClient())
            .children(ParentSession("schirmziit_session=abc123"))

        assertEquals(listOf("Emma", "Noah"), children.map { it.displayName })
        val request = server.takeRequest()
        assertEquals("/v1/children", request.path)
        assertEquals("schirmziit_session=abc123", request.getHeader("cookie"))
        server.shutdown()
    }

    @Test
    fun `claimDevice enrols this phone without a code`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(201).setBody("""{"device_id":"d9","token":"t9"}"""))
            start()
        }

        val result = SchirmziitClient(server.url("/").toString(), OkHttpClient())
            .claimDevice(ParentSession("schirmziit_session=abc123"), "c1", "android", "FP4", "Emmas Fairphone")

        assertEquals("t9", result.token)
        val request = server.takeRequest()
        assertEquals("/v1/children/c1/devices", request.path)
        assertEquals("schirmziit_session=abc123", request.getHeader("cookie"))
        assertEquals("POST", request.method)
        server.shutdown()
    }

    @Test
    fun `signOut ends the parent session and never throws`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(500))
            start()
        }

        // A failure here must not fail a setup that already stored its token.
        SchirmziitClient(server.url("/").toString(), OkHttpClient())
            .signOut(ParentSession("schirmziit_session=abc123"))

        assertEquals("/v1/auth/logout", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `a server error while signing in is not reported as a wrong password`() {
        // 500 used to come back as null, i.e. "credentials wrong", which sends a
        // parent hunting for a typo that is not there.
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(500))
            start()
        }
        val client = SchirmziitClient(server.url("/").toString(), OkHttpClient())

        val failure = assertThrows(IngestFailure::class.java) {
            client.signIn("anna@example.ch", "a long password")
        }

        assertEquals(500, failure.status)
        server.shutdown()
    }
}
