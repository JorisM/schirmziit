package ch.jorisda.schirmziit.agent.screenshot

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ch.jorisda.schirmziit.agent.parent.ApiFailure
import ch.jorisda.schirmziit.agent.parent.ChildDayState
import ch.jorisda.schirmziit.agent.parent.ChildrenState
import ch.jorisda.schirmziit.agent.parent.Enrollment
import ch.jorisda.schirmziit.agent.parent.PairingState
import ch.jorisda.schirmziit.agent.parent.ParentChild
import ch.jorisda.schirmziit.agent.parent.ParentDevice
import ch.jorisda.schirmziit.agent.parent.Purged
import ch.jorisda.schirmziit.agent.parent.PurgeState
import ch.jorisda.schirmziit.agent.ui.parent.ChildDetailScreen
import ch.jorisda.schirmziit.agent.ui.parent.ChildrenScreen
import ch.jorisda.schirmziit.agent.ui.parent.ErrorPanel
import ch.jorisda.schirmziit.agent.ui.parent.ErrorPlacement
import ch.jorisda.schirmziit.agent.ui.parent.PairDeviceCard
import ch.jorisda.schirmziit.agent.ui.parent.PurgeDataCard
import ch.jorisda.schirmziit.agent.ui.parent.ParentHelpScreen
import ch.jorisda.schirmziit.agent.ui.parent.RoleChoiceScreen
import ch.jorisda.schirmziit.agent.ui.parent.SignInScreen
import ch.jorisda.schirmziit.agent.ui.theme.SchirmziitTheme
import ch.jorisda.schirmziit.core.AppTotalFfi
import ch.jorisda.schirmziit.core.DayDetailFfi
import ch.jorisda.schirmziit.core.DayTotalFfi
import ch.jorisda.schirmziit.core.ErrorCode
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone
import org.robolectric.annotation.Config

/**
 * Images of the parent screens, light and dark, in German and English.
 *
 * **Every shot is taken with animations switched off at the system level**, and
 * that is the point twice over. It makes the goldens deterministic — these
 * screens stagger their rows in, count a total up and sweep a ribbon across, and
 * a capture mid-flight would compare a different frame on every run. And it
 * captures the *reduced-motion* path, which is the one that must land on the
 * finished state rather than on a half-drawn chart: a bar at 20 % height in one
 * of these images is a bug in the reduced-motion path, not a timing artefact.
 *
 * The animated path is guaranteed to end in the same place by construction —
 * every animation here is an `animateFloatAsState` toward a fixed target — so
 * one settled golden covers both.
 *
 * Re-record deliberately with `-Precord.snapshots`, and look at the images.
 * Never to turn a red test green.
 */
@RunWith(RobolectricTestRunner::class)
class ParentScreenshotTest {
    // Android's qualifier order is fixed: locale, size, night, density. A
    // class-level @Config would be concatenated in front of these and break it.
    private companion object {
        const val LIGHT_DE = "de-rCH-w411dp-h891dp-xxhdpi"
        const val DARK_DE = "de-rCH-w411dp-h891dp-night-xxhdpi"
        const val LIGHT_EN = "en-w411dp-h891dp-xxhdpi"
        const val DARK_EN = "en-w411dp-h891dp-night-xxhdpi"
        const val LIGHT_FR = "fr-w411dp-h891dp-xxhdpi"

        /** 2026-08-27 20:00 Europe/Zurich — the clock these goldens are read at. */
        const val MINTED_AT = 1_787_997_600_000L
    }

