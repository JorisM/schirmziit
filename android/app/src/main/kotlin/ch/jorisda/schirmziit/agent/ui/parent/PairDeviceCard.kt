package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.PairingState
import ch.jorisda.schirmziit.agent.parent.enrollmentExpired
import ch.jorisda.schirmziit.agent.parent.enrollmentServerAddress
import ch.jorisda.schirmziit.agent.ui.StatusText

/**
 * Mints the one-shot code a child's phone is enrolled with — the Android half of
 * the dashboard's `PairDevice` and of iOS's `PairDeviceView`.
 *
 * Minted on press, never on appearance: a code lives fifteen minutes and can be
 * claimed once, so a card that mints when a parent opens the screen hands out —
 * and burns — a code nobody asked for.
 *
 * The server address is shown next to the code because that is the half of the
 * pairing whose failure is silent: a phone enrolled against the wrong host
 * enrols exactly once and then never reports again.
 *
 * **No flourish here on purpose.** `ChildDetailScreen`'s one flourish is the
 * ribbon fill; this card gets entry motion and a button that presses, and that
 * is all. Two flourishes on one screen compete and both lose.
 *
 * `nowMillis` is a parameter rather than a `System.currentTimeMillis()` call in
 * the body so the expired card has a golden: the clock is the only thing that
 * decides which of the two lines below the code is shown.
 */
@Composable
fun PairDeviceCard(
    state: PairingState,
    nowMillis: Long,
    onMint: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.devices_pair_title),
                style = MaterialTheme.typography.titleMedium,
            )

            state.failure?.let { failure ->
                // A failed mint keeps the code that is already on screen and says
                // the *new* one did not arrive: the old one may well still be
                // valid, and blanking it takes away the only thing the parent can
                // act on.
                ErrorPanel(
                    failure = failure,
                    placement = if (state.enrollment == null) {
                        ErrorPlacement.Inline
                    } else {
                        ErrorPlacement.Banner
                    },
                    onRetry = onMint,
                )
            }

            state.enrollment?.let { enrollment ->
                val expired = enrollmentExpired(enrollment.expiresAtMillis, nowMillis)

                // Numbered, not three stacked sentences: unnumbered they run
                // together as one paragraph, and this is an errand a parent does
                // while walking to another room with a phone in hand.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Step(1, stringResource(R.string.devices_pair_step1))
                    Step(2, stringResource(R.string.devices_pair_step2))
                    Step(3, stringResource(R.string.devices_pair_step3))
                }

                // Only when the server drew one: the code and the address below
                // are the whole pairing on their own, so a square that could not
                // be drawn costs a scan and nothing else. An empty frame here
                // would read as a broken card instead.
                enrollment.qr?.let { matrix ->
                    QrMatrixImage(
                        matrix = matrix,
                        description = stringResource(R.string.devices_pair_qr),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.devices_pair_code),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Tracked wide and monospaced: these six characters get read
                    // out loud and typed on another phone one at a time.
                    Text(
                        enrollment.code,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        letterSpacing = 4.sp,
                        // An expired code is still worth showing — it is what the
                        // parent has half-typed on the other phone — but it must
                        // stop being the loudest thing here, or the line under it
                        // is arguing with it.
                        color = if (expired) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.devices_pair_server),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(enrollmentServerAddress(enrollment.qrPayload))
                    Text(
                        stringResource(R.string.devices_pair_server_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (expired) {
                    // Not a styling variant of the same line: a code shown as
                    // usable after it expired sends a parent to a phone that
                    // will refuse it.
                    Text(
                        stringResource(R.string.devices_pair_expired),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        "${stringResource(R.string.devices_pair_expires)} " +
                            StatusText.timeOfDay(enrollment.expiresAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(onClick = onMint, enabled = !state.busy) {
                Text(
                    stringResource(
                        when {
                            state.busy -> R.string.devices_pair_working
                            state.enrollment == null -> R.string.devices_pair_create
                            else -> R.string.devices_pair_new
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
