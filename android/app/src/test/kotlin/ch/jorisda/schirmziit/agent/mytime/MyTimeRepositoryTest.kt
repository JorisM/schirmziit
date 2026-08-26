package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTimeRepositoryTest {

    private val strip = listOf(DayTotalFfi("2026-08-20", 60_000L, 0L))
    private val detail = DayDetailFfi(60_000L, 3, List(24) { 0L }, emptyList(), 0L, List(24) { 0L }, false)

    @Test
    fun `loads the strip and the selected day`() {
        val repo = MyTimeRepository(
            fetch = { _, _, bucket, _ -> if (bucket == "day") "STRIP" else "DETAIL" },
            parseStrip = { json -> if (json == "STRIP") strip else error("wrong body") },
            parseDetail = { json -> if (json == "DETAIL") detail else error("wrong body") },
        )

        val result = repo.load("2026-08-20")

        assertEquals(strip, result.days)
        assertEquals(detail, result.detail)
        assertFalse(result.failed)
    }

    @Test
    fun `a failed load reports a failure and never an empty day`() {
        val repo = MyTimeRepository(
            fetch = { _, _, _, _ -> throw IllegalStateException("offline") },
            parseStrip = { strip },
            parseDetail = { detail },
        )

        val result = repo.load("2026-08-20")

        // The screen never sees this shape directly — `mergeMyTimeResult`
        // (MyTimeUiStateTest) keeps whatever was already on screen and reads
        // only `failed` from here — but the repository must still tell its
        // caller plainly that there is nothing usable in this result.
        assertTrue("the caller must be told the load failed", result.failed)
        assertTrue(result.days.isEmpty())
        assertEquals(null, result.detail)
    }

    @Test
    fun `an unparseable body is a failure, not a quiet day`() {
        val repo = MyTimeRepository(
            fetch = { _, _, _, _ -> "<html>captcha</html>" },
            parseStrip = { throw IllegalArgumentException("malformed json") },
            parseDetail = { detail },
        )

        assertTrue(repo.load("2026-08-20").failed)
    }

    /// The finding this test exists for: picking a day used to always fetch
    /// both the strip and the detail, costing a tap two requests instead of
    /// one — expensive on a child's phone, the surface of the three most
    /// likely to be metered.
    @Test
    fun `days already on screen are reused instead of re-fetching the strip`() {
        var fetchCalls = 0
        val repo = MyTimeRepository(
            fetch = { _, _, bucket, _ ->
                fetchCalls++
                if (bucket == "day") error("must not fetch the strip when the caller already has one")
                "DETAIL"
            },
            parseStrip = { error("must not parse a strip that was never fetched") },
            parseDetail = { detail },
        )

        val result = repo.load("2026-08-20", days = strip)

        assertEquals("picking a day must cost exactly one request", 1, fetchCalls)
        assertEquals(strip, result.days)
        assertEquals(detail, result.detail)
    }

    @Test
    fun `a malformed selected day is a failure, not a crash`() {
        // The default `from` used to be computed outside the try/catch, so a
        // bad selected day threw straight out of load() instead of landing on
        // failed = true.
        val repo = MyTimeRepository(
            fetch = { _, _, _, _ -> error("must not be called") },
            parseStrip = { strip },
            parseDetail = { detail },
        )

        assertTrue(repo.load("not-a-date").failed)
    }
}
