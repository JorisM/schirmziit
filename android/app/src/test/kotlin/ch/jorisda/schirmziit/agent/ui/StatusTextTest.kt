package ch.jorisda.schirmziit.agent.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun `one is not written as a plural`() {
        // "1 hours ago" and "vor 1 Stunden" both shipped, on the one screen a
        // child is invited to open and read about themselves. A count that can
        // be one needs a plural form, not an %1$d in a sentence written for
        // many, and not an "(n)" bolted onto the end either.
        assertEquals("1 minute ago", StatusText.lastSync(context, now, now - minute))
        assertEquals("1 hour ago", StatusText.lastSync(context, now, now - 60 * minute))
    }

    @Test
    @Config(qualifiers = "de-rCH")
    fun `german says Stunde for one and Stunden for more`() {
        assertEquals("vor 1 Minute", StatusText.lastSync(context, now, now - minute))
        assertEquals("vor 5 Minuten", StatusText.lastSync(context, now, now - 5 * minute))
        assertEquals("vor 1 Stunde", StatusText.lastSync(context, now, now - 60 * minute))
        assertEquals("vor 3 Stunden", StatusText.lastSync(context, now, now - 180 * minute))
    }

    @Test
    @Config(qualifiers = "de-rCH")
    fun `the queued-hours count is a plural too, not Stunde-in-brackets-n`() {
        assertEquals("1 Stunde", StatusText.pendingHours(context, 1))
        assertEquals("2 Stunden", StatusText.pendingHours(context, 2))
    }

    @Test
    fun `durations under an hour show only minutes`() {
        assertEquals("18 min", StatusText.duration(18 * minute))
    }

    @Test
    fun `an exact number of hours drops the minutes`() {
        assertEquals("1 h", StatusText.duration(60 * minute))
    }

    @Test
    fun `hours and minutes both show, never a decimal hour`() {
        assertEquals("2 h 14 min", StatusText.duration(134 * minute))
    }

    @Test
    fun `zero is a duration too, not blank`() {
        assertEquals("0 min", StatusText.duration(0L))
    }

    @Test
    fun `a sub-minute remainder rounds rather than truncating to zero`() {
        // 15 seconds short of 3 minutes: truncation would read "2 min", which
        // under-reports every single value by up to 59 seconds.
        assertEquals("3 min", StatusText.duration(2 * minute + 45_000))
    }

    @Test
    fun `renders seconds below a minute`() {
        assertEquals("20 s", StatusText.duration(20_000))
        assertEquals("45 s", StatusText.duration(45_400))
    }

    @Test
    fun `keeps zero as zero minutes`() {
        assertEquals("0 min", StatusText.duration(0))
    }

    @Test
    fun `never renders sixty seconds`() {
        assertEquals("1 min", StatusText.duration(59_500))
        assertEquals("1 min", StatusText.duration(60_000))
    }

    @Test
    fun `leaves longer spans alone`() {
        assertEquals("2 min", StatusText.duration(90_000))
        assertEquals("1 h", StatusText.duration(3_600_000))
        assertEquals("2 h 14 min", StatusText.duration(8_040_000))
    }
}
