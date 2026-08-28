package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeletons shaped like the content that replaces them — never a spinner over
 * the layout.
 *
 * The shape is the point: a parent's eye settles on where the numbers will be
 * before they arrive, so the screen does not jump when they do. The same reason
 * the error panel takes its skeleton's footprint.
 */
@Composable
private fun Bone(width: Dp? = null, height: Dp = 16.dp, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    // A slow breath, not a shimmer sweep: this sits under a screen a parent
    // opens daily, and a gradient racing across it is the interface performing.
    // Reduced motion holds it still at the mid tone.
    val pulse = if (reduced) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skeleton-pulse",
        )
        value
    }

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .alpha(pulse)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    )
}

/** One child row: a name on the left, today's total on the right. */
@Composable
fun ChildRowsSkeleton(rows: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bone(width = 120.dp, height = 20.dp)
                Bone(width = 72.dp, height = 20.dp)
            }
        }
    }
}

/** The fourteen-day strip, at the height the real bars occupy. */
@Composable
fun StripSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Bone(width = 140.dp, height = 20.dp)
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Varied heights, not a flat row: a flat skeleton reads as a chart
            // that has already loaded and found nothing.
            listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.3f, 0.6f, 0.8f, 0.5f, 0.7f, 0.4f, 0.6f, 0.9f, 0.5f, 0.7f)
                .forEach { share ->
                    Bone(height = (12 + 48 * share).dp, modifier = Modifier.weight(1f))
                }
        }
    }
}

/** Two figures side by side, the shape the week card settles into. */
@Composable
fun WeekSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Bone(width = 120.dp, height = 18.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            repeat(2) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Bone(width = 88.dp, height = 12.dp)
                    Bone(width = 104.dp, height = 28.dp)
                    Bone(width = 120.dp, height = 12.dp)
                }
            }
        }
    }
}

/** The day's headline total, its hour ribbon, and a few app rows. */
@Composable
fun DaySkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Bone(width = 100.dp, height = 14.dp)
            Bone(width = 180.dp, height = 40.dp)
            Bone(width = 120.dp, height = 14.dp)
        }
        Bone(height = 56.dp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Bone(width = 140.dp)
                    Bone(width = 60.dp)
                }
            }
        }
    }
}
