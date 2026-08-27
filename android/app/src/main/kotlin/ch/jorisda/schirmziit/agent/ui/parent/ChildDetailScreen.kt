package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.mytime.splitApps
import ch.jorisda.schirmziit.agent.mytime.visibleApps
import ch.jorisda.schirmziit.agent.parent.ChildDayState
import ch.jorisda.schirmziit.agent.parent.ParentChild
import ch.jorisda.schirmziit.agent.parent.PairingState
import ch.jorisda.schirmziit.agent.parent.ParentDevice
import ch.jorisda.schirmziit.agent.ui.StatusText
import ch.jorisda.schirmziit.core.AppTotalFfi
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * One child: the fortnight, one day out of it hour by hour, the apps, the phones.
 *
 * The numbers all come from `crates/core` (`parseDayStrip`/`parseDayDetail`) —
 * the same two functions the child's own `MyTimeScreen` calls. That is
 * deliberate and stronger than iOS, which decodes the parent side by hand: a
 * parent and a child looking at the same day cannot be shown different totals if
 * only one function computes them.
 *
 * The screen's one flourish is the hour ribbon filling left to right — the day
 * passing. The background-listening figure sits beside the total as a number
 * only: a second animated measure would compete with the ribbon and both would
 * lose.
 */
@Composable
fun ChildDetailScreen(
    child: ParentChild,
    state: ChildDayState,
    pairing: PairingState,
    /**
     * Read once per composition and passed down, not called inside the pairing
     * card: it decides which of two lines sits under the code, and a golden of
     * the expired card needs that to be an input rather than the wall clock.
     */
    nowMillis: Long,
    onSelectDay: (String) -> Unit,
    onRetryDay: () -> Unit,
    onRetryStrip: () -> Unit,
    onMintCode: () -> Unit,
    onRevokeDevice: (ParentDevice) -> Unit,
    onBack: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    var pendingRevoke by remember { mutableStateOf<ParentDevice?>(null) }
    val today = remember { LocalDate.now().toString() }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) }
        Text(child.displayName, style = MaterialTheme.typography.headlineLarge)

        // Independent of the selected day: this section depends only on the
        // strip, so it stays on screen — selection outline included — while a
        // newly picked day's own sections skeleton below it.
        when {
            state.strip != null -> {
                // A refresh that failed leaves the fortnight where it is and
                // says it is stale. Blanking a loaded strip because a poll
                // failed loses a day at the presentation layer.
                state.stripFailure?.let {
                    ErrorPanel(failure = it, placement = ErrorPlacement.Banner, onRetry = onRetryStrip)
                }
                DayStrip(
                    days = state.strip.map { it.day to it.foregroundMs },
                    selectedDay = state.selected,
                    reduced = reduced,
                    onSelect = onSelectDay,
                )
            }
            // Never zero-fill in place of a failed fetch: fourteen quiet bars
            // read as a genuinely quiet fortnight, which is exactly the lost day
            // this app promises never to show.
            state.stripFailure != null -> ErrorPanel(
                failure = state.stripFailure,
                onRetry = onRetryStrip,
            )

            else -> StripSkeleton()
        }

        val detail = state.detail
        when {
            detail != null -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(
                                if (state.selected == today) R.string.child_total else R.string.child_selected,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            StatusText.duration(detail.totalMs),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            stringResource(R.string.child_unlocks, detail.unlockCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Its own line, its own colour, never inside the total
                        // above. `backgroundMeasured == false` says "this phone
                        // could not observe it", which is not a zero.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.child_background),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (detail.backgroundMeasured) {
                                Text(
                                    StatusText.duration(detail.backgroundMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.child_background_not_measured),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // A refresh of the selected day that failed: the numbers above
                // stay, this says they are stale.
                state.dayFailure?.let {
                    ErrorPanel(failure = it, placement = ErrorPlacement.Banner, onRetry = onRetryDay)
                }

                if (detail.totalMs == 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.child_nodata),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.child_nodata_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HourRibbon(hours = detail.hours, reduced = reduced, key = state.selected)

                if (detail.apps.isNotEmpty()) {
                    AppRows(
                        apps = detail.apps,
                        // A day nothing could observe background playback on has
                        // no per-app background figure to show either. The
                        // rows carry whatever the server sent, and a phone that
                        // reported for part of the day can leave a non-zero
                        // there while the day as a whole is unmeasured —
                        // rendering it would claim a measure the line above has
                        // just said was not taken.
                        backgroundMeasured = detail.backgroundMeasured,
                        reduced = reduced,
                    )
                }

                state.devices?.let { devices ->
                    Devices(
                        devices = devices,
                        onRevoke = { pendingRevoke = it },
                    )
                }

                // Under the devices list, deliberately: connecting a phone is
                // something a parent does having just looked at which phones are
                // already connected, and often because one of them is missing.
                PairDeviceCard(
                    state = pairing,
                    nowMillis = nowMillis,
                    onMint = onMintCode,
                )
            }

            // The day failed with nothing to fall back on.
            state.dayFailure != null -> ErrorPanel(failure = state.dayFailure, onRetry = onRetryDay)

            else -> DaySkeleton()
        }
    }

    // A phone that is disconnected cannot be reconnected without enrolling it
    // again, so the question is asked by name.
    pendingRevoke?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text(device.label) },
            text = { Text(stringResource(R.string.devices_revoke_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRevoke = null
                        onRevokeDevice(device)
                    },
                ) { Text(stringResource(R.string.devices_revoke_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text(stringResource(R.string.app_cancel))
                }
            },
        )
    }
}

