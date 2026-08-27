package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.Purged
import ch.jorisda.schirmziit.agent.parent.PurgeState

/**
 * Deletes a child's stored figures — the Android half of the dashboard's
 * `PurgeData` and of iOS's `PurgeDataView`.
 *
 * The privacy page and all three help screens promise this. Until now it existed
 * on the phone as an API route only, which makes the promise true for whoever
 * can run curl and for nobody else.
 *
 * Two presses, not one. The control sits at the foot of a screen a parent opens
 * daily, under numbers they came to read, and a single tap there is one mis-tap
 * away from an irreversible deletion. The question is asked in place rather than
 * in a dialog so the sentence explaining what will happen stays under the
 * heading naming whose figures they are.
 *
 * **No flourish here on purpose.** `ChildDetailScreen`'s one flourish is the
 * ribbon fill. This card gets entry motion on the counts and buttons that
 * press, and that is all — and the *failure* path gets no motion of its own: an
 * interface that animates a failure is enjoying itself at the parent's expense.
 */
@Composable
fun PurgeDataCard(
    state: PurgeState,
    reduced: Boolean,
    onAsk: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.data_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.data_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.purged?.let { Receipt(purged = it, reduced = reduced) }

            state.failure?.let { failure ->
                // Inline, never a banner: a banner sits over data that is still
                // good, and there is no purge on screen for this to be stale
                // beside. `onRetry` is deliberately absent — the confirm button
                // below is the retry, and two controls meaning the same thing
                // inside one question is a worse question.
                ErrorPanel(failure = failure, placement = ErrorPlacement.Inline, onRetry = null)
            }

            if (state.asking) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.data_delete_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onConfirm,
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(
                                stringResource(
                                    if (state.busy) R.string.data_delete_working else R.string.data_delete_confirm,
                                ),
                            )
                        }
                        TextButton(onClick = onCancel, enabled = !state.busy) {
                            Text(stringResource(R.string.app_cancel))
                        }
                    }
                }
            } else {
                // Quiet, not red. The loud one is the confirm inside the
                // question — a standing red control at the foot of a screen a
                // parent opens daily teaches them to ignore the colour that
                // means something is about to be irreversible. The dashboard's
                // `DestructiveAction` makes the same split.
                TextButton(onClick = onAsk) {
                    Text(
                        stringResource(R.string.data_delete),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What actually went, in the server's own numbers.
 *
 * Three counts rather than one sentence, and shown even when they are all zero:
 * a family whose phone has not reported yet needs to be able to tell a purge
 * that worked from one that found nothing.
 */
@Composable
private fun Receipt(purged: Purged, reduced: Boolean) {
    var shown by remember(purged) { mutableStateOf(reduced) }
    LaunchedEffect(purged) { shown = true }
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spec(reduced),
        label = "purge-receipt",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(fade)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.data_deleted),
            style = MaterialTheme.typography.titleSmall,
            // Announced when it arrives: the parent pressed a button and the
            // only proof it worked is this block.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Count(R.string.data_deleted_hours, purged.usageHours)
            Count(R.string.data_deleted_device_hours, purged.deviceHours)
            Count(R.string.data_deleted_days, purged.usageDays)
        }
    }
}

@Composable
private fun Count(label: Int, value: Long) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}
