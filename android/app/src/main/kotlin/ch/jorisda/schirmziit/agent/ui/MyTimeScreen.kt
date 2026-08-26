package ch.jorisda.schirmziit.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import android.provider.Settings
import ch.jorisda.schirmziit.agent.mytime.backgroundShare
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.mytime.MyTime
import ch.jorisda.schirmziit.agent.mytime.splitApps
import ch.jorisda.schirmziit.agent.mytime.visibleApps
import ch.jorisda.schirmziit.core.AppTotalFfi
import ch.jorisda.schirmziit.core.DayDetailFfi

/**
 * What the child sees about themselves. Deliberately the same numbers the parent
 * sees — the app's whole claim is that nothing here is hidden from the person
 * carrying the phone.
 */
@Composable
fun MyTimeScreen(
    state: MyTime,
    onSelectDay: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    // Independent of `state`: a first load in flight is passed a placeholder
    // `MyTime` (there is nothing real to show yet), and this flag is what
    // stops that placeholder's empty `detail` from being read as a genuinely
    // empty day below.
    loading: Boolean = false,
    // Independent of `state.failed` on purpose: the caller keeps the previous
    // successful `state` on screen when a load fails, so `state` itself never
    // carries `failed = true` here — this is what says a load just failed.
    error: Boolean = false,
) {
    Column(
        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A visible way back in every state, not only on failure: MainActivity
        // renders this screen in place of StatusScreen, so without this the
        // child would have no way back to it short of leaving the app.
        TextButton(onClick = onBack) { Text(stringResource(R.string.mytime_back)) }
        Text(stringResource(R.string.mytime_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.mytime_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The error sits above whatever is already on screen rather than
        // replacing it: a child who taps a bar on a flaky connection should
        // not have the screen emptied — only iOS got this right first.
        if (error) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.mytime_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.mytime_retry)) }
            }
        }

        val busiest = state.days.maxOfOrNull { it.foregroundMs } ?: 0L
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.days.forEach { day ->
                val share = if (busiest > 0) day.foregroundMs.toFloat() / busiest else 0f
                Column(
                    modifier = Modifier.weight(1f).clickable { onSelectDay(day.day) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            // A floor, not a zero: a quiet day is still a day,
                            // and a bar of no height reads as a hole in the chart.
                            .height((8 + 48 * share).dp)
                            .fillMaxWidth()
                            .background(
                                if (day.foregroundMs > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(3.dp),
                            )
                            .border(
                                if (day.day == state.selected) 2.dp else 0.dp,
                                MaterialTheme.colorScheme.onSurface,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                    Text(day.day.takeLast(2), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        val detail = state.detail
        if (detail == null) {
            // `detail == null` while `loading` means "not known yet", and while
            // `error` means "the error line above already explains why" — in
            // neither case is it "nothing recorded", which is what
            // mytime_empty says. Say nothing rather than say something false.
            if (!loading && !error) {
                Text(stringResource(R.string.mytime_empty), style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }
        if (detail.totalMs == 0L) {
            Text(stringResource(R.string.mytime_empty), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.mytime_total),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(StatusText.duration(detail.totalMs), style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.mytime_unlocks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${detail.unlockCount}")
                }
                if (detail.backgroundMeasured) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            stringResource(R.string.mytime_background),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Its own number, next to screen time and never inside
                        // it: listening with the screen off is not screen time.
                        Text(
                            StatusText.duration(detail.backgroundMs),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        // The day as 24 cells, same shape as the parent dashboard's hour
        // ribbon: a bar chart answers "how much", this answers "when".
        val busiestHour = detail.hours.maxOrNull() ?: 0L
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            detail.hours.forEach { hourMs ->
                val share = if (busiestHour > 0) hourMs.toFloat() / busiestHour else 0f
                Box(
                    Modifier
                        .weight(1f)
                        // Same floor logic as the day strip above: every hour
                        // keeps a visible sliver, so a quiet 03:00 still reads
                        // as an hour that happened rather than a gap.
                        .height((4 + 52 * share).dp)
                        .background(
                            if (hourMs > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }

        BackgroundLane(detail)

        if (detail.apps.isNotEmpty()) {
            // `visibleApps` is the tested seam: the cap lands on `shown`
            // alone, after the split, so a folded glance can never be the
            // thing an eight-row cap crowds out.
            val visible = remember(detail.apps) { visibleApps(splitApps(detail.apps), cap = 8) }
            var briefExpanded by remember(detail.apps) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mytime_apps), style = MaterialTheme.typography.titleMedium)
                    visible.shown.forEach { app -> AppRow(app) }
                    if (visible.brief.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { briefExpanded = !briefExpanded }
                                // A bare clickable says nothing to TalkBack about
                                // what tapping it does; the expand/collapse
                                // actions are what make it announce the state.
                                .semantics(mergeDescendants = true) {
                                    if (briefExpanded) {
                                        collapse { briefExpanded = false; true }
                                    } else {
                                        expand { briefExpanded = true; true }
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${stringResource(R.string.mytime_brief_apps)} (${visible.brief.size})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // No AnimatedVisibility: this screen runs in the
                        // battery-budgeted background collector, so the fold
                        // is a plain state toggle rather than motion.
                        if (briefExpanded) {
                            visible.brief.forEach { app -> AppRow(app) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppTotalFfi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(app.label)
        Text(StatusText.duration(app.foregroundMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Background listening as a lane under the hour ribbon, same 24-hour axis.
 *
 * `backgroundMeasured` is not "was there any": a phone that cannot observe
 * background playback gets a sentence saying so. A flat line here would tell
 * the child nothing played, which is the one thing we do not know.
 */
@Composable
private fun BackgroundLane(detail: DayDetailFfi) {
    if (!detail.backgroundMeasured) {
        Text(
            stringResource(R.string.mytime_background),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.mytime_background_not_measured),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val context = LocalContext.current
    // Reduced motion is a first-class path, not a fallback: it lands on the
    // finished wave rather than on a half-drawn one.
    val animatorScale = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }
    var drawn by remember(detail) { mutableStateOf(animatorScale == 0f) }
    LaunchedEffect(detail) { drawn = true }
    val progress by animateFloatAsState(
        targetValue = if (drawn) 1f else 0f,
        animationSpec = tween(durationMillis = if (animatorScale == 0f) 0 else 600),
        label = "background-wave",
    )

    val colour = MaterialTheme.colorScheme.tertiary
    // Titled, or the help line reads as a caption for the hour ribbon above it
    // and the wave looks like part of the same measure.
    Text(
        stringResource(R.string.mytime_background),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(R.string.mytime_background_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag("background-wave"),
    ) {
        val step = size.width / detail.backgroundHours.size
        val points = detail.backgroundHours.mapIndexed { index, ms ->
            Offset(step * (index + 0.5f), size.height - backgroundShare(ms) * size.height)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midX = (previous.x + current.x) / 2
                // Horizontally symmetric control points: an ordinary spline
                // overshoots below the baseline after a spike and draws
                // listening into an hour that had none.
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }

        val measure = PathMeasure().apply { setPath(path, false) }
        val drawnPath = Path()
        measure.getSegment(0f, measure.length * progress, drawnPath, true)
        drawPath(drawnPath, colour, style = Stroke(width = 2.dp.toPx()))
    }

    if (detail.backgroundMs == 0L) {
        Text(
            stringResource(R.string.mytime_background_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
