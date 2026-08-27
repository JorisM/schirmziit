package ch.jorisda.schirmziit.agent.pair

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
const val DEFAULT_SERVER = "https://api.schirmziit.ch"

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

    var useCode by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf(DEFAULT_SERVER) }
    var email by rememberSaveable { mutableStateOf("") }
    // Saved across rotation like the rest: it lands in the activity's saved
    // state, which stays in this process. Retyping a manager-filled password
    // because the phone turned sideways is worse.
    var password by rememberSaveable { mutableStateOf("") }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf(Build.MODEL) }
    var session by remember { mutableStateOf<ParentSession?>(null) }
    var children by remember { mutableStateOf<List<SetupChild>>(emptyList()) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    // Collapsed by default. Compose has no content type for "not fillable", so a
    // visible server field sitting above the email field gets filled with the
    // email by a password manager's heuristics. Almost nobody edits it anyway.
    var showServer by rememberSaveable { mutableStateOf(false) }

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
            .imePadding()
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
                enabled = enrollCodeComplete(manualCode),
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

            // contentType is what makes a password manager offer to fill these.
            // Without it the email field is just a text box to Android, which is
            // why only the password field was being offered.
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.pair_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.pair_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
            )
            Button(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                onClick = { signIn() },
            ) {
                Text(stringResource(R.string.pair_parent_submit))
            }
            // Right under the button: the old placement was at the very bottom of
            // a scrolling column, so with the keyboard up the error was off screen
            // and the screen looked like it had simply done nothing.
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            if (showServer) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text(stringResource(R.string.pair_server)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    stringResource(R.string.pair_server_current, server),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { showServer = true }) {
                    Text(stringResource(R.string.pair_server_change))
                }
            }
            TextButton(onClick = { useCode = true }) {
                Text(stringResource(R.string.pair_code_instead))
            }
        }

        if (useCode || children.isNotEmpty()) {
            status?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
