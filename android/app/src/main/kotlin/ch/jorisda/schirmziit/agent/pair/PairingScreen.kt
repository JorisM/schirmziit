package ch.jorisda.schirmziit.agent.pair

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.store.AgentSettings
import ch.jorisda.schirmziit.agent.sync.ParentSession
import ch.jorisda.schirmziit.agent.sync.SchirmziitClient
import ch.jorisda.schirmziit.agent.sync.SetupChild
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

/**
 * Two ways to connect this phone.
 *
 * Signing in with the parent account is the default, because it is the one that
 * needs no code read aloud and typed on a phone keyboard. The code path stays
 * for the case where the parent is not standing there — a child setting up their
 * own phone should not need a parent password.
 */
@Composable
fun PairingScreen(
    settings: AgentSettings,
    incoming: EnrollPayload? = null,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var useCode by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(DEFAULT_SERVER) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var label by remember { mutableStateOf(Build.MODEL) }
    var session by remember { mutableStateOf<ParentSession?>(null) }
    var children by remember { mutableStateOf<List<SetupChild>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // stringResource is a composable and cannot be called from the coroutines.
    val pairingLabel = stringResource(R.string.pair_working)
    val failedLabel = stringResource(R.string.pair_failed)
    val badQrLabel = stringResource(R.string.pair_bad_qr)
    val badInputLabel = stringResource(R.string.pair_bad_input)
    val wrongLabel = stringResource(R.string.pair_parent_wrong)
    val model = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun pairWithCode(payload: EnrollPayload) {
        scope.launch {
            status = pairingLabel
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SchirmziitClient(payload.baseUrl, OkHttpClient()).enroll(
                        code = payload.code,
                        platform = "android",
                        model = model,
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

    fun setup(baseUrl: String) = ParentSetup(SchirmziitClient(baseUrl, OkHttpClient()), settings)

    fun signIn() {
        scope.launch {
            busy = true
            status = null
            val base = server.trim().trimEnd('/')
            when (val result = withContext(Dispatchers.IO) { setup(base).signIn(email.trim(), password) }) {
                is ParentSetup.SignIn.Ready -> {
                    session = result.session
                    children = result.children
                    chosen = result.children.singleOrNull()?.id
                    password = ""
                }
                is ParentSetup.SignIn.WrongCredentials -> status = wrongLabel
                is ParentSetup.SignIn.Failed -> status = failedLabel.format(result.message)
            }
            busy = false
        }
    }

    fun claim() {
        val active = session ?: return
        val childId = chosen ?: return
        scope.launch {
            busy = true
            status = pairingLabel
            val base = server.trim().trimEnd('/')
            val claimed = withContext(Dispatchers.IO) {
                setup(base).claim(active, base, childId, model, label.ifBlank { Build.MODEL })
            }
            busy = false
            // The session is gone either way, so a retry starts from sign-in.
            session = null
            children = emptyList()
            claimed.onSuccess {
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
        if (incoming != null) pairWithCode(incoming)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val payload = result.contents?.let(EnrollPayloadParser::parse)
        if (payload == null) status = badQrLabel else pairWithCode(payload)
    }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineSmall)

        if (children.isNotEmpty()) {
            Text(stringResource(R.string.pair_whose), style = MaterialTheme.typography.titleMedium)
            children.forEach { child ->
                Column {
                    Button(
                        onClick = { chosen = child.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = chosen == child.id, onClick = { chosen = child.id })
                        Text(child.displayName)
                    }
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.pair_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(enabled = !busy && chosen != null, onClick = { claim() }) {
                Text(stringResource(R.string.pair_claim))
            }
        } else if (useCode) {
            Text(stringResource(R.string.pair_body))

            Button(onClick = { scanner.launch(ScanOptions().setBeepEnabled(false)) }) {
                Text(stringResource(R.string.pair_scan))
            }
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
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
                        "schirmziit://enroll?url=${server.trim()}&code=${manualCode.trim()}",
                    )
                    if (payload == null) status = badInputLabel else pairWithCode(payload)
                },
            ) {
                Text(stringResource(R.string.pair_submit))
            }
            HorizontalDivider()
            TextButton(onClick = { useCode = false }) {
                Text(stringResource(R.string.pair_parent_instead))
            }
        } else {
            Text(stringResource(R.string.pair_parent_body))

            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.pair_server)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.pair_email)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.pair_password)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                onClick = { signIn() },
            ) {
                Text(stringResource(R.string.pair_parent_submit))
            }
            HorizontalDivider()
            TextButton(onClick = { useCode = true }) {
                Text(stringResource(R.string.pair_code_instead))
            }
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
