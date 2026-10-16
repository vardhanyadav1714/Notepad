package `in`.innovaticshub.notepad.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import `in`.innovaticshub.notepad.data.prefs.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(viewModel: NotesListViewModel, folders: List<FolderEntity>,
    onNoteClick: (String) -> Unit, onOpenMenu: () -> Unit, onOpenFolders: () -> Unit,
    onOpenCanvas: () -> Unit, showNavigation: Boolean = true) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var sortMenu by remember { mutableStateOf(false) }
    var optionsMenu by remember { mutableStateOf(false) }
    var moveSheet by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var searching by remember {mutableStateOf(false)}
    val snackbar = remember { SnackbarHostState() }
    BackHandler(state.isInSelectionMode) { viewModel.clearSelection() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() } }
    fun create(checklist: Boolean) {
        if (creating) return
        creating = true
        scope.launch { try { viewModel.createNote(checklist)?.let(onNoteClick) } finally { creating = false } }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
            if(!state.isInSelectionMode && !searching && state.query.isBlank()) Box(Modifier.fillMaxWidth().height(96.dp),contentAlignment=Alignment.Center) {
                Text(state.folderName,fontSize=32.sp,fontWeight=FontWeight.Normal,maxLines=1)
            }
            TopAppBar(title = {
                if (state.isInSelectionMode) Text("${state.selection.size} selected", style = MaterialTheme.typography.titleLarge)
                else if (searching || state.query.isNotBlank()) TextField(value = state.query, onValueChange = viewModel::search,
                    placeholder = { Text("Search notes") }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            },
                navigationIcon = { if (showNavigation || state.isInSelectionMode) IconButton(onClick = { if (state.isInSelectionMode) viewModel.clearSelection() else onOpenMenu() }) {
                    Icon(if (state.isInSelectionMode) Icons.Outlined.Close else Icons.Outlined.Menu,
                        if (state.isInSelectionMode) "Clear selection" else "Open navigation")
                } },
                actions = {
                    if (state.isInSelectionMode) {
                        TextButton(onClick = viewModel::selectAll) { Text("All") }
                    } else {
                        IconButton(onClick = {if(searching) viewModel.search("");searching=!searching}) {Icon(if(searching) Icons.Outlined.Close else Icons.Outlined.Search,if(searching) "Close search" else "Search notes")}
                        Box {
                            IconButton(onClick = {optionsMenu=true}) {Icon(Icons.Outlined.MoreVert,"Library options")}
                            DropdownMenu(optionsMenu,{optionsMenu=false}) {
                                DropdownMenuItem(text={Text(if(state.viewMode==NoteViewMode.GRID) "List view" else "Grid view")},
                                    onClick={viewModel.setViewMode(if(state.viewMode==NoteViewMode.GRID) NoteViewMode.LIST else NoteViewMode.GRID);optionsMenu=false})
                                DropdownMenuItem(text={Text("Select notes")},enabled=state.notes.isNotEmpty(),onClick={state.notes.firstOrNull()?.let {viewModel.toggleSelected(it.id)};optionsMenu=false})
                                DropdownMenuItem(text={Text("Manage folders")},onClick={optionsMenu=false;onOpenFolders()})
                                DropdownMenuItem(text={Text("Sketchbook")},onClick={optionsMenu=false;onOpenCanvas()})
                            }
                        }
                    }
                }, windowInsets=WindowInsets(0,0,0,0), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }
        },
        bottomBar = {
            if(state.isInSelectionMode) Surface(color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton(onClick={moveSheet=true}) {Icon(Icons.Outlined.DriveFileMove,null);Spacer(Modifier.width(6.dp));Text("Move")}
                    TextButton(onClick={viewModel.pinSelected(!state.notes.filter {it.id in state.selection}.all {it.isPinned})}) {Icon(Icons.Outlined.StarBorder,null);Spacer(Modifier.width(6.dp));Text("Favourite")}
                    TextButton(onClick={deleteDialog=true}) {Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(6.dp));Text("Delete")}
                }
            }
        },
        floatingActionButton = {
            if (!state.isInSelectionMode) {
                FloatingActionButton(onClick = { create(false) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = androidx.compose.foundation.shape.CircleShape) {Icon(Icons.Outlined.Edit,"Create note")}
            }
        }) { padding ->
        LazyVerticalGrid(columns = if (state.viewMode == NoteViewMode.GRID) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(padding)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        if(state.query.isNotBlank()) Text("Search results",style=MaterialTheme.typography.labelLarge)
                        Text("${state.notes.size} ${if (state.notes.size == 1) "note" else "notes"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                    TextButton(onClick = { sortMenu = true },contentPadding=PaddingValues(horizontal=4.dp)) {
                        Text(state.sort.label(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ExpandMore,"Sort notes",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            NoteSort.entries.forEach { sort ->
                                DropdownMenuItem(text = { Text(sort.label()) },
                                    leadingIcon = { if (sort == state.sort) Icon(Icons.Outlined.Check, null) },
                                    onClick = { viewModel.setSort(sort); sortMenu = false })
                            }
                        }
                    }
                }
            }
            if (state.isLoading) item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.notes.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().heightIn(min=300.dp).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Text(if(state.query.isNotBlank()) "No results found" else "No notes",fontSize=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(if(state.query.isNotBlank()) "Try a different search." else if(state.pinnedOnly) "Notes you add to favourites will appear here." else "Tap the Create note button to add a note.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            items(state.notes, key = { it.id }) { note ->
                NoteCard(note, state.viewMode, note.id in state.selection,
                    onClick = { if (state.isInSelectionMode) viewModel.toggleSelected(note.id) else onNoteClick(note.id) },
                    onLongClick = { viewModel.toggleSelected(note.id) })
            }
        }
    }
    if (moveSheet) ModalBottomSheet(onDismissRequest = { moveSheet = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text("Move to a folder", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { viewModel.moveSelectedToFolder(null); moveSheet = false }) { Text("No folder") }
            folders.forEach { folder ->
                TextButton(onClick = { viewModel.moveSelectedToFolder(folder.id); moveSheet = false }) {
                    FolderColorDot(folder.colorArgb); Spacer(Modifier.width(12.dp)); Text(folder.name)
                }
            }
        }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false },
        title = { Text("Move to trash?") }, text = { Text("You can restore these notes from Trash.") },
        confirmButton = { TextButton(onClick = { viewModel.trashSelected(); deleteDialog = false }) { Text("Move to trash") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep notes") } })
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), modifier = modifier) {
        Column(Modifier.padding(vertical = 17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(7.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun NotebookEmptyState(title: String, description: String, icon: ImageVector, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(84.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.let { Spacer(Modifier.height(20.dp)); it() }
    }
}

fun NoteSort.label() = when (this) { NoteSort.MODIFIED -> "Last edited"; NoteSort.CREATED -> "Date created"; NoteSort.TITLE -> "Title, A–Z" }
