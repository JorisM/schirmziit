package ch.jorisda.schirmziit.agent.playback

import ch.jorisda.schirmziit.agent.store.CarryOverRow
import ch.jorisda.schirmziit.agent.store.PendingHourRow
import ch.jorisda.schirmziit.agent.store.PlaybackCarryRow
import ch.jorisda.schirmziit.agent.store.PlaybackEventRow
import ch.jorisda.schirmziit.agent.store.QueueDao
import ch.jorisda.schirmziit.agent.store.RawEventRow
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs nothing until told to, so a test can see what was dispatched and what was not. */
private class HeldExecutor : Executor {
    private val queued = mutableListOf<Runnable>()

    override fun execute(command: Runnable) {
        queued += command
    }

    fun runAll() {
        queued.toList().also { queued.clear() }.forEach { it.run() }
    }
}

private class RecordingDao : QueueDao {
    val playback = mutableListOf<PlaybackEventRow>()

    override fun appendPlayback(rows: List<PlaybackEventRow>) {
        playback += rows
    }

    override fun playbackEvents(fromMillis: Long, toMillis: Long): List<PlaybackEventRow> =
        playback.filter { it.atMillis in fromMillis..toMillis }.sortedBy { it.atMillis }

    override fun playbackBefore(millis: Long): PlaybackEventRow? =
        playback.filter { it.atMillis < millis }.maxByOrNull { it.atMillis }

    override fun prunePlaybackBefore(millis: Long) = Unit
    override fun playbackEventCount(): Int = playback.size
    override fun appendRaw(rows: List<RawEventRow>) = Unit
    override fun upsert(rows: List<PendingHourRow>) = Unit
    override fun pending(): List<PendingHourRow> = emptyList()
    override fun delete(hourStarts: List<Long>) = Unit
    override fun pendingCount(): Int = 0
    override fun carryOver(): CarryOverRow? = null
    override fun setCarryOver(row: CarryOverRow) = Unit
    override fun carryOverCount(): Int = 0
    override fun clearCarryOver() = Unit
    override fun pruneRawBefore(millis: Long) = Unit
    override fun rawCount(): Int = 0
    override fun playbackCarry(): PlaybackCarryRow? = null
    override fun setPlaybackCarry(row: PlaybackCarryRow) = Unit
    override fun clearPlaybackCarry() = Unit
}

class PlaybackWatcherTest {

    private val book = "com.audiobookshelf.app"

    /** Writes land before the assertion, which a real single-thread executor cannot promise. */
    private val inline = Executor { it.run() }

    @Test
    fun `a session that starts playing is recorded, though the session list never changed`() {
        // What actually happens on a phone: the app is opened, its media session
        // is created paused, and only later does a child press play. Watching the
        // session LIST alone never fires for that press — the list is unchanged —
        // so an audiobook playing all evening was recorded as nothing at all.
        val dao = RecordingDao()
        val reader = FakePlaybackReader(granted = true)
        var at = 1_000L
        PlaybackWatcher(reader, PlaybackHandler(dao, { at }, inline)).start()

        assertTrue("a paused session is not listening", dao.playback.isEmpty())

        at = 2_000L
        reader.emit(listOf(PlaybackState(book, playing = true)))

        assertEquals(1, dao.playback.size)
        assertEquals(2_000L, dao.playback.first().atMillis)
        assertEquals(book, dao.playback.first().packageName)
        assertTrue(dao.playback.first().started)
    }

    @Test
    fun `whatever is already playing when the watch starts opens a stretch`() {
        // The system binds and unbinds this service freely. A rebind in the middle
        // of a night of listening has to re-open the stretch.
        val dao = RecordingDao()
        val reader = FakePlaybackReader(granted = true, active = listOf(PlaybackState(book, playing = true)))

        PlaybackWatcher(reader, PlaybackHandler(dao, { 1_000L }, inline)).start()

        assertEquals(1, dao.playback.size)
        assertTrue(dao.playback.first().started)
    }

    @Test
    fun `pausing closes the stretch`() {
        val dao = RecordingDao()
        val reader = FakePlaybackReader(granted = true)
        var at = 1_000L
        PlaybackWatcher(reader, PlaybackHandler(dao, { at }, inline)).start()

        at = 2_000L
        reader.emit(listOf(PlaybackState(book, playing = true)))
        at = 3_000L
        reader.emit(listOf(PlaybackState(book, playing = false)))

        assertEquals(2, dao.playback.size)
        assertEquals(false, dao.playback.last().started)
        assertEquals(3_000L, dao.playback.last().atMillis)
    }

    @Test
    fun `the database write is dispatched off the calling thread`() {
        // NotificationListenerService delivers on the main looper, where Room
        // throws rather than writes. The service died on the first thing a child
        // played, and the system backed its restart off to half an hour — so a
        // phone that had listened once recorded nothing for the rest of the day.
        val dao = RecordingDao()
        val reader = FakePlaybackReader(granted = true)
        val writes = HeldExecutor()
        PlaybackWatcher(reader, PlaybackHandler(dao, { 1_000L }, writes)).start()

        reader.emit(listOf(PlaybackState(book, playing = true)))

        assertTrue("the insert must not run on the caller's thread", dao.playback.isEmpty())
        writes.runAll()
        assertEquals(1, dao.playback.size)
    }

    @Test
    fun `stopping the watch releases it`() {
        val dao = RecordingDao()
        val reader = FakePlaybackReader(granted = true)
        val watcher = PlaybackWatcher(reader, PlaybackHandler(dao, { 1_000L }, inline))

        watcher.start()
        watcher.stop()

        assertTrue("the reader must be released, or the callback outlives the service", reader.watching.not())
    }
}
