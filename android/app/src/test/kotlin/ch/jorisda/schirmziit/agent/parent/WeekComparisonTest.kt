package ch.jorisda.schirmziit.agent.parent

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The week is read strictly, because the alternative is a card full of zeroes
 * out of a captcha page — a lost week rendered as a quiet one.
 */
// Robolectric: android.jar's org.json is a stub whose methods return null.
@RunWith(RobolectricTestRunner::class)
class WeekComparisonTest {

    private fun body(week: String) = JSONObject("""{"child_id":"c1","tz":"Europe/Zurich","week":$week}""")

    private val full = """
        {
          "from":"2026-08-13","to":"2026-08-19",
          "previous_from":"2026-08-06","previous_to":"2026-08-12",
          "total_ms":44400000,"previous_total_ms":42000000,
          "evening_ms":7200000,"previous_evening_ms":4800000,
          "evening_from_hour":21,
          "movers":[{"package":"com.a","label":"TikTok",
                     "foreground_ms":9000000,"previous_foreground_ms":3600000}],
          "previous_measured":true
        }
    """.trimIndent()

    @Test
    fun `a full body becomes a comparison`() {
        val week = weekComparisonFrom(body(full))

        assertNotNull(week)
        assertEquals(21, week!!.eveningFromHour)
        assertEquals(2_400_000L, week.deltaMs)
        assertEquals(2_400_000L, week.eveningDeltaMs)
        assertEquals(5_400_000L, week.movers.single().deltaMs)
        assertEquals("TikTok", week.movers.single().label)
        assertTrue(week.previousMeasured)
    }

    @Test
    fun `a first week is read, not refused`() {
        val week = weekComparisonFrom(
            body(full.replace("\"previous_measured\":true", "\"previous_measured\":false")),
        )

        assertNotNull(week)
        assertFalse(week!!.previousMeasured)
    }

    @Test
    fun `a body missing a total is not a week of no screen time`() {
        // The whole reason every field is read with `get`: a truncated body must
        // be a failure the screen can explain, never a zero it would draw.
        assertNull(weekComparisonFrom(body(full.replace("\"total_ms\":44400000,", ""))))
    }

    @Test
    fun `a mover missing its own figures takes the week down with it`() {
        assertNull(
            weekComparisonFrom(
                body(full.replace("\"previous_foreground_ms\":3600000", "\"unrelated\":1")),
            ),
        )
    }

    @Test
    fun `a body with no week at all is nothing`() {
        assertNull(weekComparisonFrom(JSONObject("""{"child_id":"c1","tz":"Europe/Zurich"}""")))
        assertNull(weekComparisonFrom(null))
    }

    // ─── how it lands on the screen ──────────────────────────────────────

    @Test
    fun `a failed week keeps the one already on screen`() {
        val loaded = weekComparisonFrom(body(full))!!
        val state = ChildDayState(selected = "2026-08-20", week = loaded)

        val after = mergeWeek(state, Result.failure(IOException("no route to host")))

        assertEquals(loaded, after.week)
        assertNotNull(after.weekFailure)
    }

    @Test
    fun `a week that loads clears the failure beside it`() {
        val loaded = weekComparisonFrom(body(full))!!
        val state = ChildDayState(
            selected = "2026-08-20",
            weekFailure = ApiFailure.of(IOException("offline"), "/v1/children/insight"),
        )

        val after = mergeWeek(state, Result.success(loaded))

        assertEquals(loaded, after.week)
        assertNull(after.weekFailure)
    }

    @Test
    fun `picking a day leaves the week alone`() {
        // The week is about the fortnight, not the tapped day: re-fetching or
        // blanking it on every tap would be the expensive half of the screen
        // doing the least useful work.
        val loaded = weekComparisonFrom(body(full))!!
        val state = ChildDayState(selected = "2026-08-20", week = loaded)

        assertEquals(loaded, selectDay(state, "2026-08-18").week)
    }
}
