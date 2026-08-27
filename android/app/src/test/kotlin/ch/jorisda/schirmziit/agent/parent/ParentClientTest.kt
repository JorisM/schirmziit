package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.jar's org.json is a stub whose methods return null.
@RunWith(RobolectricTestRunner::class)
class ParentClientTest {

    private fun client(server: MockWebServer, session: ParentSessionStore = InMemoryParentSession()) =
        ParentClient(server.url("/").toString(), OkHttpClient(), session)

    @Test
    fun `signing in keeps the session cookie and the server it belongs to`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setBody("""{"ok":true}""")
                    .addHeader("set-cookie", "schirmziit_session=abc; HttpOnly; Path=/"),
            )
            start()
        }
        val session = InMemoryParentSession()

        client(server, session).signIn("parent@example.ch", "hunter2")

        assertEquals("schirmziit_session=abc", session.cookie)
        assertNotNull(session.baseUrl)
        assertEquals("/v1/auth/login", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `a sign-in that returns no cookie is not a session`() {
        // The shape a proxy stripping Set-Cookie takes. Reading it as success
        // would drop a parent into a dashboard that 401s on every read, which
        // is worse than being told the sign-in failed.
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody("""{"ok":true}"""))
            start()
        }
        val session = InMemoryParentSession()

        val thrown = assertThrows(ApiException::class.java) {
            client(server, session).signIn("parent@example.ch", "hunter2")
        }

        assertEquals(ErrorCode.BAD_RESPONSE_BODY, thrown.failure.code)
        assertNull(session.cookie)
        server.shutdown()
    }

    @Test
    fun `wrong credentials arrive as the catalog's own code, not as a status`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(401).setBody(
                    """{"type":"about:blank","title":"invalid credentials","status":401,
                        "detail":"no","code":"SZ-E101","ref":"a1b2c3"}""",
                ),
            )
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            client(server).signIn("parent@example.ch", "wrong")
        }

        assertEquals(ErrorCode.INVALID_CREDENTIALS, thrown.failure.code)
        assertEquals("SZ-E101", thrown.failure.wire)
        assertEquals("the server's own reference is what a parent reports", "a1b2c3", thrown.failure.ref)
        server.shutdown()
    }

    @Test
    fun `a captcha page is never read as a problem the app understands`() {
        // The invariant, at the parent surface: something answering in the
        // server's place must throw with SZ-E504, never be parsed as anything.
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(403)
                    .setBody("<html><body>Sign in to the guest network</body></html>"),
            )
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).children()
        }

        assertEquals(ErrorCode.BAD_RESPONSE_BODY, thrown.failure.code)
        assertEquals(403, thrown.failure.httpStatus)
        server.shutdown()
    }

    @Test
    fun `a bare json object from a proxy is not a well-formed problem`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(502).setBody("{}"))
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).children()
        }

        assertEquals(ErrorCode.BAD_RESPONSE_BODY, thrown.failure.code)
        server.shutdown()
    }

    @Test
    fun `a server older than the catalog still produces a reportable failure`() {
        // No `code`, no `ref`: a self-hoster upgrades on their own schedule, and
        // an app newer than its server is a normal state for this product.
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"about:blank","title":"internal","status":500,"detail":"boom"}""",
                ),
            )
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).children()
        }

        assertEquals(ErrorCode.INTERNAL, thrown.failure.code)
        assertEquals("a locally made reference still identifies the occurrence", 6, thrown.failure.ref.length)
        server.shutdown()
    }

    @Test
    fun `a code this app has never heard of does not lose the error`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(500).setBody(
                    """{"type":"about:blank","title":"newer","status":500,"detail":"x",
                        "code":"SZ-E999","ref":"ffeedd"}""",
                ),
            )
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).children()
        }

        assertEquals(ErrorCode.INTERNAL, thrown.failure.code)
        assertEquals("ffeedd", thrown.failure.ref)
        server.shutdown()
    }

    @Test
    fun `children carry today's total and the session cookie goes up`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setBody(
                    """[{"id":"c1","display_name":"Alice","today_ms":5400000},
                        {"id":"c2","display_name":"Bruno","today_ms":0}]""",
                ),
            )
            start()
        }

        val children = client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).children()

        assertEquals(listOf("Alice", "Bruno"), children.map { it.displayName })
        assertEquals(5_400_000L, children[0].todayMs)
        val request = server.takeRequest()
        assertEquals("schirmziit_session=abc", request.getHeader("cookie"))
        assertTrue("the caller's own zone is what today means", request.path!!.contains("tz="))
        server.shutdown()
    }

    @Test
    fun `a read without a session never reaches the network`() {
        // 401 is what the server would answer; asking it at all would be the
        // client shrugging at its own state.
        val thrown = assertThrows(ApiException::class.java) {
            ParentClient("https://api.example.ch", OkHttpClient(), InMemoryParentSession()).children()
        }
        assertEquals(ErrorCode.UNAUTHENTICATED, thrown.failure.code)
    }

    @Test
    fun `a phone is disconnected by its own id, never through the child's route`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            start()
        }

        client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).revokeDevice("d9")

        val request = server.takeRequest()
        assertEquals("/v1/devices/d9", request.path)
        assertEquals("DELETE", request.method)
        server.shutdown()
    }

    @Test
    fun `removing a child uses the child route`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(204))
            start()
        }

        client(server, InMemoryParentSession(cookie = "schirmziit_session=abc")).removeChild("c1")

        assertEquals("/v1/children/c1", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `signing out ends the session even when the server does not answer`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(500))
            start()
        }
        val session = InMemoryParentSession(cookie = "schirmziit_session=abc", baseUrl = "x")

        client(server, session).signOut()

        assertNull("a parent who tapped sign out is signed out", session.cookie)
        assertNull(session.baseUrl)
        server.shutdown()
    }

    @Test
    fun `usage is handed back unparsed for the core to read`() {
        val body = """{"child_id":"c1","from":"2026-08-20","to":"2026-08-20","bucket":"hour",
            "tz":"Europe/Zurich","devices":[],"series":[],"device_totals":[]}"""
        val server = MockWebServer().apply {
            enqueue(MockResponse().setBody(body))
            start()
        }

        val raw = client(server, InMemoryParentSession(cookie = "schirmziit_session=abc"))
            .usage("c1", "2026-08-20", "2026-08-20", "hour")

        assertTrue(raw.contains("device_totals"))
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/v1/children/c1/usage"))
        assertTrue(path.contains("bucket=hour"))
        server.shutdown()
    }

    @Test
    fun `devices are read off the usage body, including one that never reported`() {
        val body = """{"child_id":"c1","from":"2026-08-20","to":"2026-08-20","bucket":"hour",
            "tz":"Europe/Zurich","series":[],"device_totals":[],
            "devices":[
              {"id":"d1","label":"Fairphone","last_seen_at":"2026-08-20T18:04:00Z","stale":false},
              {"id":"d2","label":"Old tablet","stale":true}
            ]}"""

        val devices = ParentClient.devices(body)

        assertEquals(listOf("Fairphone", "Old tablet"), devices.map { it.label })
        assertNotNull(devices[0].lastSeenAtMillis)
        // Never reported is not "reported long ago": the screen says so in words.
        assertNull(devices[1].lastSeenAtMillis)
        assertTrue(devices[1].stale)
    }
}
