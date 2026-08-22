package ch.jorisda.nestling.agent.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jorisda.nestling.agent.R

/**
 * PACKAGE_USAGE_STATS is an AppOps grant, not a runtime permission: there is no
 * requestPermissions() path. Settings is the only route, so the screen says so
 * rather than pretending a button could do it.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.safeDrawingPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.permission_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.permission_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text(stringResource(R.string.permission_open_settings))
        }
        OutlinedButton(onClick = onGranted) {
            Text(stringResource(R.string.permission_granted))
        }
    }
}
