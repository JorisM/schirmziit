package ch.jorisda.schirmziit.agent.pair

import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import ch.jorisda.schirmziit.agent.sync.SchirmziitClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ParentSetupTest {
    private fun setup(server: MockWebServer, settings: FakeAgentSettings) =
        ParentSetup(SchirmziitClient(server.url("/").toString(), OkHttpClient()), settings)

    private fun loggedIn() = MockResponse()
        .setResponseCode(200)
        .setHeader("set-cookie", "schirmziit_session=abc; Path=/")
        .setBody("""{"ok":true}""")

    @Test
    fun `sign in returns the children the parent can pick from`() {
        val server = MockWebServer().apply {
            enqueue(loggedIn())
            enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"c1","display_name":"Emma"}]"""))
            start()
        }

        val result = setup(server, FakeAgentSettings()).signIn("anna@example.ch", "a long password")

        assertTrue(result is ParentSetup.SignIn.Ready)
        assertEquals(listOf("Emma"), (result as ParentSetup.SignIn.Ready).children.map { it.displayName })
        server.shutdown()
    }

    @Test
    fun `a wrong password is its own answer so the screen can say so`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401))
            start()
        }

        val result = setup(server, FakeAgentSettings()).signIn("anna@example.ch", "wrong password")

        assertTrue(result is ParentSetup.SignIn.WrongCredentials)
        server.shutdown()
    }

    @Test
    fun `claiming stores the device token and then ends the parent session`() {
        val settings = FakeAgentSettings()
        val server = MockWebServer().apply {
            enqueue(loggedIn())
            enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"c1","display_name":"Emma"}]"""))
            enqueue(MockResponse().setResponseCode(201).setBody("""{"device_id":"d1","token":"t1"}"""))
            enqueue(MockResponse().setResponseCode(204))
            start()
        }
        val base = server.url("/").toString()
        val flow = setup(server, settings)

        val ready = flow.signIn("anna@example.ch", "a long password") as ParentSetup.SignIn.Ready
        val claimed = flow.claim(ready.session, base, "c1", "FP4", "Emmas Fairphone")

        assertEquals("t1", claimed.getOrNull()?.token)
        assertEquals("t1", settings.deviceToken)
        assertEquals(base, settings.baseUrl)

        val requests = (1..4).map { server.takeRequest() }
        assertEquals("/v1/auth/login", requests[0].path)
        // tz is required by the server; asserted as present rather than pinning
        // the device's own zone as a literal, which would just be re-asserting
        // the implementation.
        assertTrue(requests[1].path!!.startsWith("/v1/children"))
        assertTrue(!requests[1].requestUrl!!.queryParameter("tz").isNullOrBlank())
        assertEquals("/v1/children/c1/devices", requests[2].path)
        assertEquals("/v1/auth/logout", requests[3].path)
        server.shutdown()
    }

    @Test
    fun `a failed claim stores nothing and still ends the session`() {
        val settings = FakeAgentSettings()
        val server = MockWebServer().apply {
            enqueue(loggedIn())
            enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"c1","display_name":"Emma"}]"""))
            enqueue(MockResponse().setResponseCode(500))
            enqueue(MockResponse().setResponseCode(204))
            start()
        }
        val base = server.url("/").toString()
        val flow = setup(server, settings)

        val ready = flow.signIn("anna@example.ch", "a long password") as ParentSetup.SignIn.Ready
        val claimed = flow.claim(ready.session, base, "c1", "FP4", "phone")

        assertTrue(claimed.isFailure)
        assertNull(settings.deviceToken)
        // Still logged out: an abandoned session on a child's phone is the thing
        // this whole flow exists to avoid.
        repeat(3) { server.takeRequest() }
        assertEquals("/v1/auth/logout", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `unpairing needs the parent password, so a child cannot stop reporting`() {
        val settings = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "t1")
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401))
            start()
        }

        val result = setup(server, settings).unpair("anna@example.ch", "the child's guess")

        assertTrue(result is ParentSetup.Unpair.WrongCredentials)
        // The point of the whole guard: a wrong password changes nothing.
        assertEquals("t1", settings.deviceToken)
        assertEquals("https://api.example.ch", settings.baseUrl)
        server.shutdown()
    }

    @Test
    fun `unpairing clears the token and then ends the parent session`() {
        val settings = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "t1")
        val server = MockWebServer().apply {
            enqueue(loggedIn())
            enqueue(MockResponse().setResponseCode(204))
            start()
        }

        val result = setup(server, settings).unpair("anna@example.ch", "a long password")

        assertTrue(result is ParentSetup.Unpair.Done)
        assertNull(settings.deviceToken)
        assertNull(settings.baseUrl)
        // Bounded, not the blocking overload: an implementation that stops
        // calling the server at all would hang this test forever instead of
        // failing it, and a test that hangs reports nothing.
        assertEquals("/v1/auth/login", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        // Same rule as claim(): the session opened to check the password is
        // never left behind on a child's phone.
        assertEquals("/v1/auth/logout", server.takeRequest(2, TimeUnit.SECONDS)?.path)
        server.shutdown()
    }

    @Test
    fun `an unreachable server leaves the phone paired rather than half-unpaired`() {
        val settings = FakeAgentSettings(baseUrl = "https://api.example.ch", deviceToken = "t1")
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(502))
            start()
        }

        val result = setup(server, settings).unpair("anna@example.ch", "a long password")

        assertTrue("expected Failed, got $result", result is ParentSetup.Unpair.Failed)
        // A phone that silently stopped reporting because the server blipped is
        // the failure this product exists to avoid.
        assertEquals("t1", settings.deviceToken)
        server.shutdown()
    }

    @Test
    fun `an unreachable server is its own answer, not wrong credentials`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(502))
            start()
        }

        val result = setup(server, FakeAgentSettings()).signIn("anna@example.ch", "a long password")

        assertTrue("expected Failed, got $result", result is ParentSetup.SignIn.Failed)
        assertTrue(
            "the message must carry the status so the screen can say something useful",
            (result as ParentSetup.SignIn.Failed).message.contains("502"),
        )
        server.shutdown()
    }
}
