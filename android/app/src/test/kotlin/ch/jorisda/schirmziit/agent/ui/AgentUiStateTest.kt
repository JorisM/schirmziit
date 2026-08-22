package ch.jorisda.schirmziit.agent.ui

import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.power.FakePowerStatus
import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import ch.jorisda.schirmziit.agent.usage.FakeUsageSource
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentUiStateTest {
    private val now = 1_787_313_600_000L

    private fun read(
        permitted: Boolean = true,
        exempt: Boolean = false,
        paired: Boolean = true,
        lastSync: Long = 0L,
        pending: Int = 0,
    ) = AgentUiState.read(
        source = FakeUsageSource(emptyList(), permitted = permitted),
        power = FakePowerStatus(exempt = exempt),
        settings = FakeAgentSettings(
            baseUrl = if (paired) "https://schirmziit.test" else null,
            deviceToken = if (paired) "token" else null,
            lastSyncMillis = lastSync,
        ),
        pendingHours = pending,
        nowMillis = now,
    )

    @Test
    fun `suggests the exemption while the phone is not whitelisted`() {
        assertEquals(BatteryHint.Suggested, read(exempt = false).batteryHint)
    }

    @Test
    fun `granting the exemption clears the hint on the next read`() {
        // The regression: this used to be read once at composition, so the card
        // stayed on screen after the user came back from system settings having
        // granted it.
        val power = FakePowerStatus(exempt = false)
        val settings = FakeAgentSettings(baseUrl = "https://schirmziit.test", deviceToken = "t")
        val source = FakeUsageSource(emptyList())

        val before = AgentUiState.read(source, power, settings, 0, now)
        assertEquals(BatteryHint.Suggested, before.batteryHint)

        power.exempt = true // what the system dialog does while we are paused
        val after = AgentUiState.read(source, power, settings, 0, now)
        assertEquals(BatteryHint.None, after.batteryHint)
    }

    @Test
    fun `granting usage access clears the permission screen on the next read`() {
        val power = FakePowerStatus(exempt = true)
        val settings = FakeAgentSettings(baseUrl = "https://schirmziit.test", deviceToken = "t")

        assertEquals(false, AgentUiState.read(FakeUsageSource(emptyList(), permitted = false), power, settings, 0, now).hasPermission)
        assertEquals(true, AgentUiState.read(FakeUsageSource(emptyList(), permitted = true), power, settings, 0, now).hasPermission)
    }

    @Test
    fun `an unpaired phone is reported as unpaired`() {
        assertEquals(false, read(paired = false).isPaired)
    }

    @Test
    fun `a missed sync makes the battery hint urgent`() {
        assertEquals(BatteryHint.Urgent, read(exempt = false, lastSync = now - 95 * 60_000).batteryHint)
    }

    @Test
    fun `queue depth is carried through untouched`() {
        assertEquals(7, read(pending = 7).pendingHours)
    }
}
