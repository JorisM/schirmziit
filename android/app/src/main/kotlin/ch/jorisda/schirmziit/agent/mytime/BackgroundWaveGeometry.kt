package ch.jorisda.schirmziit.agent.mytime

/**
 * One hour of background listening fills the lane. Fixed, not day-relative, and
 * the same constant the web dashboard uses: a scale that adapted to each day
 * would draw ten quiet minutes exactly like a full hour, and comparing one day
 * to the next is the point of the lane.
 */
const val BACKGROUND_FULL_SCALE_MS = 3_600_000L

/** How much of the lane's height one hour's listening fills, 0f..1f. */
fun backgroundShare(ms: Long): Float =
    (ms.coerceIn(0L, BACKGROUND_FULL_SCALE_MS).toFloat() / BACKGROUND_FULL_SCALE_MS)