/**
 * Fourteen days as bars. The ribbon answers "when in the day"; this answers "was
 * today unusual", which one day on its own cannot.
 */
@Composable
private fun DayStrip(
    days: List<Pair<String, Long>>,
    // Not `selected`: inside `semantics {}` a parameter of that name shadows the
    // `selected` semantics property, and the assignment below then targets a val.
    selectedDay: String,
    reduced: Boolean,
    onSelect: (String) -> Unit,
) {
    var grown by remember(days) { mutableStateOf(reduced) }
    LaunchedEffect(days) { grown = true }
    val busiest = days.maxOfOrNull { it.second } ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.child_history_title), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { index, (day, ms) ->
                val share = if (busiest > 0) ms.toFloat() / busiest else 0f
                // Read out here: inside `semantics {}` the `selected` property
                // shadows this function's `selected` parameter.
                val isSelected = day == selectedDay
                val interaction = remember(day) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val height by animateFloatAsState(
                    targetValue = if (grown) share else 0f,
                    animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index, cap = 14)),
                    label = "strip-bar",
                )
                val press by animateFloatAsState(
                    targetValue = if (pressed) 1.08f else 1f,
                    animationSpec = Motion.spec(reduced, durationMillis = Motion.FAST),
                    label = "strip-press",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(day) }
                        // One announcement per bar, in words: VoiceOver spelled
                        // the raw "2026-08-24" out digit by digit on iOS before
                        // this was fixed there, and TalkBack does the same.
                        .semantics(mergeDescendants = true) {
                            contentDescription = spokenDay(day)
                            stateDescription = StatusText.duration(ms)
                            selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        Modifier
                            // A floor, not a zero: an empty day is still a day,
                            // and a bar of no height reads as a hole.
                            .height((8 + 52 * height).dp)
                            .fillMaxWidth()
                            .scale(scaleX = 1f, scaleY = press)
                            .background(
                                if (ms > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(3.dp),
                            )
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                MaterialTheme.colorScheme.onSurface,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                    Text(
                        day.takeLast(2),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.child_history_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Monday 24 August", not "two zero two six dash zero eight dash two four". */
private fun spokenDay(day: String): String = runCatching {
    val date = LocalDate.parse(day)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    "$weekday ${date.dayOfMonth} $month"
}.getOrDefault(day)

/**
 * The day as 24 cells, midnight to midnight — and this screen's one flourish:
 * they fill left to right, which is the day passing.
 *
 * A bar chart answers "how much"; a parent's real question is "when" — an hour
 * at 23:00 means something different from an hour after lunch.
 */
@Composable
private fun HourRibbon(hours: List<Long>, reduced: Boolean, key: String) {
    var filled by remember(key) { mutableStateOf(reduced) }
    LaunchedEffect(key) { filled = true }
    var selected by remember(key) { mutableStateOf<Int?>(null) }
    val busiest = hours.maxOrNull() ?: 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.child_ribbon_title), style = MaterialTheme.typography.titleMedium)
            selected?.let { hour ->
                Text(
                    "%02d:00 · %s".format(hour, StatusText.duration(hours.getOrElse(hour) { 0L })),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEachIndexed { hour, ms ->
                val share = if (busiest > 0) ms.toFloat() / busiest else 0f
                val grow by animateFloatAsState(
                    targetValue = if (filled) 1f else 0f,
                    // The sweep IS the day passing: the whole flourish is spread
                    // across the hero budget rather than each cell taking it.
                    animationSpec = Motion.spec(
                        reduced,
                        durationMillis = Motion.BASE,
                        delayMillis = if (reduced) 0 else hour * Motion.HERO / 24,
                    ),
                    label = "ribbon-cell",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height((4 + 52 * share * grow).dp)
                        .alpha(grow)
                        .background(
                            if (ms > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(2.dp),
                        )
                        .border(
                            if (selected == hour) 2.dp else 0.dp,
                            MaterialTheme.colorScheme.onSurface,
                            RoundedCornerShape(2.dp),
                        )
                        .clickable { selected = if (selected == hour) null else hour }
                        .clearAndSetSemantics {
                            contentDescription = "%02d:00".format(hour)
                            stateDescription = StatusText.duration(ms)
                        },
                )
            }
        }

        // One weighted slot per hour, so a label sits under the hour it names.
        // `SpaceBetween` over four labels was the obvious thing and it is wrong:
        // it pushes "18" to the right edge, which is hour 23 — the first
        // screenshot of this screen showed an evening peak apparently starting
        // before noon.
        Row(Modifier.fillMaxWidth()) {
            (0 until 24).forEach { hour ->
                Box(Modifier.weight(1f)) {
                    if (hour % 6 == 0) {
                        Text(
                            "%02d".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            stringResource(R.string.child_ribbon_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ranked app rows, with the sub-minute glances folded away.
 *
 * `splitApps`/`visibleApps` are the child screen's own functions, reused rather
 * than reimplemented: a child and a parent must see the same numbers, and the
 * cap landing on `shown` alone — after the split — is the tested part.
 */
@Composable
private fun AppRows(apps: List<AppTotalFfi>, backgroundMeasured: Boolean, reduced: Boolean) {
    val visible = remember(apps) { visibleApps(splitApps(apps), cap = 8) }
    var briefExpanded by remember(apps) { mutableStateOf(false) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.child_apps), style = MaterialTheme.typography.titleMedium)
            visible.shown.forEachIndexed { index, app ->
                AppRow(app, index, backgroundMeasured, reduced)
            }
            if (visible.brief.isNotEmpty()) {
                TextButton(onClick = { briefExpanded = !briefExpanded }) {
                    Text("${stringResource(R.string.child_apps_brief)} (${visible.brief.size})")
                }
                if (briefExpanded) {
                    visible.brief.forEachIndexed { index, app ->
                        AppRow(app, visible.shown.size + index, backgroundMeasured, reduced)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppTotalFfi,
    index: Int,
    backgroundMeasured: Boolean,
    reduced: Boolean,
) {
    var shown by remember(app.`package`) { mutableStateOf(reduced) }
    LaunchedEffect(app.`package`) { shown = true }
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index)),
        label = "app-row",
    )

    Row(
        Modifier.fillMaxWidth().alpha(appearance),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(app.label)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Background listening, when it was measured at all and there is
            // any, next to the foreground figure and visibly not part of it.
            if (backgroundMeasured && app.backgroundMs > 0) {
                Text(
                    StatusText.duration(app.backgroundMs),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                StatusText.duration(app.foregroundMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The phones reporting for this child, and how to stop one of them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Devices(devices: List<ParentDevice>, onRevoke: (ParentDevice) -> Unit) {
    val revokeLabel = stringResource(R.string.devices_revoke)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.titleMedium)
            devices.forEach { device ->
                Column(
                    // A long press proposes disconnecting, as on the children
                    // list. A standing Disconnect button under every phone put
                    // the most destructive control on the screen in red, three
                    // times over, next to numbers a parent came to read.
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onLongClick = { onRevoke(device) }, onClick = {})
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(revokeLabel) { onRevoke(device); true },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (device.stale) "!" else "✓",
                                color = if (device.stale) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Text(device.label)
                        }
                        Text(
                            stringResource(
                                if (device.stale) R.string.devices_stale else R.string.devices_fresh,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (device.stale) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Text(
                        // "Never" is not "a long time ago": a phone that has
                        // never reported has a setup problem, not a sync one.
                        device.lastSeenAtMillis?.let { lastSeen ->
                            StatusText.lastSeen(lastSeen)
                        } ?: stringResource(R.string.devices_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.devices_revoke_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (devices.any { it.stale }) {
                Text(
                    stringResource(R.string.devices_stale_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
