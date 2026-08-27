package ch.jorisda.schirmziit.agent.ui.parent

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The motion budget, in one place, matching `web/src/index.css`'s `--motion-*`
 * tokens and `ios/Sources/Design/Motion.swift`.
 *
 * Only the *parent* screens use this. The child agent stays motion-free on
 * purpose — it is a background collector and battery is its budget — which is
 * why these live under `ui.parent` rather than next to the theme.
 */
object Motion {
    /** Press feedback. A different category from entry motion, deliberately quicker. */
    const val FAST = 120

    /** Entry and transition motion: the 200–400 ms band everything normal sits in. */
    const val BASE = 260

    /** The one hero count-up per screen. */
    const val HERO = 600

    /** Per-row offset for a staggered list. Not a duration. */
    const val STAGGER = 40

    fun staggerDelay(index: Int, cap: Int = 8): Int = STAGGER * minOf(index, cap)

    /**
     * `tween`, or an instant landing under reduced motion.
     *
     * Zero duration rather than no animation at all: the caller still writes one
     * code path, and reduced motion lands on the *final* state — never on a
     * half-drawn chart.
     */
    fun <T> spec(reduced: Boolean, durationMillis: Int = BASE, delayMillis: Int = 0): AnimationSpec<T> =
        if (reduced) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = FastOutSlowInEasing)
        }
}

/**
 * Whether this phone asks for reduced motion.
 *
 * `ANIMATOR_DURATION_SCALE == 0` is Android's answer — there is no
 * `prefers-reduced-motion` media query here, and "Remove animations" in
 * accessibility settings is what sets it. Read once per composition: it cannot
 * change without the app being restarted by the system anyway, and reading it
 * on every frame would put a ContentResolver call inside a draw pass.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
