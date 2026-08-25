package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.AppTotalFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSplitTest {
    private fun app(label: String, ms: Long) = AppTotalFfi(label, label, ms)

    @Test
    fun `separates the apps under a minute from the ones above it`() {
        val split = splitApps(listOf(app("A", 3_600_000), app("B", 45_000), app("C", 60_000)))
        assertEquals(listOf("A", "C"), split.shown.map { it.label })
        assertEquals(listOf("B"), split.brief.map { it.label })
    }

    @Test
    fun `drops an app that rounds to zero seconds`() {
        // A row reading "0 s" carries nothing; it is the one thing worth hiding.
        val split = splitApps(listOf(app("A", 3_600_000), app("Blink", 300)))
        assertEquals(listOf("A"), split.shown.map { it.label })
        assertTrue(split.brief.isEmpty())
    }

    @Test
    fun `keeps an app that rounds to one second`() {
        val split = splitApps(listOf(app("Blink", 900)))
        assertEquals(listOf("Blink"), split.brief.map { it.label })
    }

    @Test
    fun `a day of nothing but glances still shows them all, folded`() {
        val split = splitApps(listOf(app("A", 30_000), app("B", 20_000), app("C", 10_000)))
        assertTrue(split.shown.isEmpty())
        assertEquals(3, split.brief.size)
    }
}
