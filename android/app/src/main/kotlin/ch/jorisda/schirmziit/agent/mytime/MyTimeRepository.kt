package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi

data class MyTime(
    val days: List<DayTotalFfi>,
    val detail: DayDetailFfi?,
    val selected: String,
    /// True when anything went wrong — never drawn as zeros: "you used
    /// nothing today" is the wrong thing to tell a child because the wifi was
    /// off. `mergeMyTimeResult` is what turns this into what the screen shows:
    /// the numbers already on screen, plus an error line.
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
    // `from` is nullable rather than a `= minus13(selected)` default: a Kotlin
    // default-parameter expression evaluates in the prologue, before this
    // method's try/catch runs, so a malformed `selected` would throw past the
    // guarantee this repository exists to give — resolve it inside the try
    // instead, where every parse failure lands on `failed = true`.
    //
    // `days`: the strip already on screen, if the caller has one. Picking a
    // day is the one request that tap is allowed to cost — a child's phone is
    // the surface of the three (web, iOS parent, iOS child) most likely to be
    // metered, and re-fetching thirteen days nobody asked about is the
    // expensive half of the screen doing the least work.
    fun load(
        selected: String,
        from: String? = null,
        days: List<DayTotalFfi>? = null,
        tz: String = "UTC",
    ): MyTime = try {
        val resolvedDays = days ?: parseStrip(fetch(from ?: minus13(selected), selected, "day", tz))
        val detail = parseDetail(fetch(selected, selected, "hour", tz))
        MyTime(resolvedDays, detail, selected, failed = false)
    } catch (error: Exception) {
        MyTime(emptyList(), null, selected, failed = true)
    }

    private companion object {
        fun minus13(day: String): String =
            java.time.LocalDate.parse(day).minusDays(13).toString()
    }
}
