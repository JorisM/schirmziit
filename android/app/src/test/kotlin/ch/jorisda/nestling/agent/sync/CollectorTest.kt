package ch.jorisda.nestling.agent.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ch.jorisda.nestling.agent.core.CoreBridge
import ch.jorisda.nestling.agent.core.EventKind
import ch.jorisda.nestling.agent.core.RawEvent
import ch.jorisda.nestling.agent.store.AgentDatabase
import ch.jorisda.nestling.agent.store.FakeAgentSettings
import ch.jorisda.nestling.agent.usage.FakeUsageSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectorTest {
    private val noon = 1_787_313_600_000L
    private val hour = 3_600_000L
    private lateinit var db: AgentDatabase
    private lateinit var server: MockWebServer
    private lateinit var store: FakeAgentSettings

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AgentDatabase::class.java,
        ).allowMainThreadQueries().build()
        server = MockWebServer().apply { start() }
        store = FakeAgentSettings(deviceToken = "test-token")
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private fun collector(events: List<RawEvent>, now: Long) = Collector(
        bridge = CoreBridge(),
        source = FakeUsageSource(events, mapOf("com.a" to "App A")),
        dao = db.queue(),
        store = store,
        nowMillis = { now },
        tz = { "Europe/Zurich" },
    )

    private fun client() = NestlingClient(server.url("/").toString(), OkHttpClient())

    @Test
    fun `collect queues hours and remembers the still-open app`() {
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
            RawEvent(noon + 700_000, EventKind.Resumed("com.b")),
        )
        val queued = collector(events, noon + hour).collect()

        assertTrue("expected at least one hour queued", queued >= 1)
        assertEquals("com.b", db.queue().carryOver()?.packageName)
    }

    @Test
    fun `collecting twice in the same hour replaces rather than duplicates`() {
        val first = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 300_000, EventKind.Paused("com.a")),
        )
        collector(first, noon + 1_800_000).collect()
        val afterFirst = db.queue().pending().size

        val second = first + listOf(
            RawEvent(noon + 1_900_000, EventKind.Resumed("com.a")),
            RawEvent(noon + 2_400_000, EventKind.Paused("com.a")),
        )
        collector(second, noon + hour).collect()

        assertEquals("same hour must not queue twice", afterFirst, db.queue().pending().size)
    }

    @Test
    fun `sync posts the queue and clears what the server accepted`() {
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
        )
        val c = collector(events, noon + hour)
        c.collect()
        val queuedHour = db.queue().pending().first().hourStartMillis
        val iso = java.time.Instant.ofEpochMilli(queuedHour).toString()

        server.enqueue(MockResponse().setBody("""{"accepted":["$iso"],"rejected":[]}"""))
        val outcome = c.sync(client())

        assertEquals(1, outcome.sent)
        assertEquals(0, outcome.remaining)
        assertNull(outcome.error)
    }

    @Test
    fun `a captcha page leaves the queue intact and records the error`() {
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
        )
        val c = collector(events, noon + hour)
        c.collect()
        val before = db.queue().pending().size

        server.enqueue(MockResponse().setBody("<html>turnstile</html>"))
        val outcome = c.sync(client())

        assertEquals("the queue must survive an unparseable response", before, db.queue().pending().size)
        assertEquals(0, outcome.sent)
        assertTrue("expected an error to be recorded", outcome.error != null)
        assertEquals(outcome.error, store.lastError)
    }

    @Test
    fun `a 401 leaves the queue intact`() {
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
        )
        val c = collector(events, noon + hour)
        c.collect()

        server.enqueue(MockResponse().setResponseCode(401))
        val outcome = c.sync(client())

        assertEquals(1, db.queue().pending().size)
        assertTrue(outcome.error, outcome.error!!.contains("401"))
    }

    @Test
    fun `raw events are kept for debugging and pruned past the window`() {
        val stale = RawEvent(noon - 8 * 24 * hour, EventKind.Unlock)
        val fresh = RawEvent(noon, EventKind.Unlock)
        collector(listOf(stale, fresh), noon + hour).collect()
        assertEquals(1, db.queue().rawCount())
    }
}
