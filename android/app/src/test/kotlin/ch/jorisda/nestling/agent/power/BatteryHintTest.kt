package ch.jorisda.nestling.agent.power

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryHintTest {
    private val now = 1_787_313_600_000L
    private val minute = 60_000L

    @Test
    fun `whitelisted phone needs no hint`() {
        assertEquals(
            BatteryHint.None,
            BatteryHint.evaluate(isIgnoringOptimisations = true, lastSyncMillis = 0L, nowMillis = now),
        )
    }

    @Test
    fun `not whitelisted but syncing fine is only a suggestion`() {
        assertEquals(
            BatteryHint.Suggested,
            BatteryHint.evaluate(false, now - 20 * minute, now),
        )
    }

    @Test
    fun `not whitelisted and syncs are being missed is urgent`() {
        // Three missed 30-minute windows is what the server calls stale.
        assertEquals(
            BatteryHint.Urgent,
            BatteryHint.evaluate(false, now - 95 * minute, now),
        )
    }

    @Test
    fun `a freshly paired phone that has never synced is only a suggestion`() {
        // lastSync = 0 means "no data yet", not "90 minutes late".
        assertEquals(BatteryHint.Suggested, BatteryHint.evaluate(false, 0L, now))
    }

    @Test
    fun `a future last-sync timestamp does not read as stale`() {
        // Clock changes happen; they must not produce a scary banner.
        assertEquals(BatteryHint.Suggested, BatteryHint.evaluate(false, now + 5 * minute, now))
    }
}
