package `in`.innovaticshub.notepad.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import `in`.innovaticshub.notepad.data.content.BlockType
import `in`.innovaticshub.notepad.data.content.ImagePayload
import `in`.innovaticshub.notepad.data.content.ListType
import `in`.innovaticshub.notepad.data.content.NoteBlock
import `in`.innovaticshub.notepad.data.content.PdfPayload
import `in`.innovaticshub.notepad.data.content.RecordingPayload
import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.data.content.TextAlign
import `in`.innovaticshub.notepad.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val noteId: String? = null,
    val title: String = "",
    val blocks: List<NoteBlock> = emptyList(),
    val noteColorArgb: Int = 0xFFFFFFFF.toInt(),
    val pageStyleKey: String = "PLAIN",
    val paperColorArgb: Int = 0xFFFFFFFF.toInt(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val folderId: String? = null,
    val isLoading: Boolean = true,
    /** True when the note is locked and must be unlocked before content shows. */
    val lockedGate: Boolean = false,
    /** Set when the note could not be loaded (deleted mid-navigation). */
    val loadFailed: Boolean = false,
    val saveError: String? = null,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false
)

/**
 * Editor state holder: owns the block list, per-block formatting props,
 * autosave, and note-level actions (pin/lock/color/trash).
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class NoteEditorViewModel(
    private val requestedNoteId: String?,
    private val initialFolderId: String?,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState = _uiState.asStateFlow()

    private var revision = 0L
    private val saveMutex = Mutex()
    private var saveDirty = false
        set(value) {
            field = value
            if (value) {
                revision++
                _uiState.update { it.copy(hasUnsavedChanges = true) }
            }
        }

    init {
        load()
        viewModelScope.launch {
            _uiState.map { s -> Triple(s.noteId, s.title, s.blocks) }
                .distinctUntilChanged()
                .debounce(800)
                .collect { (noteId, title, blocks) ->
                    if (noteId != null && saveDirty) flushSave(noteId, title, blocks)
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val repo = container.noteRepository
            val note = if (requestedNoteId == null) {
                repo.createNote(folderId = initialFolderId)
            } else {
                repo.getNote(requestedNoteId)
            }
            if (note == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            if (note.isLocked && note.id !in container.unlockedNoteIds) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lockedGate = true,
                        noteId = note.id,
                        title = note.title,
                        isLocked = true,
                        folderId = note.folderId
                    )
                }
                return@launch
            }
            val blocks = repo.getBlocks(note.id)
            _uiState.update {
                it.copy(
                    noteId = note.id,
                    title = note.title,
                    blocks = blocks,
                    noteColorArgb = note.colorArgb,
                    pageStyleKey = note.pageStyleKey,
                    paperColorArgb = note.paperColorArgb,
                    isPinned = note.isPinned,
                    isLocked = note.isLocked,
                    folderId = note.folderId,
                    isLoading = false,
                    lockedGate = false
                )
            }
        }
    }

    /** Called after a successful biometric unlock of a gated note. */
    fun onUnlocked() {
        val id = _uiState.value.noteId ?: return
        container.unlockedNoteIds.add(id)
        _uiState.update { it.copy(isLoading = true) }
        load()
    }

    //region Text edits

    fun updateTitle(title: String) {
        saveDirty = true
        _uiState.update { it.copy(title = title) }
    }

    fun applyDocumentText(items:List<`in`.innovaticshub.notepad.ui.canvas.model.PageText>) {
        val old=_uiState.value.blocks
        val text=items.map {item->NoteBlock(item.id,if(item.checked==null) BlockType.TEXT else BlockType.CHECKLIST_ITEM,0,
            richText=item.content,isChecked=item.checked,textAlign=item.alignment)}
        val blocks=(text+old.filterNot {it.type==BlockType.TEXT || it.type==BlockType.CHECKLIST_ITEM}).mapIndexed {index,b->b.copy(position=index)}
        if(blocks==old) return
        saveDirty=true;_uiState.update {it.copy(blocks=blocks)}
    }

    fun updateBlockText(blockId: String, data: RichTextData) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(blocks = s.blocks.updateWhere(blockId) { it.copy(richText = data) })
        }
    }

    fun toggleChecked(blockId: String) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(
                blocks = s.blocks.updateWhere(blockId) {
                    it.copy(isChecked = !(it.isChecked ?: false))
                }
            )
        }
    }

    fun setListType(blockId: String, listType: ListType) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(blocks = s.blocks.updateWhere(blockId) { it.copy(listType = listType) })
        }
    }

    fun setTextAlign(blockId: String, align: TextAlign) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(blocks = s.blocks.updateWhere(blockId) { it.copy(textAlign = align) })
        }
    }

    fun setIndent(blockId: String, indentLevel: Int) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(
                blocks = s.blocks.updateWhere(blockId) {
                    it.copy(indentLevel = indentLevel.coerceIn(0, 2))
                }
            )
        }
    }

    //endregion

    //region Block structure

    /** Adds a TEXT block after [afterBlockId] (or at the end) and returns its id. */
    fun addTextBlock(afterBlockId: String? = null): String {
        saveDirty = true
        val block = NoteBlock(
            id = NoteBlock.newId(),
            type = BlockType.TEXT,
            position = 0
        )
        insertBlock(block, afterBlockId)
        return block.id
    }

    /** Adds a CHECKLIST_ITEM after [afterBlockId] (or at the end). */
    fun addChecklistItem(afterBlockId: String? = null): String {
        saveDirty = true
        val block = NoteBlock(
            id = NoteBlock.newId(),
            type = BlockType.CHECKLIST_ITEM,
            position = 0,
            isChecked = false
        )
        insertBlock(block, afterBlockId)
        return block.id
    }

    fun removeBlock(blockId: String) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(blocks = s.blocks.filterNot { it.id == blockId }.repositioned())
        }
    }

    fun attachImage(blockId: String, payload: ImagePayload) {
        saveDirty = true
        upsertPayloadBlock(blockId, BlockType.IMAGE) {
            it.copy(image = payload)
        }
    }

    fun attachDrawing(
        blockId: String,
        strokesFile: String,
        backgroundArgb: Int,
        collabRoom: String? = null
    ) {
        saveDirty = true
        upsertPayloadBlock(blockId, BlockType.DRAWING) {
            it.copy(
                drawing = `in`.innovaticshub.notepad.data.content.DrawingPayload(
                    strokesFile,
                    backgroundArgb,
                    collabRoom
                )
            )
        }
    }

    fun setDrawingCollabRoom(blockId: String, collabRoom: String?) {
        saveDirty = true
        _uiState.update { s ->
            s.copy(
                blocks = s.blocks.updateWhere(blockId) { block ->
                    val drawing = block.drawing ?: return@updateWhere block
                    block.copy(drawing = drawing.copy(collabRoom = collabRoom))
                }
            )
        }
    }

    fun attachPdf(blockId: String, payload: PdfPayload) {
        saveDirty = true
        upsertPayloadBlock(blockId, BlockType.PDF) { it.copy(pdf = payload) }
    }

    fun attachRecording(blockId: String, payload: RecordingPayload) {
        saveDirty = true
        upsertPayloadBlock(blockId, BlockType.RECORDING) { it.copy(recording = payload) }
    }

    private fun upsertPayloadBlock(
        blockId: String,
        type: BlockType,
        edit: (NoteBlock) -> NoteBlock
    ) {
        _uiState.update { s ->
            val existing = s.blocks.firstOrNull { it.id == blockId }
            val blocks = if (existing != null) {
                s.blocks.updateWhere(blockId, edit)
            } else {
                val fresh = edit(NoteBlock(id = blockId, type = type, position = 0))
                s.blocks + fresh
            }
            s.copy(blocks = blocks.repositioned())
        }
    }

    private fun insertBlock(block: NoteBlock, afterBlockId: String?) {
        _uiState.update { s ->
            val index = afterBlockId?.let { id -> s.blocks.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?.plus(1)
                ?: s.blocks.size
            val blocks = s.blocks.toMutableList()
            blocks.add(index, block)
            s.copy(blocks = blocks.repositioned())
        }
    }

    //endregion

    //region Note properties

    fun setPinned(pinned: Boolean) {
        val id = _uiState.value.noteId ?: return
        _uiState.update { it.copy(isPinned = pinned) }
        viewModelScope.launch { container.noteRepository.setPinned(id, pinned) }
    }

    fun setNoteColor(argb: Int) {
        val id = _uiState.value.noteId ?: return
        _uiState.update { it.copy(noteColorArgb = argb) }
        viewModelScope.launch { container.noteRepository.setColor(id, argb) }
    }

    fun setLocked(locked: Boolean) {
        val id = _uiState.value.noteId ?: return
        _uiState.update { it.copy(isLocked = locked) }
        viewModelScope.launch {
            container.noteRepository.setLocked(id, locked)
            if (!locked) container.unlockedNoteIds.add(id)
        }
    }

    fun setPageStyle(styleKey: String, paperColorArgb: Int) {
        val id = _uiState.value.noteId ?: return
        _uiState.update { it.copy(pageStyleKey = styleKey, paperColorArgb = paperColorArgb) }
        viewModelScope.launch { container.noteRepository.setPageStyle(id, styleKey, paperColorArgb) }
    }

    fun moveToFolder(folderId: String?) {
        val id = _uiState.value.noteId ?: return
        _uiState.update { it.copy(folderId = folderId) }
        viewModelScope.launch { container.noteRepository.setFolder(id, folderId) }
    }

    fun trashNote(onDone: () -> Unit) {
        val id = _uiState.value.noteId ?: return
        viewModelScope.launch {
            if (!saveBeforeLeaving()) return@launch
            container.noteRepository.trashNote(id)
            onDone()
        }
    }

    //endregion

    //region Saving

    /** Flushes pending edits immediately (ON_STOP, navigation away). */
    fun flushSaveNow() {
        val s = _uiState.value
        val noteId = s.noteId ?: return
        viewModelScope.launch { flushSave(noteId, s.title, s.blocks) }
    }

    private suspend fun flushSave(noteId: String, title: String, blocks: List<NoteBlock>) {
        saveMutex.withLock {
            val current = _uiState.value
            if (!saveDirty || current.lockedGate || current.isLoading) return@withLock
            val savingRevision = revision
            _uiState.update { it.copy(isSaving = true) }
            runCatching { container.noteRepository.saveNote(noteId, current.title, current.blocks) }
                .onSuccess {
                    if (revision == savingRevision) saveDirty = false
                    _uiState.update { it.copy(saveError = null, hasUnsavedChanges = saveDirty) }
                }
                .onFailure { _uiState.update { it.copy(saveError = "Couldn’t save your changes. Tap to retry.") } }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    suspend fun saveBeforeLeaving(): Boolean {
        val s = _uiState.value
        val id = s.noteId ?: return true
        flushSave(id, s.title, s.blocks)
        return !saveDirty
    }

    override fun onCleared() {
        val s = _uiState.value
        val noteId = s.noteId
        if (noteId != null && saveDirty) {
            // viewModelScope is cancelled in onCleared; use an app-scope launch.
            container.applicationScope.launch {
                container.noteRepository.saveNote(noteId, s.title, s.blocks)
            }
        }
        super.onCleared()
    }

    //endregion

    private fun List<NoteBlock>.updateWhere(
        blockId: String,
        edit: (NoteBlock) -> NoteBlock
    ): List<NoteBlock> = map { if (it.id == blockId) edit(it) else it }

    private fun List<NoteBlock>.repositioned(): List<NoteBlock> =
        mapIndexedNotNull { index, block ->
            if (block.isEmptyPayload() && block.type in payloadTypes) null
            else block.copy(position = index)
        }

    private fun NoteBlock.isEmptyPayload(): Boolean = when (type) {
        BlockType.IMAGE -> image == null
        BlockType.DRAWING -> drawing == null
        BlockType.PDF -> pdf == null
        BlockType.RECORDING -> recording == null
        else -> false
    }

    companion object {
        private val payloadTypes = setOf(
            BlockType.IMAGE, BlockType.DRAWING, BlockType.PDF, BlockType.RECORDING
        )

        fun factory(
            noteId: String?,
            folderId: String?,
            container: AppContainer
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoteEditorViewModel(noteId, folderId, container)
            }
        }
    }
}
