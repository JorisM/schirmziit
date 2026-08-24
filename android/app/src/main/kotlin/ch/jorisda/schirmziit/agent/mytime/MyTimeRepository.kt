package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi

data class MyTime(
    val days: List<DayTotalFfi>,
    val detail: DayDetailFfi?,
    val selected: String,
    /// True when anything went wrong. The screen says so rather than drawing
    /// zeros: "you used nothing today" is the wrong thing to tell a child
    /// because the wifi was off.
    val failed: Boolean,
)

/**
 * Constructor-injected seams rather than concrete types: the JVM tests must not
 * need a network or the native core to prove that a failed load is reported as
 * a failure.
 */
class MyTimeRepository(
    private val fetch: (from: String, to: String, bucket: String, tz: String) -> String,
    private val parseStrip: (String) -> List<DayTotalFfi>,
    private val parseDetail: (String) -> DayDetailFfi,
) {
    fun load(selected: String, from: String = minus13(selected), tz: String = "UTC"): MyTime = try {
        val days = parseStrip(fetch(from, selected, "day", tz))
        val detail = parseDetail(fetch(selected, selected, "hour", tz))
        MyTime(days, detail, selected, failed = false)
    } catch (error: Exception) {
        MyTime(emptyList(), null, selected, failed = true)
    }

    private companion object {
        fun minus13(day: String): String =
            java.time.LocalDate.parse(day).minusDays(13).toString()
    }
}
