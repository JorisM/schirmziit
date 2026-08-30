package ch.jorisda.schirmziit.agent.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.pair.ParentSetup
import ch.jorisda.schirmziit.agent.power.BatteryHint
import ch.jorisda.schirmziit.agent.store.AgentSettings
import kotlinx.coroutines.launch

/**
 * What the child sees. Structure mirrors the parent dashboard's help page — the
 * same two lists, the same words — so nobody has to take anybody's word for what
 * this app does.
 */
@Composable
fun StatusScreen(
    settings: AgentSettings,
    /**
     * "Last sent" is a distance from now, so a screenshot of this screen ages:
     * recorded against the wall clock it read "noch nie" one day and "vor 21
     * Stunden" the next, and the gate went red for everyone on a screen nobody
     * had touched. The tests pin it; the app passes the real clock.
     */
    nowMillis: () -> Long = System::currentTimeMillis,
    pendingHours: Int,
    hasPermission: Boolean,
    batteryHint: BatteryHint,
    onSendNow: () -> Unit,
    onAllowBackground: () -> Unit,
    onOpenMyTime: () -> Unit,
    backgroundGranted: Boolean = true,
    backgroundCardDismissed: Boolean = true,
    onAllowBackgroundListening: () -> Unit = {},
    onDismissBackgroundCard: () -> Unit = {},
    /**
     * Checked against the server, never locally: the password is the whole
     * guard, and a phone that could verify it offline could be tricked into
     * saying yes. The default exists so previews and screenshot tests can
     * render the entry point without a server behind it.
     */
    onUnpair: suspend (String, String) -> ParentSetup.Unpair = { _, _ ->
        ParentSetup.Unpair.Failed("")
    },
    onUnpaired: () -> Unit = {},
) {
    var helpOpen by remember { mutableStateOf(false) }
    var unpairOpen by remember { mutableStateOf(false) }

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
                            nowMillis(),
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

        FilledTonalButton(onClick = onOpenMyTime) {
            Text(stringResource(R.string.mytime_open))
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

        // Granting notification access happens in system settings, with this app
        // paused — so the only feedback the phone can give is on the way back.
        // Silently removing the ask card is not feedback: it looks identical to
        // a grant that failed, or to one this screen never noticed. Say it.
        if (backgroundGranted) {
            // A container/on-color PAIR, not a tint over whatever is behind:
            // an alpha-composited colour has no guaranteed contrast, and the
            // first recording of this card put an invisible check mark on a
            // block that read as another warning under the red battery one.
            // This is a confirmation; it must not look like an alert.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                // One line, not a card with a paragraph. The ask card above
                // already explained what this grant does; repeating it after
                // the answer only pushes the rest of the screen off the first
                // viewport. Stays visible rather than flashing once: this is a
                // status screen, and the child is entitled to keep seeing what
                // is measured about them.
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("✓")
                    Text(
                        stringResource(R.string.background_on_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Optional, and shown once. An extra grant that keeps asking is a nag,
        // and declining this one is a supported end state, not a broken setup.
        if (!backgroundGranted && !backgroundCardDismissed) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.background_card_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.background_card_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onAllowBackgroundListening) {
                            Text(stringResource(R.string.background_card_action))
                        }
                        TextButton(onClick = onDismissBackgroundCard) {
                            Text(stringResource(R.string.background_card_dismiss))
                        }
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
                Text(
                    stringResource(R.string.help_why_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.help_why), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.help_where), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.help_open), style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                // Whoever carries the phone should have somewhere to turn that
                // is not the person reading their screen time.
                Text(
                    stringResource(R.string.help_support_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.help_support), style = MaterialTheme.typography.bodyMedium)
                val supportUrl = stringResource(R.string.help_support_url)
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl)))
                    },
                ) {
                    Text(stringResource(R.string.help_support_link))
                }

                HorizontalDivider()
                // Inside the help section, not on the front of the screen: this
                // is a rare, deliberate act by a parent, and the page that
                // explains what the app does is the honest place to say how to
                // stop it. Nothing is hidden — it just is not a control the
                // child needs while reading their own numbers.
                TextButton(onClick = { unpairOpen = true }) {
                    Text(stringResource(R.string.unpair_show))
                }
            }
        }
    }

    if (unpairOpen) {
        UnpairDialog(
            onDismiss = { unpairOpen = false },
            onUnpair = onUnpair,
            onUnpaired = {
                unpairOpen = false
                onUnpaired()
            },
        )
    }
}

/**
 * Asks for the parent's password before this phone stops reporting.
 *
 * A confirm dialog alone would not do: the child holds this phone, so anything
 * they can tap through is not a guard. The password is checked by the server,
 * which is also what makes "wrong password" and "server unreachable" two
 * different answers here — the second must never look like the first, or a
 * parent retypes a correct password at a server that is simply down.
 */
// internal, not private: the screenshot test renders it directly. `shoot`
// captures a composable with no compose rule behind it, so there is no way to
// tap the help toggle and then the entry point — and this dialog carries the
// longest translated sentence in the app, which is exactly what those images
// exist to catch.
@Composable
internal fun UnpairDialog(
    onDismiss: () -> Unit,
    onUnpair: suspend (String, String) -> ParentSetup.Unpair,
    onUnpaired: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val wrongLabel = stringResource(R.string.pair_parent_wrong)
    val failedLabel = stringResource(R.string.pair_failed)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.unpair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.unpair_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.pair_email)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.pair_password)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        when (val result = onUnpair(email.trim(), password)) {
                            is ParentSetup.Unpair.Done -> onUnpaired()
                            is ParentSetup.Unpair.WrongCredentials -> {
                                error = wrongLabel
                                busy = false
                            }
                            is ParentSetup.Unpair.Failed -> {
                                error = failedLabel.format(result.message)
                                busy = false
                            }
                        }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (busy) R.string.pair_working else R.string.unpair_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.unpair_cancel))
            }
        },
    )
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
