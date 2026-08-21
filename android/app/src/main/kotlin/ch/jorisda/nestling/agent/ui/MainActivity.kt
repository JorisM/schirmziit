package ch.jorisda.nestling.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.jorisda.nestling.agent.notify.OngoingNotice
import ch.jorisda.nestling.agent.pair.PairingScreen
import ch.jorisda.nestling.agent.store.AgentDatabase
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.store.AgentStore
import ch.jorisda.nestling.agent.usage.AndroidUsageSource

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = AndroidUsageSource(this)
        val settings: AgentSettings? = runCatching { AgentStore(this) }.getOrNull()

        setContent {
            MaterialTheme {
                if (settings == null) {
                    Text("Secure storage is unavailable on this device.")
                    return@MaterialTheme
                }

                var permitted by remember { mutableStateOf(source.hasPermission()) }
                var paired by remember { mutableStateOf(settings.isPaired) }
                val pending by remember {
                    mutableIntStateOf(AgentDatabase.get(this).queue().pendingCount())
                }

                when {
                    !permitted -> PermissionScreen(onGranted = { permitted = source.hasPermission() })
                    !paired -> PairingScreen(settings) {
                        paired = true
                        OngoingNotice.update(this, settings)
                    }
                    else -> StatusScreen(settings, pending, permitted)
                }
            }
        }
    }
}
