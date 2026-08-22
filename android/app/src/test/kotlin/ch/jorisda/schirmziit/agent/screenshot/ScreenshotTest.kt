package ch.jorisda.schirmziit.agent.screenshot

import androidx.compose.runtime.Composable
import ch.jorisda.schirmziit.agent.pair.PairingScreen
import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.store.FakeAgentSettings
import ch.jorisda.schirmziit.agent.ui.StatusScreen
import ch.jorisda.schirmziit.agent.ui.theme.SchirmziitTheme
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
        )
    }
}
