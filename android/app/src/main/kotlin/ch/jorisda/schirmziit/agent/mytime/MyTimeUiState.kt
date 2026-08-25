package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayTotalFfi
import java.time.LocalDate

/**
 * What `MainActivity` should call `MyTimeRepository.load` with for a tap on
 * any given day. `today`, never `selected`, anchors the window — the earlier
 * bug anchored it to whichever day was picked, so the visible fortnight slid
 * with every tap and repeatedly tapping the leftmost bar walked it backwards
 * without limit. `previousDays` is passed straight through as `days`: a
 * `null` (nothing loaded yet) fetches the strip, anything else — including an
 * empty list, a genuinely quiet fortnight — reuses it instead of re-fetching.
 */
fun myTimeLoadArgs(today: LocalDate, previousDays: List<DayTotalFfi>?): MyTimeLoadArgs =
    MyTimeLoadArgs(from = today.minusDays(13).toString(), days = previousDays)

data class MyTimeLoadArgs(val from: String, val days: List<DayTotalFfi>?)

/**
 * What the screen should hold after a load finishes: the numbers to show, and
 * whether the load itself failed. Kept apart from `MyTime` because a failure
 * must never wipe numbers a previous load already put on screen — iOS keeps
 * the child's previous numbers and adds an error line beside them, and a
 * dropped connection should read the same way here.
 */
data class MyTimeUiState(val myTime: MyTime?, val error: Boolean)

/**
 * Combines a load's result with whatever was already on screen. A failed
 * result carries no usable data (`MyTimeRepository` always empties `days` and
 * `detail` on failure) — adopting it wholesale is exactly the bug this guards
 * against: a slow retry on a flaky connection would wipe a screen a child was
 * already reading.
 */
fun mergeMyTimeResult(previous: MyTime?, result: MyTime): MyTimeUiState =
    if (result.failed) MyTimeUiState(myTime = previous, error = true)
    else MyTimeUiState(myTime = result, error = false)
