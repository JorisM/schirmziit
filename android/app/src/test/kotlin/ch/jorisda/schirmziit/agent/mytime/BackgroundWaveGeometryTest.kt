package ch.jorisda.schirmziit.agent.mytime

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundWaveGeometryTest {

    @Test
    fun `half an hour is half the lane`() {
        assertEquals(0.5f, backgroundShare(1_800_000L), 0.001f)
    }

    @Test
    fun `an hour fills it and more does not overflow`() {
        assertEquals(1f, backgroundShare(3_600_000L), 0.001f)
        assertEquals(1f, backgroundShare(10 * 3_600_000L), 0.001f)
    }

    @Test
    fun `the scale is fixed, so the same half hour looks the same on any day`() {
        // A day-relative scale would make a quiet day's peak look identical to
        // a loud day's, and the child could not compare one to the next.
        assertEquals(backgroundShare(1_800_000L), backgroundShare(1_800_000L), 0.0f)
        assertEquals(0.25f, backgroundShare(900_000L), 0.001f)
    }

    @Test
    fun `a negative value cannot invert the lane`() {
        assertEquals(0f, backgroundShare(-1L), 0.001f)
    }
}
