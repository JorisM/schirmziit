package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTimeUiStateTest {

    private val goodStrip = listOf(DayTotalFfi("2026-08-20", 60_000L))
    private val goodDetail = DayDetailFfi(60_000L, 3, List(24) { 0L }, emptyList())
    private val good = MyTime(goodStrip, goodDetail, "2026-08-20", failed = false)
    private val failure = MyTime(emptyList(), null, "2026-08-21", failed = true)

    @Test
    fun `a successful load with nothing on screen yet shows its own numbers`() {
        val state = mergeMyTimeResult(previous = null, result = good)

        assertEquals(good, state.myTime)
        assertFalse(state.error)
    }

    /// The finding this test exists for: Android used to wipe the whole screen
    /// on a failed load, unlike iOS, which keeps the previous numbers on screen
    /// and adds only an error line. A child who taps a bar on a flaky
    /// connection should not have the screen emptied.
    @Test
    fun `a failed load after a successful one keeps the previous days and detail`() {
        val state = mergeMyTimeResult(previous = good, result = failure)

        assertEquals("the previous numbers must stay on screen", good, state.myTime)
        assertTrue("the error must be raised separately from the data", state.error)
    }

    @Test
    fun `a successful retry clears the error and shows its own numbers`() {
        val recovered = good.copy(selected = "2026-08-21")
        val state = mergeMyTimeResult(previous = good, result = recovered)

        assertEquals(recovered, state.myTime)
        assertFalse(state.error)
    }

    @Test
    fun `a failed first load shows no data and the error, not a crash`() {
        val state = mergeMyTimeResult(previous = null, result = failure)

        assertEquals(null, state.myTime)
        assertTrue(state.error)
    }
}
