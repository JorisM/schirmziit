package ch.jorisda.schirmziit.agent.screenshot

import androidx.compose.runtime.Composable
import ch.jorisda.schirmziit.agent.mytime.MyTime
import ch.jorisda.schirmziit.agent.pair.PairingScreen
import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import ch.jorisda.schirmziit.agent.ui.MyTimeScreen
import ch.jorisda.schirmziit.agent.ui.StatusScreen
import ch.jorisda.schirmziit.agent.ui.theme.SchirmziitTheme
import ch.jorisda.schirmziit.core.AppTotalFfi
import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Images of the child app's screens, light and dark, in German and English.
 *
 * These catch what the unit tests structurally cannot: a card that overflows, a
 * button pushed off screen by a long translation, a colour that disappears on
 * the dark surface. Re-record deliberately with `-Precord.snapshots`, never to
 * turn a red test green.
 */
@RunWith(RobolectricTestRunner::class)
class ScreenshotTest {
    // Android's qualifier order is fixed: locale, size, night, density. A
    // class-level @Config would be concatenated in front of these and break it.
    private companion object {
        const val LIGHT_DE = "de-rCH-w411dp-h891dp-xxhdpi"
        const val DARK_DE = "de-rCH-w411dp-h891dp-night-xxhdpi"
        const val LIGHT_EN = "en-w411dp-h891dp-xxhdpi"
    }

    /// Captures the composable directly rather than through a compose rule: the
    /// rule wants a host activity, which a library-less unit test does not have.
    private fun shoot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/snapshots/$name.png") { SchirmziitTheme { content() } }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `pairing screen in german`() {
        shoot("pairing-de-light") { PairingScreen(settings = FakeAgentSettings(), onPaired = {}) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `pairing screen in german dark`() {
        shoot("pairing-de-dark") { PairingScreen(settings = FakeAgentSettings(), onPaired = {}) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `pairing screen in english`() {
        shoot("pairing-en-light") { PairingScreen(settings = FakeAgentSettings(), onPaired = {}) }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `status screen while reporting`() {
        shoot("status-reporting-de-light") { statusScreen() }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `status screen while reporting dark`() {
        shoot("status-reporting-de-dark") { statusScreen() }
    }

    /// Usage access off: nothing is being recorded, and the screen has to say so
    /// rather than looking healthy.
    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `status screen without usage access`() {
        shoot("status-no-access-de-light") { statusScreen(hasPermission = false) }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `status screen with battery optimisation in the way`() {
        shoot("status-battery-de-light") { statusScreen(batteryHint = BatteryHint.Urgent) }
    }

    @Composable
    private fun statusScreen(
        hasPermission: Boolean = true,
        batteryHint: BatteryHint = BatteryHint.None,
    ) {
        StatusScreen(
            settings = FakeAgentSettings(
                baseUrl = "https://schirmziit.jorisda.ch",
                deviceToken = "tok",
                lastSyncMillis = 1_787_997_600_000,
            ),
            pendingHours = 2,
            hasPermission = hasPermission,
            batteryHint = batteryHint,
            onSendNow = {},
            onAllowBackground = {},
            onOpenMyTime = {},
        )
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `my time screen in german`() {
        shoot("mytime-de-light") { MyTimeScreen(state = sampleMyTime(), onSelectDay = {}, onBack = {}) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `my time screen in german dark`() {
        shoot("mytime-de-dark") { MyTimeScreen(state = sampleMyTime(), onSelectDay = {}, onBack = {}) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `my time screen could not load`() {
        // Real days and a real detail underneath the failure, not an already-
        // empty MyTime: the point of this image is proving the failed-state
        // guard *suppresses* that data, which a `MyTime(emptyList(), ...)`
        // input can't demonstrate — there'd be nothing to suppress, so the
        // image would look identical whether the guard fired or not.
        shoot("mytime-failed-en-light") {
            MyTimeScreen(state = sampleMyTime().copy(failed = true), onSelectDay = {}, onBack = {})
        }
    }

    /** A fortnight of varied totals, including one zero day, and one day's detail. */
    private fun sampleMyTime(): MyTime {
        val days = listOf(
            "2026-08-07" to 5_400_000L,
            "2026-08-08" to 7_200_000L,
            "2026-08-09" to 3_000_000L,
            "2026-08-10" to 0L,
            "2026-08-11" to 9_600_000L,
            "2026-08-12" to 4_200_000L,
            "2026-08-13" to 6_000_000L,
            "2026-08-14" to 8_100_000L,
            "2026-08-15" to 2_400_000L,
            "2026-08-16" to 5_700_000L,
            "2026-08-17" to 6_900_000L,
            "2026-08-18" to 3_600_000L,
            "2026-08-19" to 7_800_000L,
            "2026-08-20" to 6_300_000L,
        ).map { (day, ms) -> DayTotalFfi(day, ms) }

        val hours = List(24) { hour ->
            when {
                hour in 7..8 -> 900_000L
                hour in 12..13 -> 600_000L
                hour in 16..21 -> 1_200_000L
                else -> 0L
            }
        }

        val apps = listOf(
            AppTotalFfi("ch.jorisda.videoapp", "VideoApp", 2_400_000L),
            AppTotalFfi("ch.jorisda.chat", "ChatApp", 1_800_000L),
            AppTotalFfi("ch.jorisda.browser", "Browser", 900_000L),
            AppTotalFfi("ch.jorisda.game1", "Game One", 600_000L),
            AppTotalFfi("ch.jorisda.game2", "Game Two", 300_000L),
            AppTotalFfi("ch.jorisda.music", "Music", 180_000L),
            AppTotalFfi("ch.jorisda.notes", "Notes", 90_000L),
            AppTotalFfi("ch.jorisda.mail", "Mail", 30_000L),
            AppTotalFfi("ch.jorisda.weather", "Weather", 10_000L),
            // Rounds to 0 s and must not appear anywhere, folded or not.
            AppTotalFfi("ch.jorisda.blink", "Blink", 300L),
        )

        val detail = DayDetailFfi(
            totalMs = 6_300_000L,
            unlockCount = 42,
            hours = hours,
            apps = apps,
        )

        return MyTime(days = days, detail = detail, selected = "2026-08-20", failed = false)
    }
}
