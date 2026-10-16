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

@Composable
internal fun CollaborationSheet(model:DrawingViewModel) {
    val state by model.uiState.collectAsState()
    val context=LocalContext.current
    var invitation by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Collaboration",style=MaterialTheme.typography.titleLarge)
        Text(state.collaborationStatus ?: "Create a room or paste an invite to draw together.",style=MaterialTheme.typography.bodyMedium)
        if(state.isCollaborationBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        val room=state.collaborationRoomCode
        if(room!=null) {
            Text(if(state.collaborationConnected) "Connected" else "Not connected",color=MaterialTheme.colorScheme.primary)
            SelectionContainer {Text(room,style=MaterialTheme.typography.headlineSmall)}
            Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,"Join my Drawly canvas: https://drawly.smartattendance.xyz/r/$room")},"Share collaboration invite"))}) {Text("Share invite link")}
            if(!state.collaborationConnected) OutlinedButton(onClick={model.joinCollaborationRoom(room)},enabled=!state.isCollaborationBusy) {Text("Reconnect")}
            TextButton(onClick=model::leaveCollaborationRoom) {Text("Leave room")}
        } else {
            Button(onClick=model::createCollaborationRoom,enabled=!state.isCollaborationBusy,modifier=Modifier.fillMaxWidth()) {Text("Create room")}
            HorizontalDivider()
            OutlinedTextField(invitation,{invitation=it},label={Text("Room code or invite link")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={model.joinCollaborationRoom(invitation)},enabled=invitation.isNotBlank() && !state.isCollaborationBusy,modifier=Modifier.fillMaxWidth()) {Text("Join room")}
        }
        Text("Handwriting and ink edits are shared live. Text, images, audio and imported PDFs stay on this device.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
