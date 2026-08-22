package ch.jorisda.nestling.agent.power

/**
 * Whether to nudge the user about Android's battery optimisation.
 *
 * Deliberately graded rather than a permanent banner: on a phone where
 * WorkManager runs happily, being outside the whitelist changes nothing and a
 * standing warning trains people to ignore warnings. It escalates only once
 * syncs are actually being missed.
 */
enum class BatteryHint {
    None,
    Suggested,
    Urgent,
    ;

    companion object {
        /** Three missed 30-minute windows — the same threshold the server calls stale. */
        private const val STALE_AFTER_MINUTES = 90L

        fun evaluate(
            isIgnoringOptimisations: Boolean,
            lastSyncMillis: Long,
            nowMillis: Long,
        ): BatteryHint {
            if (isIgnoringOptimisations) return None
            if (lastSyncMillis <= 0L) return Suggested

            val ageMinutes = (nowMillis - lastSyncMillis) / 60_000
            return if (ageMinutes > STALE_AFTER_MINUTES) Urgent else Suggested
        }
    }
}
