package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.AppTotalFfi
import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import ch.jorisda.schirmziit.core.ErrorCode
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presentation-layer half of "nothing that can lose a day".
 *
 * The queue guards the wire; these guard the screen. A refresh that fails must
 * not blank numbers a parent is already reading, and a zero must never stand in
 * for a number nobody fetched.
 */
class ParentUiStateTest {

    private fun failure() = Result.failure<Nothing>(IOException("no route to host"))

    private val alice = ParentChild("c1", "Alice", 5_400_000L)
    private val bruno = ParentChild("c2", "Bruno", 0L)

    // ─── the children list ───────────────────────────────────────────────

    @Test
    fun `a first load draws children`() {
        val state = mergeChildren(ChildrenState(), Result.success(listOf(alice, bruno)))

        assertEquals(listOf(alice, bruno), state.children)
        assertNull(state.failure)
        assertFalse(state.busy)
    }

    @Test
    fun `nothing loaded yet is not the same as a family with no children`() {
        // The distinction the screen renders as skeleton-versus-empty-state. A
        // single nullable is the only way to hold it; a plain empty list cannot.
        assertNull(ChildrenState().children)
        assertEquals(emptyList<ParentChild>(), mergeChildren(ChildrenState(), Result.success(emptyList())).children)
    }

    @Test
    fun `a failed refresh keeps the children already on screen`() {
        val loaded = mergeChildren(ChildrenState(), Result.success(listOf(alice, bruno)))

        val after = mergeChildren(loaded, failure())

        assertEquals("the list must survive a failed poll", listOf(alice, bruno), after.children)
        assertNotNull(after.failure)
        assertEquals(ErrorCode.SERVER_UNREACHABLE, after.failure?.code)
    }

    @Test
    fun `a failed first load has an error and still no list`() {
        // Not an empty list: that would render the empty state, inviting a
        // parent to add a child they already have.
        val state = mergeChildren(ChildrenState(), failure())

        assertNull(state.children)
        assertNotNull(state.failure)
    }

    // ─── one child's day ─────────────────────────────────────────────────

    private fun detail(totalMs: Long = 6_300_000L) = DayDetailFfi(
        totalMs = totalMs,
        unlockCount = 42,
        hours = List(24) { 0L },
        apps = listOf(AppTotalFfi("ch.a", "A", totalMs, 0L)),
        backgroundMs = 0L,
        backgroundHours = List(24) { 0L },
        backgroundMeasured = false,
    )

    private fun strip() = (7..20).map { day ->
        DayTotalFfi("2026-08-%02d".format(day), 3_600_000L, 0L)
    }

    @Test
    fun `the fortnight is anchored to today, never to the day tapped`() {
        // The bug this pins shipped on the child's screen once: the window was
        // anchored to whichever day was picked, so it slid on every tap and
        // repeatedly tapping the leftmost bar walked backwards without limit.
        val (from, to) = stripWindow(LocalDate.parse("2026-08-20"))

        assertEquals("2026-08-07", from)
        assertEquals("2026-08-20", to)
        assertEquals(STRIP_DAYS, 14)
    }

    @Test
    fun `picking a day clears the previous day's numbers`() {
        val loaded = mergeDay(
            selectDay(ChildDayState("2026-08-20"), "2026-08-20"),
            "2026-08-20",
            Result.success(DayLoaded(detail(), emptyList())),
        )
        assertNotNull(loaded.detail)

        val switching = selectDay(loaded, "2026-08-19")

        // Monday's total under Tuesday's heading is a wrong number on screen,
        // not merely a slow one.
        assertNull(switching.detail)
        assertNull(switching.devices)
        assertTrue(switching.dayLoading)
        assertEquals("2026-08-19", switching.pending)
    }

    @Test
    fun `a refresh of the same day keeps its numbers while it reloads`() {
        val loaded = mergeDay(
            selectDay(ChildDayState("2026-08-20"), "2026-08-20"),
            "2026-08-20",
            Result.success(DayLoaded(detail(), emptyList())),
        )

        val refreshing = refreshDay(loaded)

        assertNotNull("pull-to-refresh must not blank a loaded day", refreshing.detail)
        assertEquals("2026-08-20", refreshing.pending)
    }

    @Test
    fun `a stale response for a day tapped away from is dropped`() {
        var state = selectDay(ChildDayState("2026-08-20"), "2026-08-19")
        // The parent taps on before the first answer lands.
        state = selectDay(state, "2026-08-18")

        val late = mergeDay(state, "2026-08-19", Result.success(DayLoaded(detail(999L), emptyList())))

        assertEquals("2026-08-18", late.selected)
        assertNull("the highlighted day and the numbers must agree", late.detail)
        assertEquals("2026-08-18", late.pending)
    }

    @Test
    fun `a failed day keeps whatever the strip already had`() {
        var state = mergeStrip(ChildDayState("2026-08-20"), Result.success(strip()))
        state = selectDay(state, "2026-08-19")

        val after = mergeDay(state, "2026-08-19", failure())

        assertEquals("the fortnight is independent of the day", 14, after.strip?.size)
        assertNull(after.stripFailure)
        assertNotNull(after.dayFailure)
    }

    @Test
    fun `a failed strip refresh leaves the fortnight on screen`() {
        val loaded = mergeStrip(ChildDayState("2026-08-20"), Result.success(strip()))

        val after = mergeStrip(loaded, failure())

        // Fourteen zero-filled bars would read as a genuinely quiet fortnight.
        assertEquals(14, after.strip?.size)
        assertTrue(after.strip!!.all { it.foregroundMs > 0 })
        assertNotNull(after.stripFailure)
    }

    @Test
    fun `a failed first strip has no zero-filled stand-in`() {
        val state = mergeStrip(ChildDayState("2026-08-20"), failure())

        assertNull(state.strip)
        assertNotNull(state.stripFailure)
    }

    // ─── adding a child ──────────────────────────────────────────────────

    @Test
    fun `a blank child name is refused before it reaches the server`() {
        assertNull(validateChildName(""))
        assertNull(validateChildName("   "))
        assertEquals("Alice", validateChildName("  Alice  "))
    }
}
