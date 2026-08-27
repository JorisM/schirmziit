package ch.jorisda.schirmziit.agent.ui.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.ApiFailure
import ch.jorisda.schirmziit.agent.parent.copyDetails
import ch.jorisda.schirmziit.agent.parent.errorCopyResource

/** Where an error sits relative to the data it is about. */
enum class ErrorPlacement {
    /** Replaces data that failed to load. */
    Inline,

    /**
     * Sits above data already on screen when a *refresh* failed. The numbers
     * stay, the banner says they are stale — blanking a loaded day because a
     * poll failed is the same mistake as losing a day, one layer up.
     */
    Banner,
}

/**
 * Every error a parent sees, in one composable.
 *
 * The copy comes from the generated `error_copy.xml` — one TOML in four
 * languages (`copy/errors.toml`), the same source the dashboard and iOS read.
 * Nothing here is hand-written: a sentence in this file would be a fifth version
 * of copy that already exists in four places. This is the first thing on Android
 * to read that table; the child agent still has its own hand-written lines.
 *
 * Entry motion and press feedback, and **no flourish** — the flourish belongs to
 * the data. An interface that animates a failure is enjoying itself at the
 * parent's expense.
 */
@Composable
fun ErrorPanel(
    failure: ApiFailure,
    modifier: Modifier = Modifier,
    placement: ErrorPlacement = ErrorPlacement.Inline,
    onRetry: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    var expanded by remember(failure) { mutableStateOf(false) }
    var copied by remember(failure) { mutableStateOf(false) }
    var shown by remember(failure) { mutableStateOf(reduced) }
    LaunchedEffect(failure) { shown = true }

    // Entry motion on the panel itself, the same fade-and-settle every other
    // row on these screens gets. It arrives; it does not perform.
    val appearance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = Motion.spec(reduced),
        label = "error-entry",
    )

    val urgent = failure.isUrgent
    val tint = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().alpha(appearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(if (urgent) "⚠" else "ℹ", color = tint)
                Text(
                    errorCopy(failure, "title"),
                    style = MaterialTheme.typography.titleMedium,
                    color = tint,
                )
            }
            Text(
                errorCopy(failure, "action"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.errorpanel_retry))
                }
            }

            // The line a parent photographs. Dimmed with a colour token, never
            // with alpha: it has to stay legible after a messenger has
            // re-compressed the screenshot twice.
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics {
                    // TalkBack would otherwise read SZ-E504 as a word.
                    contentDescription = failure.wire.toCharArray().joinToString(" ")
                },
            ) {
                Text(
                    "${failure.wire} · ${failure.ref}  ${if (expanded) "▲" else "▼"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                val context = LocalContext.current
                val details = remember(failure) { deviceDetails(context, failure) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            copyToClipboard(context, details)
                            copied = true
                        },
                    ) {
                        Text(
                            stringResource(
                                if (copied) R.string.errorpanel_copied else R.string.errorpanel_copy,
                            ),
                        )
                    }
                }
            }
        }
    }

    when (placement) {
        // An error panel takes the footprint its skeleton had, so the layout
        // does not jump when a load fails.
        ErrorPlacement.Inline -> Column(modifier) { content() }
        ErrorPlacement.Banner -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/**
 * The catalog's copy for this failure, in the phone's language.
 *
 * Resolved by resource *name* rather than through `R`: a code the catalog
 * reaches only on other platforms has no `R` field here. A missing one falls
 * back to SZ-E901's wording, which keeps something readable and reportable on
 * screen — falling back to the raw key would put `error_SZ_E603_title` in front
 * of a parent.
 */
@Composable
private fun errorCopy(failure: ApiFailure, part: String): String {
    val context = LocalContext.current
    val name = errorCopyResource(failure.code, part)
    val id = context.resources.getIdentifier(name, "string", context.packageName)
    return if (id == 0) {
        stringResource(context.resources.getIdentifier("error_SZ_E901_$part", "string", context.packageName))
    } else {
        stringResource(id)
    }
}

/**
 * What a maintainer needs and nothing that describes a family — no email, no
 * child name, no request or response body, and the endpoint as a path.
 */
private fun deviceDetails(context: Context, failure: ApiFailure): String = failure.copyDetails(
    // From the package manager rather than BuildConfig: this module does not
    // generate one (AGP 9 leaves `buildConfig` off), and turning it on for a
    // single string is a build-wide change for no gain.
    appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?",
    androidRelease = android.os.Build.VERSION.RELEASE,
    model = android.os.Build.MODEL,
)

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("schirmziit", text))
}
