package `in`.innovaticshub.notepad.feature.editor

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import `in`.innovaticshub.notepad.di.AppContainer
import `in`.innovaticshub.notepad.feature.CanvasWorkspace
import `in`.innovaticshub.notepad.feature.lock.NoteLockController
import `in`.innovaticshub.notepad.feature.notes.*
import `in`.innovaticshub.notepad.media.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacyNoteEditorScreen(viewModel: NoteEditorViewModel, container: AppContainer,
    folders: List<FolderEntity>, isDark: Boolean, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val richStates = remember(viewModel) { mutableMapOf<String, RichTextState>() }
    var activeId by remember { mutableStateOf<String?>(null) }
    var drawingId by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var insertMenu by remember {mutableStateOf(false)}
    var sheet by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val lock = remember { NoteLockController(context) }
    val activeBlock = state.blocks.firstOrNull { it.id == activeId }
    val activeRich = activeId?.let { richStates[it] }
    fun message(value: String) { scope.launch { snackbar.showSnackbar(value) } }
    fun finishRecording() {
        recorder.stop()?.let { (path, duration) ->
            viewModel.attachRecording(NoteBlock.newId(), RecordingPayload(path, duration))
        }
        recording = false
    }
    fun leave() {
        if (leaving) return
        finishRecording()
        leaving = true
        scope.launch {
            if (viewModel.saveBeforeLeaving()) onBack()
            else { message("Your changes haven’t saved yet. Please retry."); leaving = false }
        }
    }
    fun startRecording() { recording = recorder.start() != null; if (!recording) message("Couldn’t start the microphone.") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else message("Microphone access is needed to record a voice note.")
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val result = ImageImporter.import(context, uri)
            if (result == null) message("Couldn’t open this image.")
            else viewModel.attachImage(NoteBlock.newId(), ImagePayload(result.first, result.second.width, result.second.height))
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = PdfImporter.import(context, uri)
            if (result == null) message("Couldn’t open this PDF.") else {
                runCatching {
                    val sizes=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PdfPageImages.sizes(context,result.file) }
                    val pages=sizes.mapIndexed { index,size -> `in`.innovaticshub.notepad.ui.canvas.model.CanvasPage(java.util.UUID.randomUUID().toString(),result.file,index,1000f,1000f*size.second/size.first) }
                    val id=NoteBlock.newId(); val file="drawings/$id.json"
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage.saveDocument(context,file,
                            `in`.innovaticshub.notepad.ui.canvas.storage.SavedSession(emptyList(),Color.White,null,pages=pages,currentPageId=pages.first().id))
                    }
                    viewModel.attachDrawing(id,file,0xFFFFFFFF.toInt()); drawingId=id
                }.onFailure {message("Couldn’t prepare this PDF for annotation.")}
            }
        }
    }
    fun saveFormatting(action: (RichTextState) -> Unit) {
        val id = activeId ?: return
        val rich = richStates[id] ?: return
        action(rich); viewModel.updateBlockText(id, rich.toRichTextData())
    }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { finishRecording(); viewModel.flushSaveNow() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); finishRecording(); recorder.release(); viewModel.flushSaveNow() }
    }
    BackHandler(drawingId == null) { leave() }
    val drawingBlock = state.blocks.firstOrNull { it.id == drawingId }
    if (drawingBlock != null) {
        CanvasWorkspace(container, isDark, documentKey = drawingBlock.id, initialDrawing = drawingBlock.drawing,
            onSaved = { file, background ->
                viewModel.attachDrawing(drawingBlock.id, file, background, drawingBlock.drawing?.collabRoom)
                viewModel.flushSaveNow()
            },
            onRecognizedText = { text ->
                val id=viewModel.addTextBlock()
                viewModel.updateBlockText(id,RichTextData(text,emptyList()))
                check(viewModel.saveBeforeLeaving()) { "Couldn’t save the recognized text" }
            },
            onBack = { drawingId = null; viewModel.flushSaveNow() })
        return
    }
    if (state.isLoading) {
        Surface(Modifier.fillMaxSize()) { Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() } }; return
    }
    if (state.loadFailed || state.lockedGate) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                NotebookEmptyState(if (state.loadFailed) "This note is no longer here" else "A little privacy",
                    if (state.loadFailed) "It may have been deleted. Return to your notes." else "Unlock this note with your device’s screen lock or biometrics.",
                    if (state.loadFailed) Icons.Outlined.Description else Icons.Outlined.Lock) {
                    Button(onClick = {
                        if (state.loadFailed) onBack() else lock.authenticate(context as FragmentActivity, "Unlock note", "Open your private note", viewModel::onUnlocked, ::message)
                    }) { Text(if (state.loadFailed) "Back to notes" else "Unlock note") }
                }
            }
        }
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Text(if(state.saveError!=null) "Save failed - tap to retry" else folders.firstOrNull {it.id==state.folderId}?.name ?: "Note",
                    style=MaterialTheme.typography.titleMedium,maxLines=1,
                    color=if(state.saveError!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier=Modifier.clickable(enabled=state.saveError!=null) {viewModel.flushSaveNow()})
            }, navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Save and return") } },
                actions = {
                    IconButton(onClick={
                        drawingId=state.blocks.firstOrNull {it.type==BlockType.DRAWING}?.id ?: NoteBlock.newId().also {viewModel.attachDrawing(it,"drawings/$it.json",0xFFFAFAFA.toInt())}
                    }) {Icon(Icons.Outlined.Draw,"Handwriting mode")}
                    Box {
                        IconButton(onClick={insertMenu=true}) {Icon(Icons.Outlined.AttachFile,"Insert")}
                        DropdownMenu(insertMenu,{insertMenu=false}) {
                            DropdownMenuItem(text={Text("Text")},leadingIcon={Icon(Icons.Outlined.TextFields,null)},onClick={insertMenu=false;activeId=viewModel.addTextBlock(activeId)})
                            DropdownMenuItem(text={Text("Checklist")},leadingIcon={Icon(Icons.Outlined.Checklist,null)},onClick={insertMenu=false;activeId=viewModel.addChecklistItem(activeId)})
                            DropdownMenuItem(text={Text("Image")},leadingIcon={Icon(Icons.Outlined.Image,null)},onClick={insertMenu=false;imagePicker.launch("image/*")})
                            DropdownMenuItem(text={Text("PDF")},leadingIcon={Icon(Icons.Outlined.PictureAsPdf,null)},onClick={insertMenu=false;pdfPicker.launch(arrayOf("application/pdf"))})
                            DropdownMenuItem(text={Text(if(recording) "Stop recording" else "Voice recording")},leadingIcon={Icon(Icons.Outlined.Mic,null)},onClick={
                                insertMenu=false
                                if(recording) finishRecording()
                                else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startRecording()
                                else permission.launch(Manifest.permission.RECORD_AUDIO)
                            })
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Note options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Share text") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                                menu = false
                                val text = buildString {
                                    appendLine(state.title)
                                    state.blocks.forEach { block ->
                                        if (block.type == BlockType.CHECKLIST_ITEM) appendLine("${if (block.isChecked == true) "☑" else "☐"} ${block.richText.text}")
                                        else if (block.type == BlockType.TEXT) appendLine(block.richText.text)
                                    }
                                }
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share note"))
                            })
                            DropdownMenuItem(text={Text(if(state.isPinned) "Unpin note" else "Pin note")},leadingIcon={Icon(Icons.Outlined.PushPin,null)},onClick={menu=false;viewModel.setPinned(!state.isPinned)})
                            DropdownMenuItem(text = { Text("Note colour") }, leadingIcon = { Icon(Icons.Outlined.Palette, null) }, onClick = { menu = false; sheet = "color" })
                            DropdownMenuItem(text = { Text("Paper style") }, leadingIcon = { Icon(Icons.Outlined.GridOn, null) }, onClick = { menu = false; sheet = "paper" })
                            DropdownMenuItem(text = { Text("Move to folder") }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) }, onClick = { menu = false; sheet = "folder" })
                            DropdownMenuItem(text = { Text(if (state.isLocked) "Remove lock" else "Lock note") }, leadingIcon = { Icon(Icons.Outlined.Lock, null) }, onClick = {
                                menu = false
                                if (!lock.canAuthenticate()) message("Set up a screen lock on your device to lock notes.")
                                else lock.authenticate(context as FragmentActivity, if (state.isLocked) "Remove note lock" else "Lock this note", "Confirm with your device’s screen lock", {
                                    container.unlockedNoteIds.add(state.noteId!!); viewModel.setLocked(!state.isLocked)
                                }, ::message)
                            })
                            DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = { menu = false; deleteConfirm = true })
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }, bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
                Column(Modifier.imePadding().navigationBarsPadding()) {
                    if (recording) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.error)
                        Text("Recording voice note…", Modifier.weight(1f).padding(12.dp))
                        TextButton(onClick = ::finishRecording) { Text("Stop") }
                    }
                    if (activeRich != null && activeBlock != null) EditorToolbar(activeRich.currentFormatting(), activeBlock.listType, activeBlock.textAlign, activeBlock.indentLevel,
                        activeRich.undoStack.isNotEmpty(), activeRich.redoStack.isNotEmpty(),
                        onToggleStyle = { kind -> saveFormatting { it.toggleStyle(kind) } },
                        onFontColor = { argb -> saveFormatting { it.toggleStyle(SpanKind.COLOR, argb = argb) } },
                        onHighlight = { argb -> saveFormatting { it.toggleStyle(SpanKind.HIGHLIGHT, argb = argb) } },
                        onCycleSize = { saveFormatting { rich -> val current = rich.currentFormatting().sizePx ?: 16; rich.toggleStyle(SpanKind.SIZE, sizePx = FontSizes[(FontSizes.indexOf(current) + 1) % FontSizes.size]) } },
                        onAlign = { viewModel.setTextAlign(activeBlock.id, it) }, onListType = { viewModel.setListType(activeBlock.id, it) },
                        onIndent = { viewModel.setIndent(activeBlock.id, it) }, onUndo = { saveFormatting { it.undo() } }, onRedo = { saveFormatting { it.redo() } })
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val paper = if (state.noteColorArgb == -1 || isDark) MaterialTheme.colorScheme.surface else Color(state.noteColorArgb)
            val ruling = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize().background(paper)
                .drawBehind {
                    val step = 28.dp.toPx()
                    if (state.pageStyleKey == "RULED" || state.pageStyleKey == "GRID") {
                        var y = 0f
                        while (y < size.height) { drawLine(ruling, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()); y += step }
                    }
                    if (state.pageStyleKey == "GRID") {
                        var x = 0f
                        while (x < size.width) { drawLine(ruling, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx()); x += step }
                    }
                    if (state.pageStyleKey == "DOTTED") {
                        var y = step
                        while (y < size.height) { var x = step; while (x < size.width) { drawCircle(ruling, 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
                    }
                },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    BasicTextField(state.title, viewModel::updateTitle, textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { field -> Box { if (state.title.isBlank()) Text("Title", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)); field() } })
                    HorizontalDivider(Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
                itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
                    if (block.type == BlockType.TEXT || block.type == BlockType.CHECKLIST_ITEM) {
                        val rich = richStates.getOrPut(block.id) { RichTextState(block.richText) }
                        Row(Modifier.fillMaxWidth().padding(start = (block.indentLevel * 20).dp), verticalAlignment = Alignment.Top) {
                            if (block.type == BlockType.CHECKLIST_ITEM) Checkbox(block.isChecked == true, { viewModel.toggleChecked(block.id) }, Modifier.size(40.dp))
                            else if (block.listType != ListType.NONE) Text(if (block.listType == ListType.BULLET) "•" else "${index + 1}.", modifier = Modifier.padding(end = 10.dp, top = 4.dp))
                            BasicTextField(rich.textFieldValue, { value -> rich.onValueChange(value); viewModel.updateBlockText(block.id, rich.toRichTextData()) },
                                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = if (block.isChecked == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (block.isChecked == true) TextDecoration.LineThrough else null,
                                    textAlign = when (block.textAlign) { TextAlign.LEFT -> androidx.compose.ui.text.style.TextAlign.Start; TextAlign.CENTER -> androidx.compose.ui.text.style.TextAlign.Center; TextAlign.RIGHT -> androidx.compose.ui.text.style.TextAlign.End })),
                                visualTransformation = VisualTransformation { TransformedText(rich.toAnnotatedString(), OffsetMapping.Identity) },
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f).heightIn(min = 48.dp).onFocusChanged { if (it.isFocused) activeId = block.id },
                                decorationBox = { field -> Box(Modifier.padding(vertical = 4.dp)) { if (rich.text.isEmpty()) Text(if (block.type == BlockType.CHECKLIST_ITEM) "Something to do…" else "Start writing", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)); field() } })
                            if (block.type == BlockType.CHECKLIST_ITEM) IconButton(onClick = { viewModel.removeBlock(block.id) }, Modifier.size(40.dp)) { Icon(Icons.Outlined.Close, "Remove checklist item", Modifier.size(16.dp)) }
                        }
                    } else {
                        AttachmentBlock(block, container, onDrawing = { drawingId = block.id }, onRemove = { viewModel.removeBlock(block.id) }, onError = ::message)
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
    sheet?.let { current -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
        Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(when (current) { "color" -> "Note colour"; "paper" -> "Choose your paper"; else -> "Move to a folder" }, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            if (current == "color") Row(Modifier.horizontalScroll(rememberScrollState())) {
                NoteColorPalette(state.noteColorArgb, { viewModel.setNoteColor(it); sheet = null }, Modifier.width(420.dp))
            } else if (current == "paper") {
                listOf("PLAIN" to "Plain", "RULED" to "Ruled", "GRID" to "Grid", "DOTTED" to "Dotted").forEach { (key, label) ->
                    TextButton(onClick = { viewModel.setPageStyle(key, state.paperColorArgb); sheet = null }) {
                        if (state.pageStyleKey == key) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(10.dp)) }
                        Text(label)
                    }
                }
            } else {
                TextButton(onClick = { viewModel.moveToFolder(null); sheet = null }) { Text("No folder") }
                folders.forEach { folder -> TextButton(onClick = { viewModel.moveToFolder(folder.id); sheet = null }) { FolderColorDot(folder.colorArgb); Spacer(Modifier.width(12.dp)); Text(folder.name) } }
            }
        }
    } }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("Move this note to Trash?") },
        text = { Text("You can restore it later from Trash.") }, confirmButton = { TextButton(onClick = {
            deleteConfirm = false; finishRecording(); viewModel.trashNote(onBack)
        }) { Text("Move to trash") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Keep note") } })
}

@Composable
private fun InsertAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label) }
}

