package ch.jorisda.nestling.agent.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusTextTest {
    private val now = 1_787_313_600_000L

    @Test
    fun `never synced is stated plainly`() {
        assertEquals("never", StatusText.lastSync(now, 0L))
    }

    @Test
    fun `recent syncs read in minutes`() {
        assertEquals("3 minutes ago", StatusText.lastSync(now, now - 3 * 60_000))
    }

    @Test
    fun `older syncs read in hours`() {
        assertEquals("2 hours ago", StatusText.lastSync(now, now - 2 * 3_600_000))
    }

    @Test
    fun `a stale agent says so instead of rounding it away`() {
        assertEquals("over a day ago", StatusText.lastSync(now, now - 30 * 3_600_000))
    }
}
