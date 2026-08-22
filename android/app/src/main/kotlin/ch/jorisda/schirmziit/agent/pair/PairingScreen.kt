package ch.jorisda.schirmziit.agent.pair

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.sync.SchirmziitClient
import ch.jorisda.schirmziit.agent.sync.SyncWorker
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/// The instance this build points at by default. A self-hoster types their own
/// address over it; prefilling the common case saves a parent typing a URL on a
/// phone keyboard, which is where pairing usually goes wrong.
const val DEFAULT_SERVER = "https://schirmziit.jorisda.ch"

@Composable
fun PairingScreen(
    settings: AgentSettings,
    incoming: EnrollPayload? = null,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualUrl by remember { mutableStateOf(DEFAULT_SERVER) }
    var manualCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    // Resolved up front: stringResource is a composable and cannot be called
    // from the coroutine below.
    val pairingLabel = stringResource(R.string.pair_working)
    val failedLabel = stringResource(R.string.pair_failed)
    val badQrLabel = stringResource(R.string.pair_bad_qr)
    val badInputLabel = stringResource(R.string.pair_bad_input)

    fun pair(payload: EnrollPayload) {
        scope.launch {
            status = pairingLabel
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SchirmziitClient(payload.baseUrl, OkHttpClient()).enroll(
                        code = payload.code,
                        platform = "android",
                        model = "${Build.MANUFACTURER} ${Build.MODEL}",
                        label = Build.MODEL,
                    )
                }
            }
            result.onSuccess { enrolled ->
                settings.baseUrl = payload.baseUrl
                settings.deviceToken = enrolled.token
                SyncWorker.schedule(context)
                onPaired()
            }.onFailure { failure ->
                status = failedLabel.format(failure.message ?: "")
            }
        }
    }

    // A payload that arrived by deep link pairs on its own; the parent already
    // did the scanning in whatever app opened us.
    LaunchedEffect(incoming) {
        if (incoming != null) pair(incoming)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents?.let(EnrollPayloadParser::parse)
        if (payload == null) status = badQrLabel else pair(payload)
    }

    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
        stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.pair_body))

        Button(onClick = { scanner.launch(ScanOptions().setBeepEnabled(false)) }) {
            Text(stringResource(R.string.pair_scan))
        }

        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text(stringResource(R.string.pair_server)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it.uppercase() },
            label = { Text(stringResource(R.string.pair_code)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = manualCode.length == 8,
            onClick = {
                val payload = EnrollPayloadParser.parse(
                    "schirmziit://enroll?url=${manualUrl.trim()}&code=${manualCode.trim()}",
                )
                if (payload == null) {
                    status = badInputLabel
                } else {
                    pair(payload)
                }
            },
        ) {
            Text(stringResource(R.string.pair_submit))
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
