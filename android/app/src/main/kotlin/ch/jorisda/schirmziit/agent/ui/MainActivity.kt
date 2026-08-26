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
import ch.jorisda.schirmziit.agent.mytime.mergeMyTimeResult
import ch.jorisda.schirmziit.agent.mytime.myTimeLoadArgs
import ch.jorisda.schirmziit.agent.notify.OngoingNotice
import ch.jorisda.schirmziit.agent.pair.EnrollPayloadParser
import ch.jorisda.schirmziit.agent.pair.ParentSetup
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
        // Hoisted rather than built per load: this screen is designed around
        // repeated tapping (one day, then another), and a fresh OkHttpClient
        // per call would each own its own connection pool and thread pool.
        val httpClient = OkHttpClient()

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
                    // The day the child last tapped. Compared against on
                    // completion so a slow response for a day the child has
                    // since tapped away from can't overwrite a faster, newer
                    // one — the highlighted day and the numbers underneath it
                    // must always agree.
                    var pendingDay by remember { mutableStateOf<String?>(null) }
                    // True only while the day currently pending has not yet
                    // landed. Kept apart from `myTime` on purpose: an earlier
                    // version faked an empty `MyTime` while loading, which
                    // `MyTimeScreen` then rendered as "nothing recorded" —
                    // exactly the silent-zero lie the failed-state guard
                    // exists to prevent, just arriving through latency instead
                    // of a dropped connection.
                    var myTimeLoading by remember { mutableStateOf(false) }
                    // True only while the most recent load failed. Kept apart
                    // from `myTime` for the same reason as iOS: a failure must
                    // add an error line beside the previous numbers, never
                    // replace them — `mergeMyTimeResult` is what enforces that.
                    var myTimeError by remember { mutableStateOf(false) }

                    fun loadMyTime(selected: String) {
                        val token = settings.deviceToken ?: return
                        val baseUrl = settings.baseUrl ?: return
                        pendingDay = selected
                        myTimeLoading = true
                        // Fixed [today-13, today] window (anchored to today, never to
                        // whichever day was tapped — the earlier bug let the window
                        // slide with every tap) and the strip already on screen reused
                        // rather than re-fetched: picking a day is the one request that
                        // tap is allowed to cost. Read here, on the composition thread,
                        // not inside the IO block below.
                        val args = myTimeLoadArgs(java.time.LocalDate.now(), myTime?.days)
                        scope.launch {
                            // The client does network work and Room forbids
                            // main-thread access; both belong off-thread, and
                            // the repository never throws, so this is safe even
                            // offline — it comes back with failed = true.
                            val result = withContext(Dispatchers.IO) {
                                val client = SchirmziitClient(baseUrl, httpClient)
                                val bridge = CoreBridge()
                                MyTimeRepository(
                                    fetch = { from, to, bucket, tz ->
                                        client.myUsage(token, from, to, bucket, tz)
                                    },
                                    parseStrip = bridge::dayStrip,
                                    parseDetail = bridge::dayDetail,
                                ).load(
                                    selected,
                                    from = args.from,
                                    days = args.days,
                                    tz = java.util.TimeZone.getDefault().id,
                                )
                            }
                            // A tap for a different day may have landed while
                            // this one was in flight; only the response for
                            // the day still pending is allowed to win, and
                            // only it may clear the loading flag.
                            if (pendingDay == selected) {
                                val merged = mergeMyTimeResult(myTime, result)
                                myTime = merged.myTime
                                myTimeError = merged.error
                                myTimeLoading = false
                            }
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
                            error = myTimeError,
                            loading = myTimeLoading,
                            onSelectDay = ::loadMyTime,
                            // Re-issues the load for the day that was pending when
                            // it failed — the same day whose numbers, if any, are
                            // still on screen underneath the error line.
                            onRetry = { pendingDay?.let(::loadMyTime) },
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
                            backgroundGranted = state.backgroundGranted,
                            backgroundCardDismissed = state.backgroundCardDismissed,
                            onAllowBackgroundListening = {
                                startActivity(
                                    android.content.Intent(
                                        android.provider.Settings
                                            .ACTION_NOTIFICATION_LISTENER_SETTINGS,
                                    ),
                                )
                            },
                            onDismissBackgroundCard = {
                                settings.backgroundCardDismissed = true
                                refresh()
                            },
                            // Off the composition thread: this signs in and out
                            // against the server, and Compose's thread may not
                            // do network work.
                            onUnpair = { email, password ->
                                val base = settings.baseUrl
                                if (base == null) {
                                    // Unreachable in practice: this screen only
                                    // renders when isPaired, which requires a
                                    // base URL. Handled rather than asserted so
                                    // the worst case is a phone that lands back
                                    // on pairing, not one that crashes there.
                                    settings.unpair()
                                    ParentSetup.Unpair.Done
                                } else {
                                    withContext(Dispatchers.IO) {
                                        ParentSetup(
                                            SchirmziitClient(base, httpClient),
                                            settings,
                                        ).unpair(email, password)
                                    }
                                }
                            },
                            onUnpaired = {
                                // The ongoing notice claims this phone is
                                // reporting; it has to go before the screen
                                // does, or it outlives the pairing it describes.
                                OngoingNotice.update(this@MainActivity, settings)
                                refresh()
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
