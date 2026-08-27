package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.DayTotalFfi
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules around deleting a child's stored figures, asserted without a server.
 *
 * This is the one irreversible write in the parent app that answers with
 * numbers, and both halves of that matter: a failure must not read as a
 * deletion, and a deletion must not leave the deleted figures on screen.
 */
class PurgeStateTest {

    private val purged = Purged(usageHours = 42L, deviceHours = 17L, usageDays = 3L)

    @Test
    fun `a purge that failed leaves the question open and shows no counts`() {
        // A confirmation that closes on failure reads as "done" — here that
        // means telling a parent the figures are gone while every row is still
        // there, and taking away the button that would try again.
        val asking = PurgeState(asking = true, busy = true)

        val after = mergePurge(asking, Result.failure(IOException("no route to host")))

        assertTrue("the question stays open so the parent can try again", after.asking)
        assertNotNull(after.failure)
        assertNull("a failure never sits next to a count of what went", after.purged)
        assertFalse(after.busy)
    }

    @Test
    fun `a purge that matched nothing still says so, with zeros`() {
        // Zero is the honest answer for a child whose phone has not reported
        // yet. Hiding the counts because they are zero would leave the parent
        // unable to tell a purge that worked from one that found nothing.
        val after = mergePurge(
            PurgeState(asking = true, busy = true),
            Result.success(Purged(0L, 0L, 0L)),
        )

        assertNotNull(after.purged)
        assertEquals(0L, after.purged?.usageHours)
        assertNull(after.failure)
        assertFalse("the question is answered", after.asking)
    }

    @Test
    fun `a purge that worked carries the server's own counts`() {
        val after = mergePurge(PurgeState(asking = true, busy = true), Result.success(purged))

        assertEquals(42L, after.purged?.usageHours)
        assertEquals(17L, after.purged?.deviceHours)
        assertEquals(3L, after.purged?.usageDays)
        assertFalse(after.busy)
    }

    @Test
    fun `a purge that worked clears a previous failure`() {
        val failed = mergePurge(PurgeState(asking = true), Result.failure(IOException("nope")))

        val after = mergePurge(failed.copy(busy = true), Result.success(purged))

        assertNull("figures that went are not an error state", after.failure)
        assertNotNull(after.purged)
    }

    @Test
    fun `the deleted figures do not stay on screen`() {
        // The one place this app blanks loaded data on purpose. Everywhere else
        // that would be a lost day; here the bars describe rows the server has
        // just deleted, and leaving them up says the purge did not work.
        val loaded = ChildDayState(
            selected = "2026-08-20",
            strip = listOf(DayTotalFfi("2026-08-20", 5_400_000L, 0L)),
            devices = listOf(ParentDevice("d1", "Fairphone", null, stale = false)),
        )

        val after = purgedDay(loaded)

        assertNull(after.strip)
        assertNull(after.detail)
        assertNull(after.devices)
        assertEquals("the parent is still looking at the same day", "2026-08-20", after.selected)
        assertEquals("and both halves are being re-read", "2026-08-20", after.pending)
    }
}