    /// Same widened per-pixel distance as `ScreenshotTest`: Skia rounds
    /// premultiplied alpha differently on macOS/arm64 than on the Linux/x86-64
    /// runner, so a handful of antialiased edge pixels come out one step apart
    /// and the default comparator calls that a difference. A moved button or a
    /// wrapped line differs by far more.
    private val comparing = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator(maxDistance = 0.01f),
        ),
    )

    @Before
    fun pinTheClockZone() {
        // `StatusText.lastSeen` formats in the phone's own zone, which under
        // Robolectric is the *host's* zone — Europe/Zurich on this Mac, UTC on
        // the runner. Two hours apart is two different device rows, so goldens
        // recorded on one machine could never verify on the other. Pinned here
        // rather than made injectable: the zone is genuinely the phone's
        // business everywhere except in this file.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"))
    }

    @Before
    fun stopAnimating() {
        // What `rememberReducedMotion()` reads. Set before any composition, so
        // every screen below starts and stays on its settled state.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    /**
     * Wrapped in the same `Surface` `MainActivity` puts these screens in.
     *
     * Not cosmetic. Without it there is no background to paint and no content
     * colour to inherit, so `LocalContentColor` falls back to black and every
     * headline that does not name its own colour renders black on nothing — the
     * first recording of the help screen came out with an invisible title over a
     * transparent page. `fillMaxSize` is what gives a screen taller than the
     * viewport a full page to sit on rather than a strip the height of its own
     * content.
     */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/snapshots/$name.png", roborazziOptions = comparing) {
            SchirmziitTheme {
                // `background`, matching `ParentApp`'s own Surface: the scheme's
                // `surface` is this palette's card colour, and a Card on it
                // disappears.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) { content() }
            }
        }
    }

    // ─── the first question the app asks ─────────────────────────────────

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `role choice in german`() {
        shoot("parent-role-de-light") { RoleChoiceScreen(onChoose = {}) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `role choice in german dark`() {
        shoot("parent-role-de-dark") { RoleChoiceScreen(onChoose = {}) }
    }

    @Test
    @Config(qualifiers = LIGHT_FR)
    fun `role choice in french`() {
        // French is the longest of the four here — two full sentences per card.
        // If a card overflows anywhere it overflows here first.
        shoot("parent-role-fr-light") { RoleChoiceScreen(onChoose = {}) }
    }

    // ─── sign-in ─────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `sign in in german`() {
        shoot("parent-signin-de-light") { signIn() }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `sign in in german dark`() {
        shoot("parent-signin-de-dark") { signIn() }
    }

    @Composable
    private fun signIn() = SignInScreen(
        onSignIn = { _, _, _ -> null },
        onSignedIn = {},
        onBack = {},
    )

    // ─── the children list ───────────────────────────────────────────────

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `children in german`() {
        shoot("parent-children-de-light") { children(ChildrenState(children = sampleChildren())) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `children in german dark`() {
        shoot("parent-children-de-dark") { children(ChildrenState(children = sampleChildren())) }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `children with no child yet`() {
        // The empty state carries the action it is asking for. Distinct from the
        // skeleton: an empty list is a fact, a first load is not.
        shoot("parent-children-empty-de-light") { children(ChildrenState(children = emptyList())) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `children keep their numbers when a refresh fails`() {
        // The image that proves the rule: a failed poll adds a banner above the
        // list, it does not blank it. A `children = null` input could not show
        // that, because there would be nothing left on screen either way.
        shoot("parent-children-stale-en-light") {
            children(
                ChildrenState(
                    children = sampleChildren(),
                    failure = ApiFailure(ErrorCode.SERVER_UNREACHABLE, "a1b2c3", "/v1/children"),
                ),
            )
        }
    }

    @Composable
    private fun children(state: ChildrenState) = ChildrenScreen(
        state = state,
        onOpenChild = {},
        onAddChild = {},
        onRemoveChild = {},
        onRetry = {},
        onOpenHelp = {},
        onSignOut = {},
    )

    // ─── one child's day ─────────────────────────────────────────────────

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `child detail in german`() {
        shoot("parent-child-de-light") { childDetail(loadedDay()) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `child detail in german dark`() {
        shoot("parent-child-de-dark") { childDetail(loadedDay()) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `child detail where background listening was not measured`() {
        // Must read as "not counted on this phone", never as a flat zero: a
        // phone without the grant does not know that nothing played.
        shoot("parent-child-unmeasured-en-light") {
            childDetail(
                loadedDay().let { state ->
                    state.copy(
                        detail = state.detail!!.copy(
                            backgroundMs = 0L,
                            backgroundHours = List(24) { 0L },
                            backgroundMeasured = false,
                        ),
                    )
                },
            )
        }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `a fortnight that failed to load is never fourteen quiet bars`() {
        // The lost-day rule at the presentation layer. Fourteen zero-filled
        // bars would read as a genuinely quiet fortnight; this has to be an
        // error panel with a code on it instead.
        shoot("parent-child-strip-failed-en-light") {
            childDetail(
                ChildDayState(
                    selected = "2026-08-20",
                    stripFailure = ApiFailure(ErrorCode.OFFLINE, "ffeedd", "/v1/children/usage"),
                    detail = loadedDay().detail,
                    devices = sampleDevices(),
                ),
            )
        }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `a day that failed keeps the fortnight on screen`() {
        shoot("parent-child-day-failed-en-light") {
            childDetail(
                loadedDay().copy(
                    dayFailure = ApiFailure(ErrorCode.TIMEOUT, "0f0f0f", "/v1/children/usage"),
                ),
            )
        }
    }

    @Composable
    private fun childDetail(
        state: ChildDayState,
        pairing: PairingState = PairingState(),
        nowMillis: Long = MINTED_AT,
    ) = ChildDetailScreen(
        child = ParentChild("c1", "Lena", 6_300_000L),
        state = state,
        pairing = pairing,
        purge = PurgeState(),
        // A fixed clock, not the wall clock: it decides which of two lines sits
        // under a minted code, and a golden that flips at a quarter past is a
        // golden nobody can verify.
        nowMillis = nowMillis,
        onSelectDay = {},
        onRetryDay = {},
        onRetryStrip = {},
        onMintCode = {},
        onRevokeDevice = {},
        onAskPurge = {},
        onCancelPurge = {},
        onConfirmPurge = {},
        onBack = {},
    )

    // ─── connecting a phone ──────────────────────────────────────────────

    // The card on its own rather than through `ChildDetailScreen`: it sits under
    // the devices list, which is well past the first viewport, and Roborazzi
    // captures one screen's worth. A golden of the scroll position above it
    // proves nothing about the card.

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `an unminted pairing card offers a code and shows none`() {
        // Nothing is minted on appearance: a code lives fifteen minutes and can
        // be claimed once, so opening the screen must not burn one.
        shoot("parent-pair-empty-de-light") { pairCard(PairingState()) }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `a minted pairing code in german`() {
        shoot("parent-pair-de-light") { pairCard(PairingState(enrollment = enrollment())) }
    }

    @Test
    @Config(qualifiers = DARK_DE)
    fun `a minted pairing code in german dark`() {
        shoot("parent-pair-de-dark") { pairCard(PairingState(enrollment = enrollment())) }
    }

    @Test
    @Config(qualifiers = LIGHT_FR)
    fun `a minted pairing code in french`() {
        // The three steps are longest in French, and step 2 is the one naming
        // the server address — the half of the pairing whose failure is silent.
        shoot("parent-pair-fr-light") { pairCard(PairingState(enrollment = enrollment())) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `an expired pairing code says so instead of looking usable`() {
        // A code shown as usable after it expired sends a parent to a phone that
        // will refuse it, so the expired line is not a styling variant of the
        // "valid until" one — and the code itself steps back rather than
        // arguing with the line underneath it.
        shoot("parent-pair-expired-en-light") {
            pairCard(
                PairingState(enrollment = enrollment()),
                nowMillis = MINTED_AT + 20 * 60 * 1000L,
            )
        }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `a failed mint keeps the code already on screen`() {
        shoot("parent-pair-stale-en-light") {
            pairCard(
                PairingState(
                    enrollment = enrollment(),
                    failure = ApiFailure(ErrorCode.TIMEOUT, "0c0d0e", "/v1/children/enrollments"),
                ),
            )
        }
    }

    @Composable
    private fun pairCard(state: PairingState, nowMillis: Long = MINTED_AT) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PairDeviceCard(state = state, nowMillis = nowMillis, onMint = {})
        }
    }

    private fun enrollment() = Enrollment(
        code = "K7MNPQ",
        expiresAtMillis = MINTED_AT + 15 * 60 * 1000L,
        qrPayload = "schirmziit://enroll?url=https://api.schirmziit.ch&code=K7MNPQ",
    )

    // ─── deleting a child's figures ──────────────────────────────────────

    // On its own for the same reason the pairing card is: it sits at the very
    // foot of `ChildDetailScreen`, past the devices list, and Roborazzi captures
    // one screen's worth.

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `the delete control asks before it deletes`() {
        // One press must not delete. The resting state is a single quiet text
        // button under the sentence saying what it does — not a red button
        // sitting next to numbers a parent came to read.
        shoot("parent-purge-de-light") { purgeCard(PurgeState()) }
    }

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `the question names what will go and offers a way out`() {
        shoot("parent-purge-asking-de-light") { purgeCard(PurgeState(asking = true)) }
    }

    @Test
    @Config(qualifiers = DARK_EN)
    fun `what was deleted is counted, not asserted`() {
        // "Deleted" with nothing behind it is exactly the claim a family has no
        // way to check. These are the server's own `rows_affected`.
        shoot("parent-purge-done-en-dark") {
            purgeCard(PurgeState(purged = Purged(usageHours = 412L, deviceHours = 168L, usageDays = 14L)))
        }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `a purge that matched nothing says zero rather than nothing`() {
        // A child whose phone has not reported yet. Zero has to be legible as an
        // answer, or a parent cannot tell a purge that worked from one that
        // found nothing.
        shoot("parent-purge-zero-en-light") {
            purgeCard(PurgeState(purged = Purged(0L, 0L, 0L)))
        }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `a purge that failed keeps the question open and shows no count`() {
        // A confirmation that closes on failure reads as "done". The question
        // stays, with the failure inside it — and no receipt anywhere near it.
        shoot("parent-purge-failed-en-light") {
            purgeCard(
                PurgeState(
                    asking = true,
                    failure = ApiFailure(ErrorCode.TIMEOUT, "0c0d0e", "/v1/children/data"),
                ),
            )
        }
    }

    @Composable
    private fun purgeCard(state: PurgeState) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // `reduced = true`: every shot here is taken with animations off at
            // the system level, and the card's receipt fades in. Passing the
            // reduced path explicitly captures the settled state rather than
            // whichever frame the capture happened to land on.
            PurgeDataCard(
                state = state,
                reduced = true,
                onAsk = {},
                onCancel = {},
                onConfirm = {},
            )
        }
    }

    // ─── help, and the error panel on its own ────────────────────────────

    @Test
    @Config(qualifiers = LIGHT_DE)
    fun `parent help in german`() {
        shoot("parent-help-de-light") { ParentHelpScreen(onBack = {}) }
    }

    @Test
    @Config(qualifiers = LIGHT_EN)
    fun `an urgent error and a neutral one do not look alike`() {
        // SZ-E101 is urgent, SZ-E501 is not. An offline phone in a Swiss valley
        // painting the screen red teaches a parent to ignore the colour that
        // means something actually broke.
        shoot("parent-error-panels-en-light") {
            Column(
                modifier = Modifier.safeDrawingPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ErrorPanel(
                    failure = ApiFailure(ErrorCode.INVALID_CREDENTIALS, "a1b2c3", "/v1/auth/login", 401),
                )
                ErrorPanel(
                    failure = ApiFailure(ErrorCode.OFFLINE, "d4e5f6", "/v1/children"),
                    placement = ErrorPlacement.Banner,
                    onRetry = {},
                )
            }
        }
    }

    // ─── the fixtures ────────────────────────────────────────────────────

    private fun sampleChildren() = listOf(
        ParentChild("c1", "Lena", 6_300_000L),
        ParentChild("c2", "Jonas", 1_800_000L),
        // A quiet day is a real number, and it has to render as one.
        ParentChild("c3", "Mira", 0L),
    )

    private fun sampleDevices() = listOf(
        ParentDevice("d1", "Fairphone 5", lastSeenAtMillis = 1_787_997_600_000L, stale = false),
        ParentDevice("d2", "Altes Tablet", lastSeenAtMillis = 1_787_900_000_000L, stale = true),
        // Never reported is not "reported long ago": the screen says so in words.
        ParentDevice("d3", "Neues Handy", lastSeenAtMillis = null, stale = true),
    )

    /** A fortnight of varied totals including one zero day, and one day's detail. */
    private fun loadedDay(): ChildDayState {
        val strip = listOf(
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
        ).map { (day, ms) -> DayTotalFfi(day, ms, 1_800_000L) }

        val hours = List(24) { hour ->
            when {
                hour in 7..8 -> 900_000L
                hour in 12..13 -> 600_000L
                hour in 16..21 -> 1_200_000L
                else -> 0L
            }
        }

        val apps = listOf(
            AppTotalFfi("ch.jorisda.videoapp", "VideoApp", 2_400_000L, 0L),
            AppTotalFfi("ch.jorisda.chat", "ChatApp", 1_800_000L, 0L),
            AppTotalFfi("ch.jorisda.browser", "Browser", 900_000L, 0L),
            AppTotalFfi("ch.jorisda.game1", "Game One", 600_000L, 0L),
            // Listened to with the screen off, mostly at bedtime.
            AppTotalFfi("ch.jorisda.music", "Music", 180_000L, 4_500_000L),
            AppTotalFfi("ch.jorisda.mail", "Mail", 30_000L, 0L),
            // Rounds to 0 s and must not appear anywhere, folded or not.
            AppTotalFfi("ch.jorisda.blink", "Blink", 300L, 0L),
        )

        return ChildDayState(
            selected = "2026-08-20",
            strip = strip,
            detail = DayDetailFfi(
                totalMs = 6_300_000L,
                unlockCount = 42,
                hours = hours,
                apps = apps,
                backgroundMs = 4_500_000L,
                backgroundHours = List(24) { hour -> if (hour == 22) 3_000_000L else 0L },
                backgroundMeasured = true,
            ),
            devices = sampleDevices(),
        )
    }
}
