package ch.jorisda.nestling.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real Rust core on the JVM through JNA, so the FFI boundary is proven
 * without a device or an emulator. `just android-bindings` must have run.
 */
class CoreBridgeTest {
    private val bridge = CoreBridge()
    private val noon = 1_787_313_600_000L
    private val hour = 3_600_000L

    @Test
    fun `stitches a session across the boundary`() {
        val out = bridge.stitch(
            prevOpen = null,
            events = listOf(
                RawEvent(noon, EventKind.Resumed("com.a")),
                RawEvent(noon + 300_000, EventKind.Paused("com.a")),
            ),
            windowEndMillis = noon + hour,
        )
        assertEquals(1, out.closed.size)
        assertEquals(300_000, out.closed[0].endMillis - out.closed[0].startMillis)
        assertNull(out.open)
    }

    @Test
    fun `carries the still-open app back out`() {
        val out = bridge.stitch(
            prevOpen = null,
            events = listOf(RawEvent(noon, EventKind.Resumed("com.b"))),
            windowEndMillis = noon + 1_800_000,
        )
        assertEquals("com.b", out.open?.packageName)
        assertEquals(noon, out.open?.sinceMillis)
    }

    @Test
    fun `builds the ingest body the server accepts`() {
        val hours = bridge.bucket(
            sessions = listOf(Session("com.a", noon, noon + 600_000)),
            unlockMillis = listOf(noon),
            tz = "Europe/Zurich",
            labels = mapOf("com.a" to "App A"),
            computedAtMillis = noon + hour,
        )
        assertEquals(1, hours.size)

        val body = bridge.buildIngestBody(hours, noon + hour)
        assertTrue(body, body.contains("\"schema\":1"))
        assertTrue(body, body.contains("\"label\":\"App A\""))
    }

    @Test
    fun `refuses to interpret a captcha page as success`() {
        val hours = bridge.bucket(
            sessions = listOf(Session("com.a", noon, noon + 1000)),
            unlockMillis = emptyList(),
            tz = "UTC",
            labels = emptyMap(),
            computedAtMillis = noon,
        )
        // A queue-wiping bug would return an empty list here instead of throwing.
        val attempt = runCatching { bridge.applyResult(hours, "<html>captcha</html>") }
        assertTrue("expected a failure, got ${attempt.getOrNull()}", attempt.isFailure)
    }

    @Test
    fun `plans the oldest hour first`() {
        val hours = bridge.bucket(
            sessions = listOf(
                Session("com.a", noon, noon + 600_000),
                Session("com.a", noon + hour, noon + hour + 600_000),
            ),
            unlockMillis = emptyList(),
            tz = "UTC",
            labels = emptyMap(),
            computedAtMillis = noon + 2 * hour,
        )
        val plan = bridge.planSync(hours, maxRows = 1u)
        assertEquals(1, plan.send.size)
        assertEquals(1, plan.deferred.size)
        assertTrue(plan.send[0].hourStartMillis < plan.deferred[0].hourStartMillis)
    }
}