@Composable
private fun AttachmentBlock(block: NoteBlock, container: AppContainer, onDrawing: () -> Unit, onRemove: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    var player by remember(block.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(block.id) { onDispose { player?.release() } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player?.pause(); playing = false } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            if (block.type == BlockType.DRAWING && block.drawing != null) {
                val preview by produceState<String?>(null, block.id) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        `in`.innovaticshub.notepad.ui.canvas.drawing.NoteThumbnailRenderer.render(container.appContext, block.id, block.drawing, cacheOnly = true)
                    }
                }
                if (preview != null) coil.compose.AsyncImage(model = File(preview!!), contentDescription = "Handwritten sketch preview",
                    modifier = Modifier.fillMaxWidth().height(180.dp).clickable(onClick = onDrawing))
            }
            if (block.type == BlockType.IMAGE && block.image != null) coil.compose.AsyncImage(model = File(container.appContext.filesDir, block.image.path),
                contentDescription = "Attached image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = when (block.type) { BlockType.DRAWING -> "Handwritten sketch"; BlockType.PDF -> "PDF · ${block.pdf?.pageCount ?: 0} pages"; BlockType.RECORDING -> "Voice note · ${(block.recording?.durationMs ?: 0) / 1000}s"; else -> "Image" }
                val icon = when (block.type) { BlockType.DRAWING -> Icons.Outlined.Gesture; BlockType.PDF -> Icons.Outlined.PictureAsPdf; BlockType.RECORDING -> Icons.Outlined.Mic; else -> Icons.Outlined.Image }
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge)
                if (block.type != BlockType.IMAGE) IconButton(onClick = {
                    when (block.type) {
                        BlockType.DRAWING -> onDrawing()
                        BlockType.PDF -> runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(context.filesDir, block.pdf!!.file))
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { onError("Install a PDF viewer to open this attachment.") }
                        BlockType.RECORDING -> runCatching {
                            if (playing) { player?.pause(); playing = false }
                            else { if (player == null) player = MediaPlayer().apply {
                                setDataSource(File(context.filesDir, block.recording!!.path).absolutePath); prepare()
                                setOnCompletionListener { playing = false }
                            }; player?.start(); playing = true }
                        }.onFailure { onError("Couldn’t play this voice note.") }
                        else -> Unit
                    }
                }) { Icon(if (block.type == BlockType.RECORDING) { if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow } else Icons.Outlined.OpenInNew, "Open attachment") }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove attachment", Modifier.size(18.dp)) }
            }
        }
    }
}
