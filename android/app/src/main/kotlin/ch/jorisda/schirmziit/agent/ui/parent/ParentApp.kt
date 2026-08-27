package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ch.jorisda.schirmziit.agent.core.CoreBridge
import ch.jorisda.schirmziit.agent.parent.ApiFailure
import ch.jorisda.schirmziit.agent.parent.ChildDayState
import ch.jorisda.schirmziit.agent.parent.ChildrenState
import ch.jorisda.schirmziit.agent.parent.DayLoaded
import ch.jorisda.schirmziit.agent.parent.PairingState
import ch.jorisda.schirmziit.agent.parent.ParentChild
import ch.jorisda.schirmziit.agent.parent.ParentClient
import ch.jorisda.schirmziit.agent.parent.ParentSessionStore
import ch.jorisda.schirmziit.agent.parent.mergeChildren
import ch.jorisda.schirmziit.agent.parent.mergeDay
import ch.jorisda.schirmziit.agent.parent.mergeEnrollment
import ch.jorisda.schirmziit.agent.parent.mergeStrip
import ch.jorisda.schirmziit.agent.parent.refreshDay
import ch.jorisda.schirmziit.agent.parent.selectDay
import ch.jorisda.schirmziit.agent.parent.stripWindow
import ch.jorisda.schirmziit.agent.parent.validateChildName
import ch.jorisda.schirmziit.core.ErrorCode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The parent role, from sign-in to one child's day.
 *
 * Everything decidable lives in `agent.parent` as plain functions with tests
 * (`ParentUiStateTest`); this only wires them to the network and to Compose. The
 * split is the same one `MyTimeRepository`/`mergeMyTimeResult` made on the child
 * side, and for the same reason: the rules about never losing a day are worth
 * asserting without a server or an emulator.
 */
