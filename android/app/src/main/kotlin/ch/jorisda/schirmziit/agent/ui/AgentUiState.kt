package ch.jorisda.schirmziit.agent.ui

import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.power.PowerStatus
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.usage.UsageSource

/**
 * Everything the screens need, read in one go.
 *
 * It exists as a value rather than a set of `remember`ed booleans because both
 * the usage permission and the battery exemption are granted in *system*
 * settings: the app is paused while it happens and must re-read on resume. Read
 * once at composition and the card telling you to grant something stays on
 * screen after you granted it.
 */
data class AgentUiState(
    val hasPermission: Boolean,
    val isPaired: Boolean,
    val batteryHint: BatteryHint,
    val pendingHours: Int,
) {
    companion object {
        fun read(
            source: UsageSource,
            power: PowerStatus,
            settings: AgentSettings,
            pendingHours: Int,
            nowMillis: Long,
        ): AgentUiState = AgentUiState(
            hasPermission = source.hasPermission(),
            isPaired = settings.isPaired,
            batteryHint = BatteryHint.evaluate(
                isIgnoringOptimisations = power.isIgnoringOptimisations(),
                lastSyncMillis = settings.lastSyncMillis,
                nowMillis = nowMillis,
            ),
            pendingHours = pendingHours,
        )
    }
}
