package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.ApiFailure
import ch.jorisda.schirmziit.agent.parent.ChildrenState
import ch.jorisda.schirmziit.agent.parent.ParentChild
import ch.jorisda.schirmziit.agent.ui.StatusText

/**
 * The family, and how long each child's phone was used today.
 *
 * The screen a parent opens every day, so it is the one that most has to be a
 * pleasure to open: rows stagger in, today's total counts up, every touch
 * answers. The flourish is the count-up — one per screen, and this is it.
 *
 * Mirrors `ios/Sources/Views/ChildrenView.swift`, including the two rules that
 * cost real bugs there: a removal is a confirmation and never a bare gesture,
 * and a failed load keeps the list it could not refresh.
 */
@Composable
fun ChildrenScreen(
    state: ChildrenState,
    onOpenChild: (ParentChild) -> Unit,
    onAddChild: (String) -> Unit,
    onRemoveChild: (ParentChild) -> Unit,
    onRetry: () -> Unit,
    onOpenHelp: () -> Unit,
    onSignOut: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    // The child a tap has proposed removing. Optional rather than a bool beside
    // an id: the dialog names the child it is about, and a bool can go true
    // while the id it belongs to is stale.
    var pendingRemoval by remember { mutableStateOf<ParentChild?>(null) }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Title on its own line and the two actions under it, rather than all
        // three across one row: "Wie das funktioniert" next to "Abmelden" next
        // to a headline already fills 411 dp in German, and French and Italian
        // are longer still.
        Text(stringResource(R.string.children_title), style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpenHelp) { Text(stringResource(R.string.app_help)) }
            TextButton(onClick = onSignOut) { Text(stringResource(R.string.app_signout)) }
        }

        val children = state.children
        state.failure?.let { failure ->
            // A refresh that failed with a list already on screen is a banner
            // over it, not a replacement for it.
            ErrorPanel(
                failure = failure,
                placement = if (children.isNullOrEmpty()) ErrorPlacement.Inline else ErrorPlacement.Banner,
                onRetry = onRetry,
            )
        }

        when {
            // Not asked yet. Distinct from an empty list on purpose: an empty
            // state under a first load invites a parent to add a child they
            // already have.
            children == null -> if (state.failure == null) ChildRowsSkeleton()

            children.isEmpty() -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.children_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.children_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The empty state carries the action it is asking for: a
                    // parent reading "add a child" should not have to go
                    // looking for where.
                    Button(onClick = { adding = true }, enabled = !state.busy) {
                        Text(stringResource(R.string.children_add))
                    }
                }
            }

            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                children.forEachIndexed { index, child ->
                    ChildRow(
                        child = child,
                        index = index,
                        reduced = reduced,
                        enabled = !state.busy,
                        onOpen = { onOpenChild(child) },
                        onRemove = { pendingRemoval = child },
                    )
                }
                // A long press is undiscoverable without being told, so it is
                // told — once, quietly, under the list. The alternative was a
                // standing Remove button per row, which the first screenshot
                // showed for what it is: the loudest thing on the screen a
                // parent opens every day, in red, next to their child's name.
                Text(
                    stringResource(R.string.children_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!children.isNullOrEmpty()) {
            TextButton(onClick = { adding = true }, enabled = !state.busy) {
                Text(stringResource(R.string.children_add))
            }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false; newName = "" },
            title = { Text(stringResource(R.string.children_add_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.children_add_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onAddChild(newName)
                        newName = ""
                        adding = false
                    },
                ) { Text(stringResource(R.string.children_add_save)) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false; newName = "" }) {
                    Text(stringResource(R.string.app_cancel))
                }
            },
        )
    }

    // Removing a child removes their devices too, on the server, in one
    // transaction — so the dialog says what it is about rather than only asking
    // whether the parent is sure.
    pendingRemoval?.let { child ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(child.displayName) },
            text = { Text(stringResource(R.string.children_remove_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRemoval = null
                        onRemoveChild(child)
                    },
                ) { Text(stringResource(R.string.children_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.app_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildRow(
    child: ParentChild,
    index: Int,
    reduced: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var shown by remember(child.id) { mutableStateOf(reduced) }
    LaunchedEffect(child.id) { shown = true }

    // List rows stagger ~40 ms apart. No data grid that simply appears.
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index)),
        label = "child-row-entry",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = Motion.spec(reduced, durationMillis = Motion.FAST),
        label = "child-row-press",
    )

    val removeLabel = stringResource(R.string.children_remove)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(appearance)
            .scale(press)
            // Tap opens the child; a long press proposes removing them. The
            // Android counterpart of the iOS swipe, and the same rule: the
            // gesture only ever opens the question, it never answers it.
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onLongClick = onRemove,
                onClick = onOpen,
            )
            // A gesture TalkBack cannot perform is a control it does not have,
            // so removal is also a named custom action on the row.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(removeLabel) { onRemove(); true },
                )
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(child.displayName, style = MaterialTheme.typography.titleMedium)
            Column(horizontalAlignment = Alignment.End) {
                CountingTotal(targetMs = child.todayMs, reduced = reduced)
                Text(
                    stringResource(R.string.children_today_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * Today's total, counting up from zero. The screen's one flourish.
 *
 * Motion never delays reading: the number is legible on the first frame, and
 * the last frame is *assigned* rather than interpolated — a total that stops one
 * millisecond short formats as the wrong duration.
 */
@Composable
private fun CountingTotal(targetMs: Long, reduced: Boolean) {
    var started by remember(targetMs) { mutableStateOf(false) }
    LaunchedEffect(targetMs) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = Motion.spec(reduced, durationMillis = Motion.HERO),
        label = "today-total",
    )

    val shown = if (progress >= 1f) targetMs else (targetMs * progress).toLong()
    Text(
        StatusText.duration(shown),
        style = MaterialTheme.typography.titleMedium,
    )
}
