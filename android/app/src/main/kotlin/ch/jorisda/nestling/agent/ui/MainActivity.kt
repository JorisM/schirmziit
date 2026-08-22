package ch.jorisda.nestling.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jorisda.nestling.agent.R
import ch.jorisda.nestling.agent.notify.OngoingNotice
import ch.jorisda.nestling.agent.pair.EnrollPayloadParser
import ch.jorisda.nestling.agent.pair.PairingScreen
import ch.jorisda.nestling.agent.power.AndroidPowerStatus
import ch.jorisda.nestling.agent.power.BatteryHint
import ch.jorisda.nestling.agent.store.AgentDatabase
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.store.AgentStore
import ch.jorisda.nestling.agent.sync.SyncWorker
import ch.jorisda.nestling.agent.ui.theme.NestlingTheme
import ch.jorisda.nestling.agent.usage.AndroidUsageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = AndroidUsageSource(this)
        val power = AndroidPowerStatus(this)
        val settings: AgentSettings? = runCatching { AgentStore(this) }.getOrNull()

        val link = intent?.data?.toString()
        val deepLink = link?.let(EnrollPayloadParser::parse)
        // nestling://sync forces one sync now; used by the acceptance harness
        // instead of waiting out the 30-minute cadence.
        if (link?.startsWith("nestling://sync") == true) SyncWorker.runNow(this)

        setContent {
            NestlingTheme {
                // fillMaxSize: without it the window background shows through
                // below the content as a pale band on a dark theme.
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (settings == null) {
                        Text(stringResource(R.string.status_off))
                        return@Surface
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
                        !permitted -> PermissionScreen(
                            onGranted = { permitted = source.hasPermission() },
                        )

                        !paired -> PairingScreen(settings, deepLink) {
                            paired = true
                            OngoingNotice.update(this@MainActivity, settings)
                        }

                        else -> StatusScreen(
                            settings = settings,
                            pendingHours = pending,
                            hasPermission = permitted,
                            batteryHint = BatteryHint.evaluate(
                                isIgnoringOptimisations = power.isIgnoringOptimisations(),
                                lastSyncMillis = settings.lastSyncMillis,
                                nowMillis = System.currentTimeMillis(),
                            ),
                            onSendNow = { SyncWorker.runNow(this@MainActivity) },
                            onAllowBackground = { power.requestExemption(this@MainActivity) },
                        )
                    }
                }
            }
        }
    }
}
