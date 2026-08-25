package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayTotalFfi
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyTimeLoadArgsTest {

    /// The finding this test exists for: the window used to be anchored to
    /// whichever day was tapped, so it slid on every tap and repeatedly
    /// tapping the leftmost bar walked it backwards without limit. `selected`
    /// is not even a parameter here any more — the window must not be able to
    /// depend on it.
    @Test
    fun `the window is always today minus thirteen days, regardless of which day is picked`() {
        val today = LocalDate.of(2026, 8, 24)

        val args = myTimeLoadArgs(today, previousDays = null)

        assertEquals("2026-08-11", args.from)
    }

    @Test
    fun `the window moves with today, not with any particular selection`() {
        val args = myTimeLoadArgs(LocalDate.of(2026, 9, 3), previousDays = null)

        assertEquals("2026-08-21", args.from)
    }

    @Test
    fun `nothing loaded yet asks the repository to fetch the strip`() {
        val args = myTimeLoadArgs(LocalDate.of(2026, 8, 24), previousDays = null)

        assertNull("null must fetch, not reuse an empty answer that was never asked for", args.days)
    }

    /// The other half of the finding: a tap on a day the strip already covers
    /// must reuse it rather than re-fetching, so picking a day costs one
    /// request instead of two.
    @Test
    fun `a strip already on screen is reused, not asked for again`() {
        val strip = listOf(DayTotalFfi("2026-08-20", 60_000L))

        val args = myTimeLoadArgs(LocalDate.of(2026, 8, 24), previousDays = strip)

        assertEquals(strip, args.days)
    }

    @Test
    fun `a genuinely quiet fortnight is still reused, not mistaken for nothing loaded yet`() {
        val args = myTimeLoadArgs(LocalDate.of(2026, 8, 24), previousDays = emptyList())

        assertEquals(emptyList<DayTotalFfi>(), args.days)
    }
}
