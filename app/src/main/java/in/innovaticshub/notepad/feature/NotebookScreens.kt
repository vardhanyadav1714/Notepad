package `in`.innovaticshub.notepad.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.data.db.entity.*
import `in`.innovaticshub.notepad.data.prefs.*
import `in`.innovaticshub.notepad.di.AppContainer
import `in`.innovaticshub.notepad.feature.notes.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() }, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to notes") } }, actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, content = content)
}

private val FolderColors = listOf(0xFF367C69, 0xFFBD8D50, 0xFF8672AD, 0xFF6C8FAA, 0xFFC17C77).map { it.toInt() }

@Composable
fun FoldersScreen(container: AppContainer, onBack: () -> Unit, onOpen: (FolderEntity) -> Unit) {
    val folders by remember(container) { container.folderRepository.observeFoldersWithCounts() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<FolderEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(FolderColors.first()) }
    var deleting by remember { mutableStateOf<FolderEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    NotebookPage("Your folders", onBack, actions = {
        IconButton(onClick = { editing = null; name = ""; color = FolderColors.first(); showForm = true }) { Icon(Icons.Outlined.CreateNewFolder, "Create folder") }
    }, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Everything in its place.", style = MaterialTheme.typography.headlineLarge)
                Text("A home for every project, plan, and possibility.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            }
            if (folders.isEmpty()) item {
                NotebookEmptyState("Make room for a new project", "Gather related notes in a folder of their own.", Icons.Outlined.FolderOpen) {
                    Button(onClick = { editing = null; name = ""; showForm = true }) { Text("Create a folder") }
                }
            }
            items(folders, key = { it.folder.id }) { item ->
                val folder = item.folder
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (folder.parentId != null) 28.dp else 12.dp, end = 4.dp)) {
                        Row(Modifier.weight(1f).clickable { onOpen(folder) }.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderOpen, null, tint = Color(folder.colorArgb), modifier = Modifier.padding(horizontal = 8.dp).size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.noteCount} notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { editing = folder; name = folder.name; color = folder.colorArgb; showForm = true }) { Icon(Icons.Outlined.Edit, "Edit ${folder.name}", Modifier.size(20.dp)) }
                        IconButton(onClick = { deleting = folder }) { Icon(Icons.Outlined.DeleteOutline, "Trash ${folder.name}", Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
    if (showForm) AlertDialog(onDismissRequest = { if (!saving) showForm = false }, title = { Text(if (editing == null) "A new folder" else "Edit folder") },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderColors.forEach { swatch ->
                    IconButton(onClick = { color = swatch }, modifier = Modifier.background(Color(swatch).copy(alpha = 0.15f), RoundedCornerShape(12.dp))) {
                        Icon(if (color == swatch) Icons.Outlined.CheckCircle else Icons.Outlined.Circle, "Folder colour", tint = Color(swatch))
                    }
                }
            }
        } }, confirmButton = {
            TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                saving = true
                scope.launch {
                    runCatching {
                        val folder = editing
                        if (folder == null) container.folderRepository.create(name, color, null)
                        else { container.folderRepository.rename(folder.id, name); container.folderRepository.setColor(folder.id, color) }
                    }.onSuccess { showForm = false }.onFailure { snackbar.showSnackbar("Couldn’t save this folder. Please try again.") }
                    saving = false
                }
            }) { Text(if (saving) "Saving…" else "Save folder") }
        }, dismissButton = { TextButton(onClick = { showForm = false }, enabled = !saving) { Text("Cancel") } })
    deleting?.let { folder -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Move folder to trash?") },
        text = { Text("Notes in “${folder.name}” will move to Trash. Restore individual notes there.") },
        confirmButton = { TextButton(onClick = {
            deleting = null; scope.launch { runCatching { container.folderRepository.trashSubtree(folder.id) }
                .onFailure { snackbar.showSnackbar("Couldn’t move this folder to Trash.") } }
        }) { Text("Move to trash") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable
fun TrashScreen(container: AppContainer, onBack: () -> Unit) {
    val notes by remember(container) { container.noteRepository.observeTrash() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<NoteEntity?>(null) }
    NotebookPage("Trash", onBack, snackbar = snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("A second chance for your ideas.", style = MaterialTheme.typography.headlineLarge)
                Text("Restore a note or delete it permanently.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) }
            if (notes.isEmpty()) item { NotebookEmptyState("All clear", "Notes you move to Trash will appear here.", Icons.Outlined.DeleteOutline) }
            items(notes, key = { it.id }) { note ->
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Moved to Trash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { scope.launch { runCatching { container.noteRepository.restoreNote(note.id) }
                            .onSuccess { snackbar.showSnackbar("Note restored") }.onFailure { snackbar.showSnackbar("Couldn’t restore this note.") } } }) { Icon(Icons.Outlined.Restore, "Restore note") }
                        IconButton(onClick = { deleting = note }) { Icon(Icons.Outlined.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this note forever?") },
        text = { Text("“${note.title.ifBlank { "Untitled note" }}” and its attachments will be permanently deleted. This cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            deleting = null; scope.launch { runCatching { container.noteRepository.purgeNote(note.id) }
                .onFailure { snackbar.showSnackbar("Couldn’t delete this note.") } }
        }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep note") } }) }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings = container.settingsRepository
    val theme by settings.themeMode.collectAsState(ThemeMode.SYSTEM)
    val mode by settings.viewMode.collectAsState(NoteViewMode.GRID)
    val sort by settings.noteSort.collectAsState(NoteSort.MODIFIED)
    val scope = rememberCoroutineScope()
    var choosing by remember {mutableStateOf<String?>(null)}
    val inkPreferences=remember {`in`.innovaticshub.notepad.ui.canvas.storage.DrawingPreferences(container.appContext)}
    var fingerDrawing by remember {mutableStateOf(inkPreferences.fingerDrawing)}
    val version=remember {container.appContext.packageManager.getPackageInfo(container.appContext.packageName,0).versionName.orEmpty()}
    NotebookPage("Drawly settings", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SettingsGroup("Display") {
                SettingsRow("Theme",when(theme) {ThemeMode.SYSTEM->"Use system settings";ThemeMode.LIGHT->"Light";ThemeMode.DARK->"Dark"}) {choosing="Theme"}
                HorizontalDivider(Modifier.padding(horizontal=20.dp))
                SettingsRow("View","${if(mode==NoteViewMode.GRID) "Grid" else "List"} view") {choosing="View"}
                HorizontalDivider(Modifier.padding(horizontal=20.dp))
                SettingsRow("Sort notes",sort.label()) {choosing="Sort notes"}
            }
            SettingsGroup("Handwriting") {
                Row(Modifier.fillMaxWidth().clickable {fingerDrawing=!fingerDrawing;inkPreferences.fingerDrawing=fingerDrawing}.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=16.dp)) {
                        Text("Finger drawing",style=MaterialTheme.typography.bodyLarge)
                        Text("Turn off to write with a stylus and scroll with your fingers.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                    }
                    Switch(fingerDrawing,{fingerDrawing=it;inkPreferences.fingerDrawing=it})
                }
            }
            SettingsGroup("About") {
                SettingsRow("Drawly","Version $version") {choosing="About Drawly"}
            }
        }
    }
    choosing?.let {section->AlertDialog(onDismissRequest={choosing=null},title={Text(section)},text={Column {
        when(section) {
            "Theme"->ThemeMode.entries.forEach {option->SettingChoice(when(option) {ThemeMode.SYSTEM->"Use system settings";ThemeMode.LIGHT->"Light";ThemeMode.DARK->"Dark"},theme==option) {scope.launch {settings.setThemeMode(option)};choosing=null}}
            "View"->NoteViewMode.entries.forEach {option->SettingChoice(if(option==NoteViewMode.GRID) "Grid view" else "List view",mode==option) {scope.launch {settings.setViewMode(option)};choosing=null}}
            "Sort notes"->NoteSort.entries.forEach {option->SettingChoice(option.label(),sort==option) {scope.launch {settings.setNoteSort(option)};choosing=null}}
            else->Text("Notes save automatically on this device. Handwriting, text, images, recordings and PDF annotations stay with your note.")
        }
    }},confirmButton={TextButton(onClick={choosing=null}) {Text("Close")}})}
}

@Composable
private fun SettingsRow(title:String,subtitle:String,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=20.dp,vertical=16.dp)) {
        Text(title,style=MaterialTheme.typography.bodyLarge)
        Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=5.dp))
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(vertical = 6.dp), content = content) }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = selected, onClick = onClick)
    }
}
