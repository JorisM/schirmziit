package ch.jorisda.schirmziit.agent.mytime

import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTimeRepositoryTest {

    private val strip = listOf(DayTotalFfi("2026-08-20", 60_000L))
    private val detail = DayDetailFfi(60_000L, 3, List(24) { 0L }, emptyList())

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

        assertTrue("the screen must say it could not load", result.failed)
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
}
