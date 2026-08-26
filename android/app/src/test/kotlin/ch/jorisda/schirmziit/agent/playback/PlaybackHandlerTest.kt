package ch.jorisda.schirmziit.agent.playback

import ch.jorisda.schirmziit.agent.store.CarryOverRow
import ch.jorisda.schirmziit.agent.store.PendingHourRow
import ch.jorisda.schirmziit.agent.store.PlaybackCarryRow
import ch.jorisda.schirmziit.agent.store.QueueDao
import ch.jorisda.schirmziit.agent.store.RawEventRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeQueueDao : QueueDao {
    val raw = mutableListOf<RawEventRow>()

    override fun appendRaw(rows: List<RawEventRow>) {
        raw += rows
    }

    override fun upsert(rows: List<PendingHourRow>) = Unit
    override fun pending(): List<PendingHourRow> = emptyList()
    override fun delete(hourStarts: List<Long>) = Unit
    override fun pendingCount(): Int = 0
    override fun carryOver(): CarryOverRow? = null
    override fun setCarryOver(row: CarryOverRow) = Unit
    override fun carryOverCount(): Int = 0
    override fun clearCarryOver() = Unit
    override fun pruneRawBefore(millis: Long) = Unit
    override fun rawCount(): Int = raw.size
    override fun playbackCarry(): PlaybackCarryRow? = null
    override fun setPlaybackCarry(row: PlaybackCarryRow) = Unit
    override fun clearPlaybackCarry() = Unit
}

class PlaybackHandlerTest {

    private val playing = listOf(PlaybackState("com.audiobookshelf.app", playing = true))

    @Test
    fun `reconnecting mid playback opens a stretch instead of losing the night`() {
        // The system unbinds and rebinds this service freely. Without re-emitting
        // what is already playing on reconnect, an audiobook that started before
        // the rebind would never be counted at all.
        val dao = FakeQueueDao()
        val handler = PlaybackHandler(dao) { 42L }

        handler.onSnapshot(playing)

        assertEquals(1, dao.raw.size)
        assertEquals(42L, dao.raw.first().atMillis)
        assertTrue(dao.raw.first().json, dao.raw.first().json.contains("PlaybackStarted"))
        assertTrue(dao.raw.first().json.contains("com.audiobookshelf.app"))
    }

    @Test
    fun `an unchanged snapshot writes nothing`() {
        val dao = FakeQueueDao()
        val handler = PlaybackHandler(dao) { 42L }

        handler.onSnapshot(playing)
        handler.onSnapshot(playing)

        assertEquals(1, dao.raw.size)
    }

    @Test
    fun `playback ending is written as a stop`() {
        val dao = FakeQueueDao()
        val handler = PlaybackHandler(dao) { 42L }

        handler.onSnapshot(playing)
        handler.onSnapshot(emptyList())

        assertEquals(2, dao.raw.size)
        assertTrue(dao.raw.last().json.contains("PlaybackStopped"))
    }

    @Test
    fun `a snapshot of nothing at all writes nothing`() {
        // The service is bound long before anything plays; an empty snapshot on
        // connect must not queue a stop for a stretch that never started.
        val dao = FakeQueueDao()
        PlaybackHandler(dao) { 42L }.onSnapshot(emptyList())
        assertTrue(dao.raw.isEmpty())
    }
}
