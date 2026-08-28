package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import ch.jorisda.schirmziit.agent.ui.theme.DarkColors
import ch.jorisda.schirmziit.agent.ui.theme.LightColors
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A skeleton nobody can see is worse than no skeleton: the screen keeps the
 * space the numbers will take and shows a hole in the middle of itself, which
 * reads as a layout bug rather than as "still loading".
 *
 * This is what went wrong: bones were painted in `surfaceVariant`, which this
 * theme defines as the paper colour — the exact colour of the page behind them.
 * Every skeleton on every parent screen was invisible, and no golden caught it
 * because none of them captured a loading screen until the week card put one in
 * the middle of a loaded one.
 */
class SkeletonContrastTest {

    /** The largest per-channel distance, 0–255. Cheap, and enough to catch "the same colour". */
    private fun distance(a: Color, b: Color): Int {
        fun channels(color: Color) = listOf(color.red, color.green, color.blue)
            .map { (it * 255).roundToInt() }
        return channels(a).zip(channels(b)).maxOf { (one, other) -> abs(one - other) }
    }

    /**
     * Below this a bone is a rumour. The palette's hairline sits ~19 steps from
     * paper and ~28 from a card, so this leaves room for a repaint without
     * leaving room for "invisible".
     */
    private val visible = 12

    /**
     * What the bone actually paints as: it is drawn with `alpha`, so the colour
     * that reaches the eye is the bone blended into whatever it stands on. The
     * dimmest frame of the breath is the one that has to stay legible.
     */
    private fun rendered(bone: Color, ground: Color, alpha: Float) = Color(
        red = ground.red + (bone.red - ground.red) * alpha,
        green = ground.green + (bone.green - ground.green) * alpha,
        blue = ground.blue + (bone.blue - ground.blue) * alpha,
    )

    private fun assertBonesAreVisible(scheme: ColorScheme, named: String) {
        val bone = skeletonBone(scheme)
        // Both grounds: skeletons stand on the page and inside cards.
        for ((ground, where) in listOf(scheme.background to "the page", scheme.surface to "a card")) {
            val faintest = rendered(bone, ground, BONE_MIN_ALPHA)
            assertTrue(
                "$named bones are invisible on $where at their faintest: ${distance(faintest, ground)}",
                distance(faintest, ground) >= visible,
            )
        }
    }

    @Test
    fun `bones are visible in the light theme`() {
        assertBonesAreVisible(LightColors, "light")
    }

    @Test
    fun `bones are visible in the dark theme`() {
        assertBonesAreVisible(DarkColors, "dark")
    }

    @Test
    fun `a bone is not the ink it stands in for`() {
        // The other way to get this wrong: a bone dark enough to read as content
        // is a skeleton pretending to be the number it is waiting for.
        assertTrue(distance(skeletonBone(LightColors), LightColors.onSurface) >= visible)
        assertTrue(distance(skeletonBone(DarkColors), DarkColors.onSurface) >= visible)
    }

    @Test
    fun `reduced motion holds a bone at full strength`() {
        // Not at the middle of the breath: nothing animating but a bone at half
        // strength is the same invisible gap by another route, and reduced
        // motion is a first-class path here rather than a degraded one.
        assertEquals(1f, BONE_FULL_ALPHA)
        assertTrue(BONE_MIN_ALPHA in 0f..BONE_FULL_ALPHA)
    }
}
