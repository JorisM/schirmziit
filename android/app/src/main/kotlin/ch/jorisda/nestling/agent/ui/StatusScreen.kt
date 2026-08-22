package ch.jorisda.nestling.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jorisda.nestling.agent.R
import ch.jorisda.nestling.agent.power.BatteryHint
import ch.jorisda.nestling.agent.store.AgentSettings

/**
 * What the child sees. Structure mirrors the parent dashboard's help page — the
 * same two lists, the same words — so nobody has to take anybody's word for what
 * this app does.
 */
@Composable
fun StatusScreen(
    settings: AgentSettings,
    pendingHours: Int,
    hasPermission: Boolean,
    batteryHint: BatteryHint,
    onSendNow: () -> Unit,
    onAllowBackground: () -> Unit,
) {
    var helpOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            // safeDrawingPadding first: without it the header sits under the
            // status bar and the last help line under the navigation bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The headline states the one fact that matters, in colour and in words.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        if (hasPermission) R.string.status_reporting else R.string.status_off,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.status_last_sent),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        StatusText.lastSync(
                            LocalContext.current,
                            System.currentTimeMillis(),
                            settings.lastSyncMillis,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.status_waiting),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.status_hours, pendingHours))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.status_server),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(settings.baseUrl ?: stringResource(R.string.status_not_paired))
                }
                settings.lastError?.let { problem ->
                    Text(
                        "${stringResource(R.string.status_last_error)}: $problem",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(onClick = onSendNow, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.status_send_now))
                }
            }
        }

        // Escalates only once syncs are actually being missed; a standing banner
        // on a phone that reports fine teaches people to ignore banners.
        if (batteryHint != BatteryHint.None) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (batteryHint == BatteryHint.Urgent) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.battery_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.battery_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = onAllowBackground) {
                        Text(stringResource(R.string.battery_action))
                    }
                }
            }
        }

        TextButton(onClick = { helpOpen = !helpOpen }) {
            Text(stringResource(if (helpOpen) R.string.help_hide else R.string.help_show))
        }

        AnimatedVisibility(visible = helpOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpList(
                    title = stringResource(R.string.help_sends_title),
                    marker = "✓",
                    tint = MaterialTheme.colorScheme.primary,
                    lines = listOf(
                        stringResource(R.string.help_sends_1),
                        stringResource(R.string.help_sends_2),
                        stringResource(R.string.help_sends_3),
                    ),
                )
                HelpList(
                    title = stringResource(R.string.help_never_title),
                    marker = "✕",
                    tint = MaterialTheme.colorScheme.error,
                    lines = listOf(
                        stringResource(R.string.help_never_1),
                        stringResource(R.string.help_never_2),
                        stringResource(R.string.help_never_3),
                        stringResource(R.string.help_never_4),
                        stringResource(R.string.help_never_5),
                    ),
                )
                HorizontalDivider()
                Text(stringResource(R.string.help_where), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.help_open), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HelpList(title: String, marker: String, tint: Color, lines: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            lines.forEach { line ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(marker, color = tint)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
