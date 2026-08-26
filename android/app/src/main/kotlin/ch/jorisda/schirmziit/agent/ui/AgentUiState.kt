package ch.jorisda.schirmziit.agent.ui

import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.power.PowerStatus
import ch.jorisda.schirmziit.agent.playback.PlaybackReader
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
    /**
     * Whether background listening is being counted on this phone. Read on
     * every resume for the same reason as [hasPermission]: it is granted in
     * system settings, with the app paused.
     */
    val backgroundGranted: Boolean,
    val backgroundCardDismissed: Boolean,
) {
    companion object {
        fun read(
            source: UsageSource,
            power: PowerStatus,
            settings: AgentSettings,
            pendingHours: Int,
            nowMillis: Long,
            /**
             * Required, deliberately. It used to default to null, and
             * MainActivity simply never passed one — so [backgroundGranted] was
             * false on every phone forever, the grant card never cleared, and
             * granting notification access looked like it had done nothing.
             * A default that silently means "not granted" cannot be forgotten
             * loudly; the compiler catching it is worth more than any test.
             */
            playback: PlaybackReader,
        ): AgentUiState = AgentUiState(
            hasPermission = source.hasPermission(),
            isPaired = settings.isPaired,
            batteryHint = BatteryHint.evaluate(
                isIgnoringOptimisations = power.isIgnoringOptimisations(),
                lastSyncMillis = settings.lastSyncMillis,
                nowMillis = nowMillis,
            ),
            pendingHours = pendingHours,
            backgroundGranted = playback.hasPermission(),
            backgroundCardDismissed = settings.backgroundCardDismissed,
        )
    }
}
