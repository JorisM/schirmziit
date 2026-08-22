package ch.jorisda.schirmziit.agent.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueueDaoTest {
    private lateinit var db: AgentDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AgentDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.queue()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `re-queuing the same hour replaces it`() {
        dao.upsert(listOf(PendingHourRow(1000L, """{"v":"partial"}""", 100L)))
        dao.upsert(listOf(PendingHourRow(1000L, """{"v":"complete"}""", 200L)))

        val rows = dao.pending()
        assertEquals(1, rows.size)
        assertEquals("""{"v":"complete"}""", rows[0].json)
    }

    @Test
    fun `pending comes back oldest first`() {
        dao.upsert(listOf(PendingHourRow(3000L, "c", 1L), PendingHourRow(1000L, "a", 1L)))
        assertEquals(listOf(1000L, 3000L), dao.pending().map { it.hourStartMillis })
    }

    @Test
    fun `delete removes only the acknowledged hours`() {
        dao.upsert(listOf(PendingHourRow(1000L, "a", 1L), PendingHourRow(2000L, "b", 1L)))
        dao.delete(listOf(1000L))
        assertEquals(listOf(2000L), dao.pending().map { it.hourStartMillis })
    }

    @Test
    fun `carry over is a single row that can be cleared`() {
        assertNull(dao.carryOver())
        dao.setCarryOver(CarryOverRow(packageName = "com.a", sinceMillis = 500L))
        assertEquals("com.a", dao.carryOver()?.packageName)

        dao.setCarryOver(CarryOverRow(packageName = "com.b", sinceMillis = 900L))
        assertEquals("com.b", dao.carryOver()?.packageName)
        assertEquals(1, dao.carryOverCount())

        dao.clearCarryOver()
        assertNull(dao.carryOver())
    }

    @Test
    fun `raw events prune to the retention window`() {
        dao.appendRaw(
            listOf(
                RawEventRow(atMillis = 100L, json = "old"),
                RawEventRow(atMillis = 900L, json = "new"),
            ),
        )
        dao.pruneRawBefore(500L)
        assertEquals(1, dao.rawCount())
    }
}
