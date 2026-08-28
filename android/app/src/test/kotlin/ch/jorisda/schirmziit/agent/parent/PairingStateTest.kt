package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.ErrorCode
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: both `Uri.parse` and android.jar's org.json are stubs otherwise.
@RunWith(RobolectricTestRunner::class)
class PairingStateTest {

    private val minted = Enrollment(
        code = "K7MNPQ",
        expiresAtMillis = 1_787_997_600_000L,
        qrPayload = "schirmziit://enroll?url=https://api.schirmziit.ch&code=K7MNPQ",
        qr = QrMatrix(3, listOf("101", "010", "101")),
    )

    // ─── minting over the wire ───────────────────────────────────────────

    @Test
    fun `minting posts and reads the code back`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"code":"K7MNPQ","expires_at":"2026-08-28T10:00:00Z",
                        "qr_payload":"schirmziit://enroll?url=https://api.schirmziit.ch&code=K7MNPQ"}""",
                ),
            )
            start()
        }

        val result = ParentClient(
            server.url("/").toString(),
            OkHttpClient(),
            InMemoryParentSession(cookie = "schirmziit_session=abc"),
        ).mintEnrollment("c1")

        assertEquals("K7MNPQ", result.code)
        assertEquals(
            "the code has to be as long as the child app will accept",
            ch.jorisda.schirmziit.agent.pair.ENROLL_CODE_LENGTH,
            result.code.length,
        )
        assertTrue(result.expiresAtMillis > 0L)
        val request = server.takeRequest()
        assertEquals("/v1/children/c1/enrollments", request.path)
        assertEquals("POST", request.method)
        server.shutdown()
    }

    @Test
    fun `minting reads back the square the server drew`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"code":"K7MNPQ","expires_at":"2026-08-28T10:00:00Z","qr_payload":"x",
                        "qr":{"size":3,"rows":["101","010","101"]}}""",
                ),
            )
            start()
        }

        val result = ParentClient(
            server.url("/").toString(),
            OkHttpClient(),
            InMemoryParentSession(cookie = "schirmziit_session=abc"),
        ).mintEnrollment("c1")

        assertEquals(QrMatrix(3, listOf("101", "010", "101")), result.qr)
        // Read by row, then column. A renderer fed a transposed matrix still
        // draws a plausible square, and a plausible square scans as nothing.
        assertTrue(result.qr!!.isDark(0, 0))
        assertFalse(result.qr!!.isDark(1, 0))
        assertTrue(result.qr!!.isDark(1, 1))
        server.shutdown()
    }

    @Test
    fun `a server that drew no square still mints a usable code`() {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"code":"K7MNPQ","expires_at":"2026-08-28T10:00:00Z","qr_payload":"x"}""",
                ),
            )
            start()
        }

        val result = ParentClient(
            server.url("/").toString(),
            OkHttpClient(),
            InMemoryParentSession(cookie = "schirmziit_session=abc"),
        ).mintEnrollment("c1")

        // The square is a convenience; the code is the pairing. A missing
        // matrix must not throw the code away with it.
        assertNull(result.qr)
        assertEquals("K7MNPQ", result.code)
        server.shutdown()
    }

    // ─── a square that is not square ─────────────────────────────────────

    @Test
    fun `a ragged or truncated matrix is no matrix at all`() {
        // Every one of these renders as a square a camera cannot read, which
        // looks to a parent like their phone is at fault.
        assertNull(qrMatrixFrom(JSONObject("""{"size":3,"rows":["101","01","101"]}""")))
        assertNull(qrMatrixFrom(JSONObject("""{"size":3,"rows":["101","010"]}""")))
        assertNull(qrMatrixFrom(JSONObject("""{"size":0,"rows":[]}""")))
        assertNull(qrMatrixFrom(JSONObject("""{"rows":["1"]}""")))
        assertNull(qrMatrixFrom(JSONObject("""{"size":1}""")))
        assertNull(qrMatrixFrom(JSONObject("""{"size":2,"rows":["1x","01"]}""")))
        assertNull(qrMatrixFrom(null))
    }

    @Test
    fun `a mint whose expiry cannot be read is treated as already gone`() {
        // Never shown as valid forever: a code with an unreadable window is one
        // the server may already be refusing, and saying "valid until ?" sends a
        // parent to a phone that will not take it.
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201)
                    .setBody("""{"code":"K7MNPQ","expires_at":"not a date","qr_payload":"x"}"""),
            )
            start()
        }

        val result = ParentClient(
            server.url("/").toString(),
            OkHttpClient(),
            InMemoryParentSession(cookie = "schirmziit_session=abc"),
        ).mintEnrollment("c1")

        assertEquals(0L, result.expiresAtMillis)
        assertTrue(enrollmentExpired(result.expiresAtMillis, nowMillis = 1L))
        server.shutdown()
    }

    @Test
    fun `a mint that answers with a proxy page throws`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(201).setBody("<html>hello</html>"))
            start()
        }

        val thrown = assertThrows(ApiException::class.java) {
            ParentClient(
                server.url("/").toString(),
                OkHttpClient(),
                InMemoryParentSession(cookie = "schirmziit_session=abc"),
            ).mintEnrollment("c1")
        }

        assertEquals(ErrorCode.BAD_RESPONSE_BODY, thrown.failure.code)
        server.shutdown()
    }

    // ─── keeping a usable code on screen ─────────────────────────────────

    @Test
    fun `a failed mint keeps the code already on screen`() {
        val showing = mergeEnrollment(PairingState(busy = true), Result.success(minted))
        assertEquals("K7MNPQ", showing.enrollment?.code)

        val after = mergeEnrollment(
            showing.copy(busy = true),
            Result.failure(IOException("no route to host")),
        )

        // The old code may well still be valid, and it is the only thing the
        // parent can act on.
        assertEquals("K7MNPQ", after.enrollment?.code)
        assertNotNull(after.failure)
        assertFalse(after.busy)
    }

    @Test
    fun `a failed first mint has an error and no code`() {
        val after = mergeEnrollment(
            PairingState(busy = true),
            Result.failure(IOException("nope")),
        )

        assertNull(after.enrollment)
        assertNotNull(after.failure)
        assertFalse(after.busy)
    }

    @Test
    fun `a fresh mint clears the previous failure`() {
        val failed = mergeEnrollment(PairingState(), Result.failure(IOException("nope")))

        val after = mergeEnrollment(failed, Result.success(minted))

        assertNull("a code that arrived is not an error state", after.failure)
        assertEquals("K7MNPQ", after.enrollment?.code)
    }

    // ─── the window ──────────────────────────────────────────────────────

    @Test
    fun `the instant a code names is already refused`() {
        // The server's window is exclusive (`expires_at > now()`), so equality
        // is expired — off by one here sends a parent to a refused code.
        assertTrue(enrollmentExpired(1_000L, nowMillis = 1_000L))
        assertTrue(enrollmentExpired(1_000L, nowMillis = 1_001L))
        assertFalse(enrollmentExpired(1_000L, nowMillis = 999L))
    }

    // ─── the address beside the code ─────────────────────────────────────

    @Test
    fun `the server address comes out of the deep link`() {
        assertEquals("https://api.schirmziit.ch", enrollmentServerAddress(minted.qrPayload))
    }

    @Test
    fun `an unreadable payload still shows the parent something`() {
        // A blank line beside the code is worse than a long one: this is the
        // half of the pairing whose failure is silent.
        assertEquals("nonsense", enrollmentServerAddress("nonsense"))
        assertEquals(
            "schirmziit://enroll?code=K7MNPQ",
            enrollmentServerAddress("schirmziit://enroll?code=K7MNPQ"),
        )
    }
}
