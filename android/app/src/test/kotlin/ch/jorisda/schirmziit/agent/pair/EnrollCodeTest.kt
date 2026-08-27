package ch.jorisda.schirmziit.agent.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The length of a pairing code, in the one place that decides it.
 *
 * The server mints six characters (`ENROLL_LEN` in
 * `crates/server/src/routes/children.rs`). The Connect button on
 * `PairingScreen` used to be gated on eight, inline, with no test: the copy said
 * six, the server sent six, and the button stayed disabled forever, so a code
 * could not be typed on Android at all. Nothing in the suite could see it,
 * because a length comparison living in an `enabled =` expression is not
 * reachable from a unit test — which is the whole reason it is a function now.
 */
class EnrollCodeTest {

    @Test
    fun `a code of the length the server mints is submittable`() {
        assertEquals("the server's own ENROLL_LEN", 6, ENROLL_CODE_LENGTH)
        assertTrue(enrollCodeComplete("K7MNPQ"))
    }

    @Test
    fun `a half-typed code is not`() {
        assertFalse(enrollCodeComplete(""))
        assertFalse(enrollCodeComplete("K7M"))
        assertFalse(enrollCodeComplete("K7MNP"))
    }

    @Test
    fun `an over-long code is not`() {
        // Refused rather than truncated: a parent who typed seven characters got
        // one of them wrong, and enrolling against a code they did not read out
        // is worse than telling them to check it.
        assertFalse(enrollCodeComplete("K7MNPQR"))
        assertFalse(enrollCodeComplete("K7MNPQ12"))
    }

    @Test
    fun `surrounding whitespace does not count towards the length`() {
        // A code read out over the phone gets a trailing space from the keyboard
        // more often than not, and the button must not stay dead because of it.
        assertTrue(enrollCodeComplete(" K7MNPQ "))
        assertTrue(enrollCodeComplete("K7MNPQ\n"))
    }
}
