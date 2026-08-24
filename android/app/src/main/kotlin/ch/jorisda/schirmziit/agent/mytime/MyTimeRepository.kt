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
    // `from` is nullable rather than a `= minus13(selected)` default: a Kotlin
    // default-parameter expression evaluates in the prologue, before this
    // method's try/catch runs, so a malformed `selected` would throw past the
    // guarantee this repository exists to give — resolve it inside the try
    // instead, where every parse failure lands on `failed = true`.
    fun load(selected: String, from: String? = null, tz: String = "UTC"): MyTime = try {
        val resolvedFrom = from ?: minus13(selected)
        val days = parseStrip(fetch(resolvedFrom, selected, "day", tz))
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
