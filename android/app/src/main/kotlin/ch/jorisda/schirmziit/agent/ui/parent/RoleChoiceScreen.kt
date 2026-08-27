package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.role.AppRole

/**
 * The first question the app asks. Deliberately not a settings toggle buried
 * later: what a phone is decides everything else it does — which login it asks
 * for, which credential it may hold, whether it measures anything at all.
 *
 * The same screen iOS shows (`ios/Sources/Views/RoleChoiceView.swift`), in
 * Compose. One flourish: the two choices arriving one after the other, so the
 * question reads before the answers do.
 */
@Composable
fun RoleChoiceScreen(onChoose: (AppRole) -> Unit) {
    val reduced = rememberReducedMotion()
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { shown = true }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.role_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RoleCard(
            glyph = "📊",
            title = stringResource(R.string.role_parent_title),
            body = stringResource(R.string.role_parent_body),
            shown = shown,
            index = 0,
            reduced = reduced,
            onClick = { onChoose(AppRole.Parent) },
        )
        RoleCard(
            glyph = "📱",
            title = stringResource(R.string.role_child_title),
            body = stringResource(R.string.role_child_body),
            shown = shown,
            index = 1,
            reduced = reduced,
            onClick = { onChoose(AppRole.Child) },
        )

        Text(
            stringResource(R.string.role_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleCard(
    glyph: String,
    title: String,
    body: String,
    shown: Boolean,
    index: Int,
    reduced: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spec(reduced, delayMillis = Motion.staggerDelay(index)),
        label = "role-card-entry",
    )
    // A reaction to every touch. Nothing on these screens flips state with no
    // transition.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.spec(reduced, durationMillis = Motion.FAST),
        label = "role-card-press",
    )

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().alpha(appearance).scale(press),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(glyph, style = MaterialTheme.typography.headlineSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}
