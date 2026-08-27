package ch.jorisda.schirmziit.agent.pair

/**
 * How many characters the server's pairing codes have.
 *
 * `ENROLL_LEN` in `crates/server/src/routes/children.rs`. Duplicated here
 * because a phone cannot ask the server how long a code is before it has one,
 * and named rather than inlined because the last time this number lived inside
 * an `enabled =` expression it went stale silently.
 */
const val ENROLL_CODE_LENGTH = 6

/**
 * Whether a typed code is worth sending.
 *
 * A function, not a comparison inside the composable, so the suite can see it:
 * the button was gated on eight characters after the server moved to six, and
 * the result was a Connect button that could never be pressed on any phone —
 * with no test able to notice, because there was nothing to call.
 */
fun enrollCodeComplete(code: String): Boolean = code.trim().length == ENROLL_CODE_LENGTH
