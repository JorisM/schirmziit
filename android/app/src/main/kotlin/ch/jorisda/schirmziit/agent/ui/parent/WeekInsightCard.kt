package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.WeekComparison
import ch.jorisda.schirmziit.agent.ui.StatusText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Last full week against the one before it — the only thing on this screen that
 * answers "was this week unusual", which one day never can.
 *
 * Every number is the server's, compared in `crates/core::insight`. This card
 * renders; it does not compare. Nothing here judges the child either: it says
 * what moved and by how much, in both directions, against no target and with no
 * streak anyone can lose.
 */
@Composable
fun WeekInsightCard(week: WeekComparison, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var risen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { risen = true }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.week_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    range(week.from, week.to),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure(
                    label = stringResource(R.string.week_total),
                    value = StatusText.duration(week.totalMs),
                    deltaMs = week.previousMeasured.takeIf { it }?.let { week.deltaMs },
                    index = 0,
                    risen = risen,
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                )
                Figure(
                    label = stringResource(R.string.week_evening_from, hour(week.eveningFromHour)),
                    value = StatusText.duration(week.eveningMs),
                    deltaMs = week.previousMeasured.takeIf { it }?.let { week.eveningDeltaMs },
                    index = 1,
                    risen = risen,
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                )
            }

            if (week.previousMeasured) {
                Text(
                    stringResource(R.string.week_movers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (week.movers.isEmpty()) {
                    Text(
                        stringResource(R.string.week_movers_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    week.movers.forEachIndexed { index, mover ->
                        val appear by animateFloatAsState(
                            targetValue = if (risen) 1f else 0f,
                            animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index + 2)),
                            label = "mover",
                        )
                        Row(
                            Modifier.fillMaxWidth().alpha(appear),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(mover.label, style = MaterialTheme.typography.bodyMedium)
                            Delta(mover.deltaMs)
                        }
                    }
                }
            } else {
                // Not a rise of a hundred per cent, and not a blank card: no
                // phone reported the week before, and a comparison against
                // silence is the lost day this app promises never to show.
                Text(
                    stringResource(R.string.week_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Figure(
    label: String,
    value: String,
    deltaMs: Long?,
    index: Int,
    risen: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val appear by animateFloatAsState(
        targetValue = if (risen) 1f else 0f,
        animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index)),
        label = "week-figure",
    )
    Column(modifier.alpha(appear), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.headlineSmall)
        if (deltaMs != null) Delta(deltaMs)
    }
}

/**
 * The direction in words as well as an arrow: an arrow alone is nothing to
 * TalkBack, and a colour alone is nothing to a reader who cannot separate the
 * two. The whole line is read as one phrase.
 */
@Composable
private fun Delta(ms: Long) {
    if (ms == 0L) {
        Text(
            stringResource(R.string.week_same),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val amount = StatusText.duration(kotlin.math.abs(ms))
    val arrow = if (ms > 0) "↑" else "↓"
    val sentence = stringResource(
        if (ms > 0) R.string.week_more else R.string.week_less,
        amount,
    )
    Text(
        "$arrow $sentence",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics { contentDescription = sentence },
    )
}

private fun hour(from: Int): String = "%02d:00".format(from)

/**
 * "13 – 19 Aug" in the phone's own locale. The dates arrive as `YYYY-MM-DD`,
 * which a screen reader would otherwise spell out digit by digit.
 */
private fun range(from: String, to: String): String {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return runCatching {
        "${LocalDate.parse(from).format(format)} – ${LocalDate.parse(to).format(format)}"
    }.getOrDefault("$from – $to")
}
