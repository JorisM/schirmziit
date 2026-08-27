package ch.jorisda.schirmziit.agent.ui.parent

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R

/**
 * What Schirmziit measures and what it never collects, for the parent.
 *
 * The same two lists as the dashboard, the child's status screen and iOS, in the
 * same words: measured and not-collected sit next to each other because a
 * promise is only credible beside its limits.
 *
 * The four Swiss services at the foot are the parent-facing addition — the same
 * list the dashboard dictionaries (`web/src/i18n/de.ts` and its siblings)
 * carry, so the app and the browser agree. Schirmziit
 * shows numbers, not advice; those four are better at the advice than we are.
 */
@Composable
fun ParentHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.help_close)) }
        Text(stringResource(R.string.parenthelp_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.parenthelp_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MarkedList(
            title = stringResource(R.string.help_sends_title),
            marker = "✓",
            tint = MaterialTheme.colorScheme.primary,
            lines = listOf(
                stringResource(R.string.help_sends_1),
                stringResource(R.string.help_sends_2),
                stringResource(R.string.help_sends_3),
            ),
        )
        MarkedList(
            title = stringResource(R.string.help_never_title),
            marker = "✕",
            tint = MaterialTheme.colorScheme.error,
            lines = listOf(
                stringResource(R.string.help_never_1),
                stringResource(R.string.help_never_2),
                stringResource(R.string.help_never_3),
                stringResource(R.string.help_never_4),
                stringResource(R.string.help_never_5),
            ),
        )

        Section(stringResource(R.string.parenthelp_where_title), stringResource(R.string.parenthelp_where))
        Section(
            stringResource(R.string.parenthelp_retention_title),
            stringResource(R.string.parenthelp_retention),
        )
        Section(
            stringResource(R.string.parenthelp_notacontrol_title),
            stringResource(R.string.parenthelp_notacontrol),
        )

        HorizontalDivider()

        Text(
            stringResource(R.string.parenthelp_resources_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.parenthelp_resources_lead),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            R.string.parenthelp_resource_jum_name to
                (R.string.parenthelp_resource_jum_note to R.string.parenthelp_resource_jum_url),
            R.string.parenthelp_resource_pj_name to
                (R.string.parenthelp_resource_pj_note to R.string.parenthelp_resource_pj_url),
            R.string.parenthelp_resource_147_name to
                (R.string.parenthelp_resource_147_note to R.string.parenthelp_resource_147_url),
            R.string.parenthelp_resource_zischtig_name to
                (R.string.parenthelp_resource_zischtig_note to R.string.parenthelp_resource_zischtig_url),
        ).forEach { (name, rest) ->
            val (note, url) = rest
            val href = stringResource(url)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(href)))
                        },
                    ) { Text(stringResource(R.string.parenthelp_resource_open)) }
                }
            }
        }

        Text(
            stringResource(R.string.parenthelp_swiss),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MarkedList(title: String, marker: String, tint: Color, lines: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(marker, color = tint)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
