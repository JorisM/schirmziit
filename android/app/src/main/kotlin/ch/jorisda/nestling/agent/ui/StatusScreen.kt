package ch.jorisda.nestling.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.sync.SyncWorker

/**
 * What the child sees. States exactly what leaves the phone, because the
 * alternative — an app that watches quietly — is the thing this project exists
 * to be an alternative to.
 */
@Composable
fun StatusScreen(settings: AgentSettings, pendingHours: Int, hasPermission: Boolean) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nestling", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (hasPermission) {
                "Screen-time reporting is on."
            } else {
                "Usage access is off — nothing is being recorded."
            },
        )
        Text("Server: ${settings.baseUrl ?: "not paired"}")
        Text("Last sent: ${StatusText.lastSync(System.currentTimeMillis(), settings.lastSyncMillis)}")
        Text("Waiting to send: $pendingHours hour(s)")
        settings.lastError?.let { Text("Last error: $it", color = MaterialTheme.colorScheme.error) }

        val context = LocalContext.current
        Button(onClick = { SyncWorker.runNow(context) }) { Text("Send now") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("What is sent", style = MaterialTheme.typography.titleMedium)
        Text(
            "Which apps were in the foreground, for how long, per hour, plus how often " +
                "the phone was unlocked.",
        )
        Text("What is never sent", style = MaterialTheme.typography.titleMedium)
        Text("What you typed, what you looked at, your messages, or your location.")
    }
}
