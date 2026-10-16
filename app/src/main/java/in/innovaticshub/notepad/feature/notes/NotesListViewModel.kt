package `in`.innovaticshub.notepad.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import `in`.innovaticshub.notepad.data.prefs.*
import `in`.innovaticshub.notepad.di.AppContainer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class NotesListUiState(
    val notes: List<NoteEntity> = emptyList(),
    val folderId: String? = null,
    val folderName: String = "All notes",
    val query: String = "",
    val pinnedOnly: Boolean = false,
    val viewMode: NoteViewMode = NoteViewMode.GRID,
    val sort: NoteSort = NoteSort.MODIFIED,
    val selection: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
) { val isInSelectionMode get() = selection.isNotEmpty() }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotesListViewModel(private val container: AppContainer) : ViewModel() {
    private val filters = MutableStateFlow(NotesListUiState())
    private val notes = container.settingsRepository.noteSort.flatMapLatest {
        container.noteRepository.observeNotes(null, it)
    }
    val uiState = combine(notes, filters, container.settingsRepository.noteSort,
        container.settingsRepository.viewMode) { all, filter, sort, mode ->
        val visible = all.filter { note ->
            (filter.folderId == null || note.folderId == filter.folderId) &&
                (!filter.pinnedOnly || note.isPinned) &&
                (filter.query.isBlank() || note.title.contains(filter.query, true) ||
                    (!note.isLocked && note.searchText.contains(filter.query, true)))
        }
        filter.copy(notes = visible, sort = sort, viewMode = mode, isLoading = false,
            selection = filter.selection.intersect(visible.map { it.id }.toSet()))
    }.catch { emit(filters.value.copy(isLoading = false, error = "Couldn’t load your notes. Please reopen the app.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotesListUiState())

    fun setLocation(folderId: String? = null, name: String = "All notes", pinned: Boolean = false) {
        filters.update { it.copy(folderId = folderId, folderName = name, pinnedOnly = pinned, selection = emptySet(), query = "") }
    }
    fun search(query: String) { filters.update { it.copy(query = query, selection = emptySet()) } }
    fun setViewMode(mode: NoteViewMode) { viewModelScope.launch { container.settingsRepository.setViewMode(mode) } }
    fun setSort(sort: NoteSort) { viewModelScope.launch { container.settingsRepository.setNoteSort(sort) } }
    fun toggleSelected(id: String) { filters.update { it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id) } }
    fun clearSelection() { filters.update { it.copy(selection = emptySet()) } }
    fun selectAll() { filters.update { it.copy(selection = uiState.value.notes.map { note -> note.id }.toSet()) } }
    fun dismissError() { filters.update { it.copy(error = null) } }
    fun trashSelected() = updateSelected { container.noteRepository.trashNote(it) }
    fun pinSelected(pinned: Boolean) = updateSelected { container.noteRepository.setPinned(it, pinned) }
    fun moveSelectedToFolder(folder: String?) = updateSelected { container.noteRepository.setFolder(it, folder) }
    private fun updateSelected(action: suspend (String) -> Unit) {
        val ids = uiState.value.selection.toList()
        viewModelScope.launch {
            runCatching { ids.forEach { action(it) } }
                .onSuccess { clearSelection() }
                .onFailure { filters.update { it.copy(error = "Couldn’t update these notes. Please try again.") } }
        }
    }
    suspend fun createNote(checklist: Boolean = false): String? = runCatching {
        container.noteRepository.createNote(folderId = filters.value.folderId,
            initialBlocks = if (checklist) listOf(NoteBlock(NoteBlock.newId(), BlockType.CHECKLIST_ITEM, 0, isChecked = false)) else emptyList()).id
    }.onFailure { filters.update { it.copy(error = "Couldn’t create a note. Please try again.") } }.getOrNull()
    companion object { fun factory(container: AppContainer) = viewModelFactory { initializer { NotesListViewModel(container) } } }
}
