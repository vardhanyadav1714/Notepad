package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.fragment.app.FragmentActivity
import `in`.innovaticshub.notepad.feature.lock.NoteLockController
import `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every new session requires disclosure acceptance and an adult's private PIN. */
@Composable
internal fun SharingApprovalDialog(onDismiss: () -> Unit, onApproved: () -> Unit, forRecognition: Boolean = false) {
    val context = LocalContext.current
    val safety = remember { SharingSafetyStore(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val setup = remember { !safety.configured }
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun approve() {
        busy = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (setup) safety.configure(pin) else check(safety.verify(pin)) { "PIN not accepted. After three attempts, wait one minute." }
                    if (!forRecognition) safety.setEnabled(true)
                }
            }.onSuccess {
                if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onApproved()
                else error = "Return to Drawly and approve again."
            }.onFailure { error = it.message ?: "Sharing could not be enabled." }
            busy = false
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (setup) "Ask an adult to set up controls" else if(forRecognition) "Adult approval for recognition" else "Adult approval for sharing") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ask a parent or guardian to approve this optional feature and supervise children. Keep the family PIN private. Local drawing works without this feature.")
                if (forRecognition) {
                    Text("Handwriting recognition uses Google ML Kit. It downloads language models and sends Google device and app information, installation identifiers, performance and error diagnostics, and configured languages. Handwriting input and recognized text are processed on this device and are not sent to Google.")
                    Text("The SDK starts after approval and may continue handling diagnostics while this app process runs. No drawing room is joined by enabling recognition.")
                } else {
                Text("Shared ink is sent to Drawly's server and everyone with the invite. Draw only with people you know. Do not draw your name, address, school, phone number, passwords or other personal details.")
                Text("Rooms normally expire after 24 hours without activity. Leaving does not delete shared ink. The room creator can end the room and delete its server copy. Others may keep their own copies.")
                Text("Sharing rules: no sexual content, abuse, bullying, threats, illegal content or personal information. Use Report or Block and leave if something is unsafe. Text, pictures, audio and imported PDFs stay local.")
                }
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text(if (setup) "Create a 6-digit family PIN" else "Family PIN") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                if (setup) OutlinedTextField(confirmation, { confirmation = it.filter(Char::isDigit).take(6) }, label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                Row { Checkbox(accepted, { accepted = it }); Text(if(forRecognition) "I am the supervising adult and approve this SDK data handling." else "I am the supervising adult, accept the sharing rules and approve sending shared ink.", Modifier.padding(top = 8.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !busy && accepted && pin.length == 6 && (!setup || pin == confirmation), onClick = {
            if (setup) {
                val activity = context as? FragmentActivity
                val lock = NoteLockController(context)
                if (activity == null || !lock.canAuthenticate()) error = "An adult must first set up this device's screen lock, then return here."
                else lock.authenticate(activity, "Adult sharing setup", "Confirm this device's screen lock before setting a private sharing PIN", ::approve, { error = it })
            } else approve()
        }) { Text(if (setup) "Set up and approve" else "Approve this session") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Keep drawing offline") } })
}
