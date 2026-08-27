package ch.jorisda.schirmziit.agent.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.jorisda.schirmziit.agent.R
import ch.jorisda.schirmziit.agent.parent.ApiFailure
import kotlinx.coroutines.launch

/**
 * The parent signing in on their own phone.
 *
 * Separate from `PairingScreen`'s parent sign-in, which enrols a *child's*
 * phone and throws the session away immediately afterwards. This one keeps the
 * session, which is exactly why the two must not share a code path.
 *
 * There is no hardcoded backend: the address is whatever the family runs. It is
 * prefilled because typing a URL on a phone is where sign-in usually goes wrong.
 */
@Composable
fun SignInScreen(
    defaultServer: String = "https://api.schirmziit.ch",
    onSignIn: suspend (server: String, email: String, password: String) -> ApiFailure?,
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(defaultServer) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiFailure?>(null) }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A way back to the role choice: picking "my phone" by mistake on a
        // phone meant for a child must not be a dead end that needs a reinstall.
        TextButton(onClick = onBack, enabled = !busy) {
            Text(stringResource(R.string.nav_back))
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.signin_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.signin_server)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.signin_email)) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            // Named for the password manager. A field it cannot classify is one
            // a parent types by hand on every sign-in.
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.signin_password)) },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        )

        // SZ-E101 already says "that email or password is wrong" in four
        // languages, so this screen keeps no sentence of its own.
        failure?.let { ErrorPanel(failure = it) }

        Button(
            onClick = {
                busy = true
                failure = null
                scope.launch {
                    val result = onSignIn(server.trim(), email.trim(), password)
                    busy = false
                    if (result == null) onSignedIn() else failure = result
                }
            },
            enabled = !busy && email.isNotBlank() && password.isNotEmpty() && server.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (busy) R.string.signin_working else R.string.signin_submit))
        }
    }
}
