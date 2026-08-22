package ch.jorisda.nestling.agent.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusTextTest {
    private val now = 1_787_313_600_000L
    private val minute = 60_000L
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `never synced is its own bucket, not zero minutes`() {
        assertEquals(StatusText.Bucket.Never, StatusText.bucket(now, 0L))
    }

    @Test
    fun `recent syncs count in minutes`() {
        assertEquals(StatusText.Bucket.Minutes(3), StatusText.bucket(now, now - 3 * minute))
    }

    @Test
    fun `older syncs count in hours`() {
        assertEquals(StatusText.Bucket.Hours(2), StatusText.bucket(now, now - 120 * minute))
    }

    @Test
    fun `a long silence says so instead of rounding it away`() {
        assertEquals(StatusText.Bucket.OverADay, StatusText.bucket(now, now - 30 * 60 * minute))
    }

    @Test
    fun `a future timestamp does not read as a huge age`() {
        // Clock changes happen; "in -5 minutes" must never reach a child's screen.
        assertEquals(StatusText.Bucket.Never, StatusText.bucket(now, now + 5 * minute))
    }

    @Test
    fun `renders through the locale, not through concatenation`() {
        assertEquals("never", StatusText.lastSync(context, now, 0L))
        assertEquals("3 minutes ago", StatusText.lastSync(context, now, now - 3 * minute))
        assertEquals("2 hours ago", StatusText.lastSync(context, now, now - 120 * minute))
    }
}
