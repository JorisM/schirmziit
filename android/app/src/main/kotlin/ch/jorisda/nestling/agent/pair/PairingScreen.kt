package ch.jorisda.nestling.agent.pair

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ch.jorisda.nestling.agent.store.AgentSettings
import ch.jorisda.nestling.agent.sync.NestlingClient
import ch.jorisda.nestling.agent.sync.SyncWorker
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@Composable
fun PairingScreen(settings: AgentSettings, onPaired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualUrl by remember { mutableStateOf("https://") }
    var manualCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    fun pair(payload: EnrollPayload) {
        scope.launch {
            status = "pairing…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    NestlingClient(payload.baseUrl, OkHttpClient()).enroll(
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
                status = "pairing failed: ${failure.message}"
            }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents?.let(EnrollPayloadParser::parse)
        if (payload == null) status = "that QR code is not a nestling pairing code" else pair(payload)
    }

    Column(
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pair this phone", style = MaterialTheme.typography.headlineSmall)
        Text("Scan the code your parent's dashboard shows, or type it in.")

        Button(onClick = { scanner.launch(ScanOptions().setBeepEnabled(false)) }) {
            Text("Scan QR code")
        }

        OutlinedTextField(
            value = manualUrl,
            onValueChange = { manualUrl = it },
            label = { Text("Server address") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = manualCode,
            onValueChange = { manualCode = it.uppercase() },
            label = { Text("8-character code") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = manualCode.length == 8,
            onClick = {
                val payload = EnrollPayloadParser.parse(
                    "nestling://enroll?url=${manualUrl.trim()}&code=${manualCode.trim()}",
                )
                if (payload == null) {
                    status = "check the server address (https) and the code"
                } else {
                    pair(payload)
                }
            },
        ) {
            Text("Pair")
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
