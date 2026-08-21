package ch.jorisda.nestling.agent.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * PACKAGE_USAGE_STATS is an AppOps grant, not a runtime permission: there is no
 * requestPermissions() path. The only route is this Settings screen, after which
 * MainActivity re-checks the op.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Allow usage access", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Nestling needs Android's \"Usage access\" permission to see how long apps " +
                "are used. Android only lets you grant this in Settings.",
        )
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text("Open Settings")
        }
        Button(onClick = onGranted) { Text("I've granted it") }
    }
}
