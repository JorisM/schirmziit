package ch.jorisda.schirmziit.agent.parent

/**
 * What the delete-my-child's-figures card holds.
 *
 * `asking` is the question, not a dialog: deleting a child's stored figures is
 * irreversible and the control sits under numbers a parent came to read, so the
 * first press only asks. `purged` is what the server said it removed —
 * "deleted" with nothing behind it is exactly the claim a family has no way to
 * check.
 */
data class PurgeState(
    val purged: Purged? = null,
    val failure: ApiFailure? = null,
    val busy: Boolean = false,
    val asking: Boolean = false,
)

/**
 * Folds a purge's outcome in.
 *
 * A failure leaves the question open. A confirmation that closes on failure
 * reads as "done", which here means telling a parent their child's figures are
 * gone while every row is still there — and it takes away the button that would
 * try again.
 *
 * A failure also carries no counts: the two must never be on screen together.
 */
fun mergePurge(
    previous: PurgeState,
    purged: Result<Purged>,
): PurgeState = purged.fold(
    onSuccess = { PurgeState(purged = it, failure = null, busy = false, asking = false) },
    onFailure = {
        previous.copy(
            purged = null,
            failure = ApiFailure.of(it, "/v1/children/data"),
            busy = false,
            asking = true,
        )
    },
)

/**
 * The child's screen once the figures have gone.
 *
 * The one place in this app where keeping what is already on screen is the
 * wrong move. Everywhere else a failed refresh leaves the loaded numbers up,
 * because blanking them loses a day at the presentation layer — but these bars
 * describe rows the server has just deleted, and leaving them there tells a
 * parent the purge did not work. Both halves go back to their skeletons and are
 * re-read.
 */
fun purgedDay(previous: ChildDayState): ChildDayState = ChildDayState(
    selected = previous.selected,
    pending = previous.selected,
)
