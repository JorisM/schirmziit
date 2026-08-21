package ch.jorisda.nestling.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ch.jorisda.nestling.agent.notify.OngoingNotice
import ch.jorisda.nestling.agent.pair.EnrollPayloadParser
import ch.jorisda.nestling.agent.pair.PairingScreen
import ch.jorisda.nestling.agent.store.AgentDatabase
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.store.AgentStore
import ch.jorisda.nestling.agent.sync.SyncWorker
import ch.jorisda.nestling.agent.usage.AndroidUsageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = AndroidUsageSource(this)
        // Arrives when the QR is opened from any scanner, or via
        // `adb shell am start -d "nestling://enroll?url=..&code=.."`.
        val link = intent?.data?.toString()
        val deepLink = link?.let(EnrollPayloadParser::parse)
        // nestling://sync forces one sync now; used by the acceptance harness
        // instead of waiting out the 30-minute cadence.
        if (link?.startsWith("nestling://sync") == true) SyncWorker.runNow(this)
        val settings: AgentSettings? = runCatching { AgentStore(this) }.getOrNull()

        setContent {
            MaterialTheme {
                if (settings == null) {
                    Text("Secure storage is unavailable on this device.")
                    return@MaterialTheme
                }

                var permitted by remember { mutableStateOf(source.hasPermission()) }
                var paired by remember { mutableStateOf(settings.isPaired) }
                // Room forbids main-thread access and enforces it with a
                // crash, so the queue depth is loaded off-thread.
                val pending by produceState(initialValue = 0) {
                    value = withContext(Dispatchers.IO) {
                        AgentDatabase.get(applicationContext).queue().pendingCount()
                    }
                }

                when {
                    !permitted -> PermissionScreen(onGranted = { permitted = source.hasPermission() })
                    !paired -> PairingScreen(settings, deepLink) {
                        paired = true
                        OngoingNotice.update(this, settings)
                    }
                    else -> StatusScreen(settings, pending, permitted)
                }
            }
        }
    }
}
