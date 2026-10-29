package `in`.innovaticshub.notepad.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.innovaticshub.notepad.data.content.DrawingPayload
import `in`.innovaticshub.notepad.di.AppContainer
import `in`.innovaticshub.notepad.ui.canvas.screens.ModernDrawingScreen
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.storage.StrokeJson
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Composable
@OptIn(kotlinx.coroutines.FlowPreview::class)
fun CanvasWorkspace(container: AppContainer, isDark: Boolean, roomCode: String? = null,
    documentKey: String = "sketchbook", initialDrawing: DrawingPayload? = null,
    onSaved: ((String, Int) -> Unit)? = null, onRecognizedText: (suspend (String) -> Unit)? = null,
    onOpenTextNote: ((String)->Unit)? = null,
    initialBlocks:List<`in`.innovaticshub.notepad.data.content.NoteBlock> = emptyList(),
    onDocumentSaved:(suspend (SavedSession)->Unit)?=null,
    title:String?=null,onTitleChange:((String)->Unit)?=null,startInTextMode:Boolean=false,
    noteMenu:(@Composable (()->Unit)->Unit)?=null,onBack: () -> Unit) {
    val canvasOwner = rememberScreenViewModelOwner(documentKey)
    val drawing: DrawingViewModel = viewModel(viewModelStoreOwner = canvasOwner, key = "canvas-$documentKey")
    var ready by remember(documentKey) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val mutex = remember(documentKey) { Mutex() }
    val lastSaved = remember(documentKey) { mutableStateOf<SavedSession?>(null) }
    val latestSaved by rememberUpdatedState(onSaved)
    val latestDocumentSaved by rememberUpdatedState(onDocumentSaved)
    val owner = LocalLifecycleOwner.current
    suspend fun persist() = mutex.withLock {
        val state = drawing.uiState.value
        val snapshot = state.savedSession()
        if (snapshot == lastSaved.value) return@withLock
        withContext(Dispatchers.IO) {
            if (documentKey == "sketchbook") DrawingStorage.saveSession(container.appContext, snapshot)
            else {
                val path = initialDrawing?.strokesFile ?: "drawings/$documentKey.json"
                DrawingStorage.saveDocument(container.appContext,path,snapshot)
                withContext(Dispatchers.Main.immediate) { latestSaved?.invoke(path, state.canvasBackgroundColor.toArgb()) }
            }
        }
        withContext(Dispatchers.Main.immediate) {latestDocumentSaved?.invoke(snapshot)}
        lastSaved.value = snapshot
    }
    fun leave() { scope.launch { runCatching { persist() }.onSuccess { onBack() }.onFailure { error = "Couldn’t save your drawing. Please try again." } } }
    BackHandler { if (ready) leave() else onBack() }
    LaunchedEffect(documentKey) {
        runCatching {
            withContext(Dispatchers.IO) {
                if (documentKey == "sketchbook") DrawingStorage.load(container.appContext)?.let {
                    drawing.restoreSession(it)
                } else {
                    val path = initialDrawing?.strokesFile ?: "drawings/$documentKey.json"
                    val session=DrawingStorage.loadDocument(container.appContext,path)
                    if(session!=null) drawing.restoreSession(session.copy(canvasBackgroundColor=session.canvasBackgroundColor ?: initialDrawing?.backgroundArgb?.let(::Color)))
                    else drawing.restoreSession(emptyList(),initialDrawing?.backgroundArgb?.let(::Color))
                }
            }
            if(initialBlocks.isNotEmpty()) {
                val seeded=withContext(Dispatchers.IO) {`in`.innovaticshub.notepad.feature.editor.seedPageDocument(container.appContext,drawing.snapshot(),initialBlocks,documentKey)}
                drawing.restoreSession(seeded)
            }
            drawing.loadPreferences(container.appContext)
            ready = true
        }.onFailure { error = "Couldn’t open this drawing. Please go back and try again." }
    }
    LaunchedEffect(ready, roomCode) {
        if (ready) (roomCode ?: initialDrawing?.collabRoom)?.let { drawing.offerCollaborationInvite(it) }
    }
    LaunchedEffect(ready,title) {if(ready && title!=null) drawing.setDocumentTitle(title)}
    LaunchedEffect(ready) {
        if (ready) drawing.uiState.map { it.savedSession() }
            .distinctUntilChanged().debounce(600).collect {
                runCatching { persist() }.onFailure { error = "Couldn’t save your drawing. Please try again." }
            }
    }
    DisposableEffect(owner, ready) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && ready) {
                drawing.pauseCollaboration()
                container.applicationScope.launch { runCatching { persist() } }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            if (ready) drawing.pauseCollaboration()
            owner.lifecycle.removeObserver(observer)
            if (ready) container.applicationScope.launch { runCatching { persist() } }
        }
    }
    if (ready) DrawingStudioScreen(drawing, onBack = ::leave,title=title,onTitleChange=onTitleChange,startInTextMode=startInTextMode,noteMenu=noteMenu, onTextMode={
        if(onOpenTextNote==null) leave() else scope.launch {
            runCatching {persist();container.noteRepository.createNote(title="")}
                .onSuccess {onOpenTextNote(it.id)}.onFailure {error="Couldn’t create a text note. Please try again."}
        }
    }, onSaveText = { text ->
        if(onRecognizedText!=null) onRecognizedText(text)
        else container.noteRepository.createNote(title="Handwritten note",initialBlocks=listOf(
            `in`.innovaticshub.notepad.data.content.NoteBlock(`in`.innovaticshub.notepad.data.content.NoteBlock.newId(),
                `in`.innovaticshub.notepad.data.content.BlockType.TEXT,0,
                richText=`in`.innovaticshub.notepad.data.content.RichTextData(text,emptyList()))))
    })
    else Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("Drawing needs attention") }, text = { Text(error!!) },
        confirmButton = { TextButton(onClick = { error = null; if (ready) leave() else onBack() }) { Text(if (ready) "Save and return" else "Go back") } })
}
