package ch.jorisda.schirmziit.agent.parent

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import java.time.LocalDate

/**
 * What the children list holds. `children == null` is "not asked yet" and draws
 * a skeleton; an empty list is a family with no children yet and draws the empty
 * state with the action in it. The two must not render alike — an empty list
 * under a first load looks like a family that has nothing, which is a lie told
 * by latency.
 */
data class ChildrenState(
    val children: List<ParentChild>? = null,
    val failure: ApiFailure? = null,
    val busy: Boolean = false,
)

/**
 * Folds a load's outcome into what is already on screen.
 *
 * A failure keeps the previous list and adds the error beside it. The list is
 * the screen a parent opens every day, and blanking it because one poll failed
 * is the "lost day" this product promises never to show, one layer up.
 */
fun mergeChildren(
    previous: ChildrenState,
    loaded: Result<List<ParentChild>>,
): ChildrenState = loaded.fold(
    onSuccess = { ChildrenState(children = it, failure = null, busy = false) },
    onFailure = {
        previous.copy(failure = ApiFailure.of(it, "/v1/children"), busy = false)
    },
)

/**
 * One child's screen: a fortnight, and one day out of it.
 *
 * The two halves fail independently on purpose. The strip depends on neither the
 * selected day nor its data, so a day that fails to load must leave the
 * fortnight — selection outline included — exactly where it was.
 */
data class ChildDayState(
    val selected: String,
    val strip: List<DayTotalFfi>? = null,
    val stripFailure: ApiFailure? = null,
    val detail: DayDetailFfi? = null,
    val devices: List<ParentDevice>? = null,
    val dayFailure: ApiFailure? = null,
    /**
     * The day whose request is in flight. Compared against on completion so a
     * slow response for a day the parent has since tapped away from cannot
     * overwrite a faster, newer one — the highlighted day and the numbers under
     * it must always agree. The child's own screen already does this
     * (`MainActivity.pendingDay`); iOS does not, and gets it wrong on a flaky
     * connection.
     */
    val pending: String? = null,
) {
    /** True while the selected day has nothing to draw and nothing to explain. */
    val dayLoading: Boolean get() = detail == null && dayFailure == null
}

/** The fourteen-day window, always anchored to today — never to the tapped day. */
fun stripWindow(today: LocalDate): Pair<String, String> =
    today.minusDays(STRIP_DAYS - 1L).toString() to today.toString()

const val STRIP_DAYS = 14

/**
 * A day was picked. The previous day's numbers must not sit under a new day's
 * heading while the request is in flight: tapping Tuesday and reading Monday's
 * total is a wrong number on screen, not merely a slow one.
 */
fun selectDay(previous: ChildDayState, day: String): ChildDayState = previous.copy(
    selected = day,
    detail = null,
    devices = null,
    dayFailure = null,
    pending = day,
)

/**
 * A refresh of the day already selected. Unlike [selectDay] this keeps what is
 * on screen: it re-fetches the same day, so blanking would reset a loaded day
 * back to skeletons for no reason.
 */
fun refreshDay(previous: ChildDayState): ChildDayState =
    previous.copy(pending = previous.selected)

data class DayLoaded(val detail: DayDetailFfi, val devices: List<ParentDevice>)

/**
 * Folds a day's outcome in, dropping it if the parent has tapped elsewhere since.
 */
fun mergeDay(
    previous: ChildDayState,
    day: String,
    loaded: Result<DayLoaded>,
): ChildDayState {
    if (previous.pending != day) return previous
    return loaded.fold(
        onSuccess = {
            previous.copy(
                detail = it.detail,
                devices = it.devices,
                dayFailure = null,
                pending = null,
            )
        },
        onFailure = {
            // `detail` is deliberately left as it is: on a refresh that leaves
            // numbers on screen, the failure is a banner over them.
            previous.copy(
                dayFailure = ApiFailure.of(it, "/v1/children/usage"),
                pending = null,
            )
        },
    )
}

/**
 * Folds the fortnight in. On failure the strip is left alone and the failure
 * recorded next to it: fourteen zero-filled bars read as a genuinely quiet
 * fortnight, which is exactly the day this app must never lose.
 */
fun mergeStrip(
    previous: ChildDayState,
    loaded: Result<List<DayTotalFfi>>,
): ChildDayState = loaded.fold(
    onSuccess = { previous.copy(strip = it, stripFailure = null) },
    onFailure = {
        previous.copy(stripFailure = ApiFailure.of(it, "/v1/children/usage"))
    },
)

/**
 * A name the server would refuse anyway, refused here first.
 *
 * Returns a failure rather than quietly doing nothing, because a request that
 * was never sent must never read as a child that was created. SZ-E301 is the
 * catalog's "the server could not use that" — which is what an empty name would
 * have got back. The disabled button is the line of defence a parent meets;
 * this is the second one.
 */
fun validateChildName(name: String): String? = name.trim().takeIf { it.isNotEmpty() }
