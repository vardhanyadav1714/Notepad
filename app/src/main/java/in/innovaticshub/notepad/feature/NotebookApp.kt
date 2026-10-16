package `in`.innovaticshub.notepad.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import `in`.innovaticshub.notepad.boot.LegacyMigration
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import `in`.innovaticshub.notepad.di.AppContainer
import `in`.innovaticshub.notepad.feature.editor.NoteEditorScreen
import `in`.innovaticshub.notepad.feature.editor.NoteEditorViewModel
import `in`.innovaticshub.notepad.feature.notes.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookApp(container: AppContainer, incomingRoom: String?, isDark: Boolean, onRoomConsumed: () -> Unit) {
    var route by rememberSaveable { mutableStateOf("library") }
    var noteId by rememberSaveable { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var startupError by remember { mutableStateOf(false) }
    var startupAttempt by remember { mutableIntStateOf(0) }
    var room by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val list: NotesListViewModel = viewModel(factory = NotesListViewModel.factory(container))
    val state by list.uiState.collectAsState()
    val folders by remember(container) { container.folderRepository.observeLiveFolders() }.collectAsState(emptyList())
    LaunchedEffect(startupAttempt) {
        startupError = false
        runCatching { withContext(Dispatchers.IO) {
            LegacyMigration.migrateIfNeeded(container.appContext, container.settingsRepository, container.noteRepository)
        } }.onSuccess { ready = true }.onFailure { startupError = true }
    }
    LaunchedEffect(incomingRoom) {
        if (incomingRoom != null) { room = incomingRoom; route = "canvas"; onRoomConsumed() }
    }
    fun openNote(id: String) { noteId = id; route = "editor" }
    fun navigate(destination: String) {
        route = destination
        scope.launch { drawer.close() }
    }
    if (!ready) {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                if (startupError) NotebookEmptyState("Your notebook couldn’t open", "Your notes are still on this device. Try opening them again.", Icons.Outlined.AutoStories) {
                    Button(onClick = { startupAttempt++ }) { Text("Try again") }
                } else CircularProgressIndicator()
            }
        }
        return
    }
    if (route == "editor" && noteId != null) {
        val editorOwner = rememberScreenViewModelOwner(noteId)
        val editor: NoteEditorViewModel = viewModel(viewModelStoreOwner = editorOwner, key = "note-$noteId", factory = NoteEditorViewModel.factory(noteId, null, container))
        NoteEditorScreen(editor, container, folders, isDark, onBack = { route = "library" })
        return
    }
    if (route == "canvas") {
        CanvasWorkspace(container, isDark, roomCode = room, onOpenTextNote=::openNote, onBack = { room = null; route = "library" })
        return
    }
    BackHandler(route != "library" || state.folderId != null || state.pinnedOnly || state.query.isNotBlank()) {
        route = "library"; list.setLocation()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val sidebar: @Composable () -> Unit = {
            NotebookSidebar(route, state, folders, onNavigate = ::navigate,
                onLocation = { id, name, pinned -> list.setLocation(id, name, pinned); navigate("library") })
        }
        val content: @Composable () -> Unit = {
            when (route) {
                "folders" -> FoldersScreen(container, onBack = { navigate("library") }, onOpen = { folder ->
                    list.setLocation(folder.id, folder.name); navigate("library")
                })
                "trash" -> TrashScreen(container, onBack = { navigate("library") })
                "settings" -> SettingsScreen(container, onBack = { navigate("library") })
                else -> NotesListScreen(list, folders, ::openNote,
                    onOpenMenu = { scope.launch { if (wide) navigate("folders") else drawer.open() } },
                    onOpenFolders = { navigate("folders") }, onOpenCanvas = { room = null; navigate("canvas") }, showNavigation = !wide)
            }
        }
        if (wide) Row(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(256.dp).fillMaxHeight()) { sidebar() }
            VerticalDivider()
            Box(Modifier.weight(1f)) { content() }
        } else ModalNavigationDrawer(drawerState = drawer, drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface, drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.widthIn(max = 328.dp)) { sidebar() }
        }, gesturesEnabled = route == "library") { content() }
    }
}

@Composable
internal fun rememberScreenViewModelOwner(key: String?): ViewModelStoreOwner {
    val owner = remember(key) { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    return owner
}

@Composable
private fun NotebookSidebar(route: String, state: NotesListUiState, folders: List<FolderEntity>,
    onNavigate: (String) -> Unit, onLocation: (String?, String, Boolean) -> Unit) {
    Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onNavigate("settings") }) { Icon(Icons.Outlined.Settings, "Settings", Modifier.size(22.dp)) }
        }
        val drawerColors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.onSurface, selectedIconColor = MaterialTheme.colorScheme.onSurface)
        NavigationDrawerItem(label = { Text("All notes", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
            selected = route == "library" && state.folderId == null && !state.pinnedOnly, shape = RoundedCornerShape(12.dp), colors = drawerColors,
            icon = { Icon(Icons.Outlined.Description, null, Modifier.size(21.dp)) }, onClick = { onLocation(null, "All notes", false) })
        NavigationDrawerItem(label = { Text("Favourites") }, selected = route == "library" && state.pinnedOnly, shape = RoundedCornerShape(12.dp), colors = drawerColors,
            icon = { Icon(Icons.Outlined.StarBorder, null, Modifier.size(21.dp)) }, onClick = { onLocation(null, "Favourites", true) })
        NavigationDrawerItem(label = { Text("Trash") }, selected = route == "trash", shape = RoundedCornerShape(12.dp), colors = drawerColors,
            icon = { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(21.dp)) }, onClick = { onNavigate("trash") })
        HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 20.dp))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(21.dp))
            Text("Folders", modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
            Text("${folders.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        folders.forEach { folder ->
            NavigationDrawerItem(label = { Text(folder.name, maxLines = 1) }, selected = state.folderId == folder.id && route == "library",
                shape = RoundedCornerShape(12.dp), colors = drawerColors,
                icon = { Icon(Icons.Outlined.Folder, null, Modifier.size(21.dp), tint = androidx.compose.ui.graphics.Color(folder.colorArgb)) },
                onClick = { onLocation(folder.id, folder.name, false) })
        }
        TextButton(onClick = { onNavigate("folders") }, modifier = Modifier.padding(start = 44.dp, top = 8.dp)) { Text("Manage folders", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(Modifier.padding(horizontal = 14.dp, vertical = 20.dp))
        NavigationDrawerItem(label = { Text("Sketchbook") }, selected = false, shape = RoundedCornerShape(12.dp),
            icon = { Icon(Icons.Outlined.Gesture, null, Modifier.size(21.dp)) }, onClick = { onNavigate("canvas") })
        Spacer(Modifier.height(24.dp))
    }
}
