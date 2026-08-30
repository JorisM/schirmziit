package ch.jorisda.schirmziit.agent.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.core.EventKind
import ch.jorisda.schirmziit.agent.core.RawEvent
import ch.jorisda.schirmziit.agent.playback.FakePlaybackReader
import ch.jorisda.schirmziit.agent.playback.PlaybackHandler
import ch.jorisda.schirmziit.agent.playback.PlaybackReader
import ch.jorisda.schirmziit.agent.playback.PlaybackState
import ch.jorisda.schirmziit.agent.store.AgentDatabase
import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import ch.jorisda.schirmziit.agent.usage.FakeUsageSource
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun collector(
        events: List<RawEvent>,
        now: Long,
        playback: PlaybackReader = FakePlaybackReader(),
    ) = Collector(
        bridge = CoreBridge(),
        source = FakeUsageSource(events, mapOf("com.a" to "App A")),
        dao = db.queue(),
        store = store,
        playback = playback,
        nowMillis = { now },
        tz = { "Europe/Zurich" },
    )

    /** One stretch of listening, written the way the bound listener writes it. */
    private fun listened(fromMillis: Long, toMillis: Long, packageName: String = "com.a") {
        var at = fromMillis
        // Inline: the real handler writes on its own thread, and this test has
        // to see the rows before it collects.
        val handler = PlaybackHandler(db.queue(), { at }, { it.run() })
        handler.onSnapshot(listOf(PlaybackState(packageName, playing = true)))
        at = toMillis
        handler.onSnapshot(emptyList())
    }

    private fun client() = SchirmziitClient(server.url("/").toString(), OkHttpClient())

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
    fun `re-collecting an hour must not shrink its total`() {
        // Regression: collect() used to start at the carry-over watermark, so a
        // later run re-derived only the part of the hour AFTER that watermark.
        // Because both the queue and the server replace an hour rather than
        // adding to it, the shorter recomputation overwrote the fuller one.
        // Observed on a real Fairphone as totals dropping 3.2 -> 1.8 minutes.
        //
        // The shape that reproduces it: com.a is used, then another app takes
        // the foreground (setting the watermark), then com.a is used again in
        // the same hour.
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
            RawEvent(noon + 700_000, EventKind.Resumed("com.b")),
            RawEvent(noon + 900_000, EventKind.Resumed("com.a")),
            RawEvent(noon + 1_200_000, EventKind.Paused("com.a")),
        )

        collector(events, noon + 800_000).collect()
        assertEquals("watermark should be the still-open app", "com.b", db.queue().carryOver()?.packageName)
        val afterFirst = foregroundMsFor("com.a")
        assertEquals(600_000, afterFirst)

        collector(events, noon + 1_500_000).collect()
        val afterSecond = foregroundMsFor("com.a")

        assertTrue(
            "re-collecting lost earlier usage: had ${afterFirst}ms, now ${afterSecond}ms",
            afterSecond >= afterFirst,
        )
        assertEquals("both sessions in the hour", 900_000, afterSecond)
    }

    private fun foregroundMsFor(packageName: String): Long =
        db.queue().pending().sumOf { row ->
            val hour = org.json.JSONObject(row.json).getJSONArray("hours").getJSONObject(0)
            val apps = hour.getJSONArray("apps")
            (0 until apps.length())
                .map { apps.getJSONObject(it) }
                .filter { it.getString("package") == packageName }
                .sumOf { it.getLong("foreground_ms") }
        }

    @Test
    fun `background listening written by the listener reaches the ingest body`() {
        // The notification listener and the sync worker never meet: the service
        // writes when a media session changes, the worker collects on its own
        // cadence hours later. Nothing joined the two, so a granted phone still
        // shipped background_ms = 0 — observed on a real Fairphone with an
        // audiobook running and notification access switched on.
        listened(noon + 60_000, noon + 660_000)
        val events = listOf(
            RawEvent(noon, EventKind.ScreenOff),
            RawEvent(noon + 900_000, EventKind.ScreenOn),
        )

        collector(events, noon + hour, FakePlaybackReader(granted = true)).collect()

        assertEquals(600_000, backgroundMsFor("com.a"))
    }

    @Test
    fun `a granted phone says the hour was measured`() {
        listened(noon + 60_000, noon + 660_000)
        val events = listOf(RawEvent(noon, EventKind.ScreenOff))

        collector(events, noon + hour, FakePlaybackReader(granted = true)).collect()

        assertTrue("granted means observed", backgroundMeasuredIn(pendingHours()))
    }

    @Test
    fun `without the grant the hour says background was not measured`() {
        // false is "this device could not observe it", never "nothing played".
        // A reader that cannot tell the two apart shows a silent zero.
        val events = listOf(
            RawEvent(noon, EventKind.Resumed("com.a")),
            RawEvent(noon + 600_000, EventKind.Paused("com.a")),
        )

        collector(events, noon + hour, FakePlaybackReader(granted = false)).collect()

        assertFalse(backgroundMeasuredIn(pendingHours()))
    }

    @Test
    fun `playback while the screen is on is foreground, never background`() {
        // The same audiobook, watched rather than listened to. Adding it to
        // background_ms would count the minute twice.
        listened(noon + 60_000, noon + 660_000)
        val events = listOf(
            RawEvent(noon, EventKind.ScreenOn),
            RawEvent(noon + 60_000, EventKind.Resumed("com.a")),
            RawEvent(noon + 660_000, EventKind.Paused("com.a")),
        )

        collector(events, noon + hour, FakePlaybackReader(granted = true)).collect()

        assertEquals(0, backgroundMsFor("com.a"))
        assertEquals(600_000, foregroundMsFor("com.a"))
    }

    private fun pendingHours(): List<org.json.JSONObject> =
        db.queue().pending().map { org.json.JSONObject(it.json).getJSONArray("hours").getJSONObject(0) }

    private fun backgroundMeasuredIn(hours: List<org.json.JSONObject>): Boolean =
        hours.isNotEmpty() && hours.all { it.getBoolean("background_measured") }

    private fun backgroundMsFor(packageName: String): Long =
        pendingHours().sumOf { hour ->
            val apps = hour.getJSONArray("apps")
            (0 until apps.length())
                .map { apps.getJSONObject(it) }
                .filter { it.getString("package") == packageName }
                .sumOf { it.getLong("background_ms") }
        }

    @Test
    fun `a night of listening must not wipe the hours it spans`() {
        // The shape a child asleep with an audiobook makes: listening starts in
        // the evening and closes hours later, long after the two-hour lookback
        // has moved past the hour the phone was actually used in.
        //
        // A stretch is only counted when it CLOSES, and closing it emits every
        // hour it touched. Derived from a window that no longer reaches those
        // hours, they come out with the screen time missing — and because the
        // queue and the server both replace an hour rather than adding to it,
        // the emptier version wins. Screen time and unlocks a parent already
        // saw would disappear from the evening.
        val events = listOf(
            RawEvent(noon, EventKind.ScreenOff),
            RawEvent(noon + 1_800_000, EventKind.ScreenOn),
            RawEvent(noon + 1_800_000, EventKind.Unlock),
            RawEvent(noon + 1_800_000, EventKind.Resumed("com.a")),
            RawEvent(noon + 2_400_000, EventKind.Paused("com.a")),
            RawEvent(noon + 2_400_000, EventKind.ScreenOff),
        )
        listened(noon + 300_000, noon + 4 * hour)
        val granted = FakePlaybackReader(granted = true)

        collector(events, noon + 3_000_000, granted).collect()
        assertEquals("the evening as it was first seen", 600_000, foregroundMsFor("com.a"))
        assertEquals(600_000, screenOnMsFor(noon))

        // Four hours later the stretch closes and every hour it touched is
        // re-derived. `now - 2h` alone starts the window inside hour four.
        collector(events, noon + 4 * hour + 300_000, granted).collect()

        assertEquals("the evening's screen time survived the night", 600_000, foregroundMsFor("com.a"))
        assertEquals(600_000, screenOnMsFor(noon))
        assertEquals(1, unlockCountFor(noon))
        // 25 min before the phone was picked up and 3 h 20 after it was put
        // down, of which 45 min fall in this first hour.
        assertEquals("and the listening was added beside it", 13_500_000, backgroundMsFor("com.a"))
        assertEquals(2_700_000, backgroundMsInHour(noon))
    }

    @Test
    fun `re-deriving an old hour does not invent the empty hours before it`() {
        // The window is widened to a whole hour and one more before it, so that
        // a session crossing into the first re-derived hour is visible. That
        // margin is context, not content: an hour nothing happened in must not
        // arrive as a row of zeroes.
        val events = listOf(RawEvent(noon, EventKind.ScreenOff))
        listened(noon + 300_000, noon + 4 * hour)
        val granted = FakePlaybackReader(granted = true)

        // The first collect leaves only the watermark behind: a stretch still
        // running is not an hour yet.
        collector(events, noon + 1_800_000, granted).collect()
        collector(events, noon + 4 * hour + 300_000, granted).collect()

        val hours = db.queue().pending().map { it.hourStartMillis }
        assertEquals(listOf(noon, noon + hour, noon + 2 * hour, noon + 3 * hour), hours)
    }

    private fun backgroundMsInHour(hourStart: Long): Long =
        hoursStartingAt(hourStart).sumOf { hour ->
            val apps = hour.getJSONArray("apps")
            (0 until apps.length()).sumOf { apps.getJSONObject(it).getLong("background_ms") }
        }

    private fun hoursStartingAt(hourStart: Long): List<org.json.JSONObject> =
        pendingHours().filter { it.getString("hour_start") == java.time.Instant.ofEpochMilli(hourStart).toString() }

    private fun screenOnMsFor(hourStart: Long): Long =
        hoursStartingAt(hourStart).sumOf { it.getLong("screen_on_ms") }

    private fun unlockCountFor(hourStart: Long): Int =
        hoursStartingAt(hourStart).sumOf { it.getInt("unlock_count") }

    @Test
    fun `raw events are kept for debugging and pruned past the window`() {
        val stale = RawEvent(noon - 8 * 24 * hour, EventKind.Unlock)
        val fresh = RawEvent(noon, EventKind.Unlock)
        collector(listOf(stale, fresh), noon + hour).collect()
        assertEquals(1, db.queue().rawCount())
    }
}
