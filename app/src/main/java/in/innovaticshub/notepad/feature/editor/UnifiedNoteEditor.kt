package `in`.innovaticshub.notepad.feature.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import `in`.innovaticshub.notepad.di.AppContainer
import `in`.innovaticshub.notepad.feature.CanvasWorkspace
import `in`.innovaticshub.notepad.feature.lock.NoteLockController
import `in`.innovaticshub.notepad.feature.notes.NotebookEmptyState
import kotlinx.coroutines.launch

/** Every library note opens one document surface. Legacy content is imported only once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(viewModel:NoteEditorViewModel,container:AppContainer,folders:List<FolderEntity>,isDark:Boolean,onBack:()->Unit) {
    val state by viewModel.uiState.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope()
    var createdHere by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf(false)}
    var moving by remember {mutableStateOf(false)}
    val lock=remember {NoteLockController(context)}
    val drawing=state.blocks.firstOrNull {it.type==BlockType.DRAWING}
    fun leave() {scope.launch {if(viewModel.saveBeforeLeaving()) onBack() else error="Could not save this note. Please retry."}}
    LaunchedEffect(state.noteId,state.isLoading,state.lockedGate,drawing?.id) {
        if(!state.isLoading && !state.loadFailed && !state.lockedGate && drawing==null) {
            val id=NoteBlock.newId();createdHere=true;viewModel.attachDrawing(id,"drawings/$id.json",state.paperColorArgb)
        }
    }
    if(state.isLoading || (!state.loadFailed && !state.lockedGate && drawing==null)) {
        Surface(Modifier.fillMaxSize()) {Box(contentAlignment=Alignment.Center) {CircularProgressIndicator()}};return
    }
    if(state.lockedGate || state.loadFailed) {
        BackHandler {onBack()}
        Surface(Modifier.fillMaxSize()) {Column(Modifier.statusBarsPadding()) {
            IconButton(onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}
            NotebookEmptyState(if(state.loadFailed) "Note unavailable" else "Locked note",if(state.loadFailed) "Return to your notes." else "Unlock with your device screen lock or biometrics.",Icons.Outlined.Lock) {
                Button(onClick={if(state.loadFailed) onBack() else lock.authenticate(context as FragmentActivity,"Unlock note","Open this note",viewModel::onUnlocked,{error=it})}) {Text(if(state.loadFailed) "Back" else "Unlock note")}
            }
        } }
    } else if(drawing!=null) {
        val initial=remember(drawing.id) {state.blocks}
        CanvasWorkspace(container,isDark,documentKey=drawing.id,initialDrawing=drawing.drawing,
            initialBlocks=initial,title=state.title,onTitleChange=viewModel::updateTitle,
            startInTextMode=createdHere || initial.any {(it.type==BlockType.TEXT || it.type==BlockType.CHECKLIST_ITEM) && it.richText.text.isNotBlank()},
            onSaved={path,background->viewModel.attachDrawing(drawing.id,path,background,drawing.drawing?.collabRoom)},
            onDocumentSaved={document->viewModel.applyDocumentText(document.texts);check(viewModel.saveBeforeLeaving()) {"Could not save note metadata"}},
            noteMenu={close->
                DropdownMenuItem(text={Text(if(state.isPinned) "Remove from favourites" else "Add to favourites")},leadingIcon={Icon(Icons.Outlined.StarBorder,null)},onClick={close();viewModel.setPinned(!state.isPinned)})
                DropdownMenuItem(text={Text("Move to folder")},leadingIcon={Icon(Icons.Outlined.FolderOpen,null)},onClick={close();moving=true})
                DropdownMenuItem(text={Text(if(state.isLocked) "Remove lock" else "Lock note")},leadingIcon={Icon(Icons.Outlined.Lock,null)},onClick={
                    close();if(!lock.canAuthenticate()) error="Set up a device screen lock first."
                    else lock.authenticate(context as FragmentActivity,"Confirm note lock","Use your device screen lock",{container.unlockedNoteIds.add(state.noteId!!);viewModel.setLocked(!state.isLocked)},{error=it})
                })
                DropdownMenuItem(text={Text("Move to trash")},leadingIcon={Icon(Icons.Outlined.DeleteOutline,null)},onClick={close();deleting=true})
            },onBack=::leave)
    }
    if(moving) ModalBottomSheet(onDismissRequest={moving=false}) {Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom=24.dp)) {
        Text("Move to folder",style=MaterialTheme.typography.titleLarge)
        TextButton(onClick={viewModel.moveToFolder(null);moving=false}) {Text("No folder")}
        folders.forEach {folder->TextButton(onClick={viewModel.moveToFolder(folder.id);moving=false}) {Text(folder.name)}}
    } }
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("Move note to trash?")},text={Text("You can restore it from Trash.")},
        confirmButton={TextButton(onClick={deleting=false;viewModel.trashNote(onBack)}) {Text("Move to trash")}},dismissButton={TextButton(onClick={deleting=false}) {Text("Cancel")}})
    error?.let {message->AlertDialog(onDismissRequest={error=null},title={Text("Note needs attention")},text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("OK")}})}
}
