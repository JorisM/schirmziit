package ch.jorisda.schirmziit.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.mytime.MyTime
import ch.jorisda.schirmziit.agent.mytime.MyTimeRepository
import ch.jorisda.schirmziit.agent.notify.OngoingNotice
import ch.jorisda.schirmziit.agent.pair.EnrollPayloadParser
import ch.jorisda.schirmziit.agent.pair.PairingScreen
import ch.jorisda.schirmziit.agent.power.AndroidPowerStatus
import ch.jorisda.schirmziit.agent.store.AgentDatabase
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.store.AgentStore
import ch.jorisda.schirmziit.agent.sync.SchirmziitClient
import ch.jorisda.schirmziit.agent.sync.SyncWorker
import ch.jorisda.schirmziit.agent.ui.theme.SchirmziitTheme
import ch.jorisda.schirmziit.agent.usage.AndroidUsageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = AndroidUsageSource(this)
        val power = AndroidPowerStatus(this)
        val settings: AgentSettings? = runCatching { AgentStore(this) }.getOrNull()

        val link = intent?.data?.toString()
        val deepLink = link?.let(EnrollPayloadParser::parse)
        // schirmziit://sync forces one sync now; used by the acceptance harness
        // instead of waiting out the 30-minute cadence.
        if (link?.startsWith("schirmziit://sync") == true) SyncWorker.runNow(this)

        setContent {
            SchirmziitTheme {
                // fillMaxSize: without it the window background shows through
                // below the content as a pale band on a dark theme.
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (settings == null) {
                        Text(stringResource(R.string.status_off))
                        return@Surface
                    }

                    val scope = rememberCoroutineScope()
                    var state by remember {
                        mutableStateOf(
                            AgentUiState.read(source, power, settings, 0, System.currentTimeMillis()),
                        )
                    }

                    fun refresh() {
                        scope.launch {
                            // Room forbids main-thread access and enforces it
                            // with a crash, so the queue depth is read off-thread.
                            val pending = withContext(Dispatchers.IO) {
                                AgentDatabase.get(applicationContext).queue().pendingCount()
                            }
                            state = AgentUiState.read(
                                source,
                                power,
                                settings,
                                pending,
                                System.currentTimeMillis(),
                            )
                        }
                    }

                    // Both the usage permission and the battery exemption are
                    // granted in system settings, which pauses us. Re-reading on
                    // resume is what makes the prompt disappear once it is done.
                    OnResume(::refresh)

                    var showMyTime by remember { mutableStateOf(false) }
                    var myTime by remember { mutableStateOf<MyTime?>(null) }

                    fun loadMyTime(selected: String) {
                        val token = settings.deviceToken ?: return
                        val baseUrl = settings.baseUrl ?: return
                        scope.launch {
                            // The client does network work and Room forbids
                            // main-thread access; both belong off-thread, and
                            // the repository never throws, so this is safe even
                            // offline — it comes back with failed = true.
                            val result = withContext(Dispatchers.IO) {
                                val client = SchirmziitClient(baseUrl, OkHttpClient())
                                val bridge = CoreBridge()
                                MyTimeRepository(
                                    fetch = { from, to, bucket, tz ->
                                        client.myUsage(token, from, to, bucket, tz)
                                    },
                                    parseStrip = bridge::dayStrip,
                                    parseDetail = bridge::dayDetail,
                                ).load(selected, tz = java.util.TimeZone.getDefault().id)
                            }
                            myTime = result
                        }
                    }

                    when {
                        !state.hasPermission -> PermissionScreen(onGranted = ::refresh)

                        !state.isPaired -> PairingScreen(settings, deepLink) {
                            OngoingNotice.update(this@MainActivity, settings)
                            refresh()
                        }

                        showMyTime -> MyTimeScreen(
                            state = myTime ?: MyTime(
                                emptyList(),
                                null,
                                java.time.LocalDate.now().toString(),
                                failed = false,
                            ),
                            onSelectDay = ::loadMyTime,
                            onBack = { showMyTime = false },
                        )

                        else -> StatusScreen(
                            settings = settings,
                            pendingHours = state.pendingHours,
                            hasPermission = state.hasPermission,
                            batteryHint = state.batteryHint,
                            onSendNow = {
                                SyncWorker.runNow(this@MainActivity)
                                refresh()
                            },
                            onAllowBackground = { power.requestExemption(this@MainActivity) },
                            onOpenMyTime = {
                                showMyTime = true
                                loadMyTime(java.time.LocalDate.now().toString())
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Runs `block` on every ON_RESUME, including the first one. */
@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
