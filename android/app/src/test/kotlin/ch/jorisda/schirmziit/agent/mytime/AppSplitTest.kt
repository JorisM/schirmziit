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

    @Test
    fun `folded apps survive the cap that ranked rows do not`() {
        // The central claim of the fold: a brief app is already folded and
        // must never be the thing an eight-row cap crowds out. Nine ranked
        // apps (one more than the cap) plus two glances proves the cap only
        // ever eats into shown — capping shown+brief together would instead
        // lose a brief app.
        val ranked = (1..9).map { app("Ranked$it", 60_000L + it) }
        val glances = listOf(app("Brief1", 30_000), app("Brief2", 20_000))
        val visible = visibleApps(splitApps(ranked + glances), cap = 8)
        assertEquals(8, visible.shown.size)
        assertEquals(listOf("Brief1", "Brief2"), visible.brief.map { it.label })
    }
}
