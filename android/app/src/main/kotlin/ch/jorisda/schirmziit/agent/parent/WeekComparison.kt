package ch.jorisda.schirmziit.agent.parent

import org.json.JSONObject

/**
 * One app in both weeks. [deltaMs] may be negative: a mover moves in either
 * direction, and the card says which.
 */
data class AppMove(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val previousForegroundMs: Long,
) {
    val deltaMs: Long get() = foregroundMs - previousForegroundMs
}

/**
 * Seven complete days against the seven before them, exactly as the server
 * compared them.
 *
 * Nothing here is computed on the phone. The comparison lives in
 * `crates/core::insight` for the same reason the wire format does: this screen,
 * the dashboard and an iPhone all print one sentence about one week, and three
 * implementations of "which week" would be three chances to print three
 * different numbers out of one database.
 */
data class WeekComparison(
    val from: String,
    val to: String,
    val previousFrom: String,
    val previousTo: String,
    val totalMs: Long,
    val previousTotalMs: Long,
    /**
     * From [eveningFromHour] to local midnight — a subset of [totalMs], never
     * something to add to it.
     */
    val eveningMs: Long,
    val previousEveningMs: Long,
    val eveningFromHour: Int,
    val movers: List<AppMove>,
    /**
     * False when no phone reported the earlier week at all. A week against
     * silence is a first week, not a doubling, and the card has to say so
     * rather than paint a rise of a hundred per cent.
     */
    val previousMeasured: Boolean,
) {
    val deltaMs: Long get() = totalMs - previousTotalMs
    val eveningDeltaMs: Long get() = eveningMs - previousEveningMs
}

/**
 * Strict on purpose: every field is read with `get`, not `opt`.
 *
 * A body missing a total is not a week of no screen time, it is a body this app
 * cannot read — and a card showing "0 min, down 12 h" out of a captcha page is
 * the lost day this product exists not to show. The caller turns null into the
 * same "could not read that" failure every other read uses.
 */
fun weekComparisonFrom(parsed: JSONObject?): WeekComparison? {
    val week = parsed?.optJSONObject("week") ?: return null
    return runCatching {
        val movers = week.getJSONArray("movers")
        WeekComparison(
            from = week.getString("from"),
            to = week.getString("to"),
            previousFrom = week.getString("previous_from"),
            previousTo = week.getString("previous_to"),
            totalMs = week.getLong("total_ms"),
            previousTotalMs = week.getLong("previous_total_ms"),
            eveningMs = week.getLong("evening_ms"),
            previousEveningMs = week.getLong("previous_evening_ms"),
            eveningFromHour = week.getInt("evening_from_hour"),
            movers = (0 until movers.length()).map { index ->
                val mover = movers.getJSONObject(index)
                AppMove(
                    packageName = mover.getString("package"),
                    label = mover.getString("label"),
                    foregroundMs = mover.getLong("foreground_ms"),
                    previousForegroundMs = mover.getLong("previous_foreground_ms"),
                )
            },
            previousMeasured = week.getBoolean("previous_measured"),
        )
    }.getOrNull()
}
