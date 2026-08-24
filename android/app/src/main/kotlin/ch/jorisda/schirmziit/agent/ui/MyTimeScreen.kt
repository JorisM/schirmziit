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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.mytime.MyTime

/**
 * What the child sees about themselves. Deliberately the same numbers the parent
 * sees — the app's whole claim is that nothing here is hidden from the person
 * carrying the phone.
 */
@Composable
fun MyTimeScreen(state: MyTime, onSelectDay: (String) -> Unit, onBack: () -> Unit) {
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

        // A failed load says so and shows nothing else. Zeros would read as
        // "you used nothing today", which is a lie told by a dropped wifi.
        if (state.failed) {
            Text(
                stringResource(R.string.mytime_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
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
        if (detail == null || detail.totalMs == 0L) {
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

        if (detail.apps.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mytime_apps), style = MaterialTheme.typography.titleMedium)
                    // Already ranked by the core (parse_day_detail); take(8)
                    // keeps the ranking rather than re-sorting a subset of it.
                    detail.apps.take(8).forEach { app ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.label)
                            Text(
                                StatusText.duration(app.foregroundMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
