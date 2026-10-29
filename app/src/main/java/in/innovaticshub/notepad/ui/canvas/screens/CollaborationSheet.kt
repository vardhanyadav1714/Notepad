package `in`.innovaticshub.notepad.ui.canvas.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CollaborationSheet(model:DrawingViewModel) {
    val state by model.uiState.collectAsState()
    val context=LocalContext.current
    var invitation by rememberSaveable {mutableStateOf("")}
    var pendingApproval by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmingEnd by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val safety = remember { `in`.innovaticshub.notepad.ui.canvas.collab.SharingSafetyStore(context) }
    val scope = rememberCoroutineScope()
    fun approve(action: () -> Unit) { pendingApproval = action }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Collaboration",style=MaterialTheme.typography.titleLarge)
        Text(state.collaborationStatus ?: "Create a room or paste an invite to draw together.",style=MaterialTheme.typography.bodyMedium)
        localError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
        if(state.isCollaborationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val room=state.collaborationRoomCode
        if(room!=null) {
            Text(if(state.collaborationConnected) "Connected" else "Not connected",color=MaterialTheme.colorScheme.primary)
            RoomMemberList(model)
            SelectionContainer {Text(room,style=MaterialTheme.typography.headlineSmall)}
            Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"Join my Drawly canvas: ${`in`.innovaticshub.notepad.ui.canvas.collab.CollaborationEndpoint.shareLink(room)}")},"Share collaboration invite"))}) {Text("Share invite link")}
            if(!state.collaborationConnected) OutlinedButton(onClick={approve {model.joinCollaborationRoom(room)}},enabled=!state.isCollaborationBusy) {Text("Approve and connect")}
            TextButton(onClick=model::leaveCollaborationRoom) {Text("Leave room")}
            OutlinedButton(onClick={reporting=true}) { Text("Report unsafe content or an artist") }
            TextButton(onClick=model::blockCollaborationRoom) { Text("Block this room and leave") }
            if (model.canEndCollaboration()) TextButton(onClick={confirmingEnd=true}) { Text("End room and delete shared ink") }
        } else {
            Button(onClick={approve(model::createCollaborationRoom)},enabled=!state.isCollaborationBusy,modifier=Modifier.fillMaxWidth()) {Text("Create room")}
            HorizontalDivider()
            OutlinedTextField(invitation,{invitation=it},label={Text("Room code or invite link")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={approve {model.joinCollaborationRoom(invitation)}},enabled=invitation.isNotBlank() && !state.isCollaborationBusy,modifier=Modifier.fillMaxWidth()) {Text("Join room")}
        }
        Text("Handwriting and ink edits are shared live. Text, images, audio and imported PDFs stay on this device.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Draw with people you know. Everyone with the invite link can join and see shared ink. Do not draw your name, address, school, phone number or other personal details.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Leaving keeps shared ink until the room expires, normally after 24 hours without activity. Ending a room deletes its server copy; others may have saved their own copies. Blocking prevents this device from joining this room again.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={model.pauseCollaboration(); scope.launch {
            runCatching { withContext(Dispatchers.IO) {safety.setEnabled(false)} }
                .onFailure {localError="Sharing is paused, but the setting could not be saved. Please retry."}
        }}) {Text("Disable sharing on this device")}
    }
    pendingApproval?.let { action -> SharingApprovalDialog(onDismiss={pendingApproval=null},onApproved={pendingApproval=null;model.approveSharingForSession();action()}) }
    if (confirmingEnd) AlertDialog(onDismissRequest={confirmingEnd=false},title={Text("End this room for everyone?")},
        text={Text("The shared server copy will be deleted and this invite will stop working. Local copies on participants' devices remain.")},
        confirmButton={TextButton(onClick={confirmingEnd=false;approve(model::endCollaborationRoom)}) {Text("Approve deletion")}},
        dismissButton={TextButton(onClick={confirmingEnd=false}) {Text("Cancel")}})
    if (reporting) AlertDialog(onDismissRequest={reporting=false},title={Text("Report and pause sharing")},
        text={Column {
            Text("Choose what happened in this room. A report sends its room reference and reason to Drawly for review, without asking for your name or email. Ask an adult for help. This is not an emergency service.")
            listOf("Unsafe or sexual content" to "unsafe_content", "Bullying, threats or an unsafe artist" to "bullying", "Personal information" to "personal_information", "Other unsafe behavior" to "other").forEach { (label, reason) ->
                TextButton(onClick={reporting=false;model.reportCollaborationRoom(reason)}) {Text(label)}
            }
        }},confirmButton={TextButton(onClick={reporting=false}) {Text("Cancel")}})
}