@Composable
fun ParentApp(
    session: ParentSessionStore,
    httpClient: OkHttpClient,
    onSignedOut: () -> Unit,
    onLeaveRole: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bridge = remember { CoreBridge() }

    // null while the stored cookie is still being checked. Distinct from false:
    // showing the sign-in form for a moment to a parent who *is* signed in is a
    // flash of the wrong screen on every cold start.
    var signedIn by remember { mutableStateOf<Boolean?>(null) }
    var children by remember { mutableStateOf(ChildrenState()) }
    var openChild by remember { mutableStateOf<ParentChild?>(null) }
    var day by remember { mutableStateOf<ChildDayState?>(null) }
    var pairing by remember { mutableStateOf(PairingState()) }
    var helpOpen by remember { mutableStateOf(false) }

    fun client(): ParentClient? =
        session.baseUrl?.let { ParentClient(it, httpClient, session) }

    suspend fun loadChildren() {
        val api = client() ?: return
        val loaded = withContext(Dispatchers.IO) { runCatching { api.children() } }
        children = mergeChildren(children, loaded)
    }

    /** One day: the hour ribbon, the apps, and the phones reporting. */
    fun loadDay(child: ParentChild, selected: String) {
        val api = client() ?: return
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val body = api.usage(child.id, selected, selected, "hour")
                    // Parsed by the core, not here: the same `parseDayDetail`
                    // the child's own screen uses, so a captcha page throws
                    // instead of reading as an empty day — and a parent and a
                    // child can never be shown different totals.
                    DayLoaded(bridge.dayDetail(body), ParentClient.devices(body))
                }
            }
            day = day?.let { mergeDay(it, selected, loaded) }
        }
    }

    fun loadStrip(child: ParentChild) {
        val api = client() ?: return
        scope.launch {
            val (from, to) = stripWindow(LocalDate.now())
            val loaded = withContext(Dispatchers.IO) {
                runCatching { bridge.dayStrip(api.usage(child.id, from, to, "day")) }
            }
            day = day?.let { mergeStrip(it, loaded) }
        }
    }

    fun open(child: ParentChild) {
        val today = LocalDate.now().toString()
        openChild = child
        // A code minted for one child must not follow the parent to another.
        pairing = PairingState()
        day = ChildDayState(selected = today, pending = today)
        loadStrip(child)
        loadDay(child, today)
    }

    fun pick(selected: String) {
        val child = openChild ?: return
        day = day?.let { selectDay(it, selected) }
        loadDay(child, selected)
    }

    LaunchedEffect(Unit) {
        // A remembered server plus a live session means straight into the list;
        // anything else falls back to the form. Same check iOS makes on launch.
        val api = client()
        signedIn = api != null && withContext(Dispatchers.IO) { api.me() }
        if (signedIn == true) loadChildren()
    }

    val child = openChild
    val currentDay = day

    // Paper, not the default `surface`. `MainActivity`'s own Surface takes the
    // colour scheme's `surface`, which in this palette is the *card* colour — so
    // a Card drawn on it is invisible, as the first recording of the help screen
    // showed. The child agent's screens are laid out against that and are left
    // alone; the parent screens get the Paper page the palette was built for.
    ParentSurface {
        when {
        signedIn == null -> ChildRowsSkeleton()

        signedIn == false -> SignInScreen(
            onSignIn = { server, email, password ->
                withContext(Dispatchers.IO) {
                    // The address has to be usable before it is stored: a
                    // typo'd scheme would otherwise be remembered and every
                    // later request would fail for a reason the form already
                    // knew about.
                    if (!server.startsWith("http")) {
                        return@withContext ApiFailure.local(ErrorCode.BASE_URL_NOT_CONFIGURED)
                    }
                    runCatching { ParentClient(server, httpClient, session).signIn(email, password) }
                        .fold(
                            onSuccess = { null },
                            onFailure = { ApiFailure.of(it, "/v1/auth/login") },
                        )
                }
            },
            onSignedIn = {
                signedIn = true
                scope.launch { loadChildren() }
            },
            onBack = onLeaveRole,
        )

        helpOpen -> ParentHelpScreen(onBack = { helpOpen = false })

        child != null && currentDay != null -> ChildDetailScreen(
            child = child,
            state = currentDay,
            pairing = pairing,
            // Recomposed whenever the pairing state changes, which is what makes
            // a code that expired while the screen was open start saying so.
            nowMillis = System.currentTimeMillis(),
            onSelectDay = ::pick,
            onRetryDay = {
                day = currentDay.let(::refreshDay)
                loadDay(child, currentDay.selected)
            },
            onRetryStrip = { loadStrip(child) },
            onMintCode = {
                val api = client() ?: return@ChildDetailScreen
                pairing = pairing.copy(busy = true)
                scope.launch {
                    val minted = withContext(Dispatchers.IO) {
                        runCatching { api.mintEnrollment(child.id) }
                    }
                    pairing = mergeEnrollment(pairing, minted)
                }
            },
            onRevokeDevice = { device ->
                val api = client() ?: return@ChildDetailScreen
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching { api.revokeDevice(device.id) }
                    }
                    outcome.fold(
                        // A revoked phone drops out of the usage response, so
                        // re-reading the day is what removes the row: there is
                        // no local list to keep in step with the server.
                        onSuccess = {
                            day = currentDay.let(::refreshDay)
                            loadDay(child, currentDay.selected)
                        },
                        onFailure = {
                            day = mergeDay(
                                currentDay.let(::refreshDay),
                                currentDay.selected,
                                Result.failure(it),
                            )
                        },
                    )
                }
            },
            onBack = {
                openChild = null
                day = null
                // Today's totals moved on while the parent was inside a child.
                scope.launch { loadChildren() }
            },
        )

        else -> ChildrenScreen(
            state = children,
            onOpenChild = ::open,
            onAddChild = { name ->
                val api = client() ?: return@ChildrenScreen
                val trimmed = validateChildName(name)
                if (trimmed == null) {
                    // A request that was never sent must never read as a child
                    // that was created.
                    children = children.copy(failure = ApiFailure.local(ErrorCode.VALIDATION_FAILED))
                    return@ChildrenScreen
                }
                children = children.copy(busy = true)
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching { api.createChild(trimmed) }
                    }
                    // Re-read rather than append: the list carries today's total
                    // per child, and a locally-appended row would sit there at
                    // zero even for a child whose phone is already reporting.
                    if (outcome.isSuccess) {
                        loadChildren()
                    } else {
                        children = mergeChildren(children, Result.failure(outcome.exceptionOrNull()!!))
                    }
                }
            },
            onRemoveChild = { removed ->
                val api = client() ?: return@ChildrenScreen
                children = children.copy(busy = true)
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching { api.removeChild(removed.id) }
                    }
                    if (outcome.isSuccess) {
                        loadChildren()
                    } else {
                        // The row stays: a delete that failed must not leave the
                        // parent looking at a list the server does not agree with.
                        children = mergeChildren(children, Result.failure(outcome.exceptionOrNull()!!))
                    }
                }
            },
            onRetry = { scope.launch { loadChildren() } },
            onOpenHelp = { helpOpen = true },
            onSignOut = {
                scope.launch {
                    withContext(Dispatchers.IO) { client()?.signOut() }
                    signedIn = false
                    children = ChildrenState()
                    onSignedOut()
                }
            },
        )
        }
    }
}

@Composable
private fun ParentSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) { content() }
}
