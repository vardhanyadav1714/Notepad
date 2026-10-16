package `in`.innovaticshub.notepad.data

import android.content.Context
import androidx.room.withTransaction
import `in`.innovaticshub.notepad.data.content.BlockMetadataJson
import `in`.innovaticshub.notepad.data.content.BlockType
import `in`.innovaticshub.notepad.data.content.DrawingPayload
import `in`.innovaticshub.notepad.data.content.ImagePayload
import `in`.innovaticshub.notepad.data.content.ListType
import `in`.innovaticshub.notepad.data.content.NoteBlock
import `in`.innovaticshub.notepad.data.content.PdfPayload
import `in`.innovaticshub.notepad.data.content.RecordingPayload
import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.data.content.RichTextJson
import `in`.innovaticshub.notepad.data.content.TextAlign
import `in`.innovaticshub.notepad.data.db.DrawlyDatabase
import `in`.innovaticshub.notepad.data.db.entity.NoteBlockEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteTagCrossRef
import `in`.innovaticshub.notepad.data.db.entity.TagEntity
import `in`.innovaticshub.notepad.data.prefs.NoteSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owner of note persistence: listing, search, editing, trash, and the
 * file-per-block payloads (strokes/images/PDFs/recordings) that live under
 * filesDir beside the database.
 */
class NoteRepository(
    private val database: DrawlyDatabase,
    private val context: Context
) {
    private val noteDao = database.noteDao()
    private val blockDao = database.noteBlockDao()
    private val tagDao = database.tagDao()
    private val pdfPageDao = database.pdfPageDao()

    //region Listing

    fun observeNotes(folderId: String?, sort: NoteSort): Flow<List<NoteEntity>> =
        when (sort) {
            NoteSort.MODIFIED -> noteDao.observeByModified(folderId)
            NoteSort.CREATED -> noteDao.observeByCreated(folderId)
            NoteSort.TITLE -> noteDao.observeByTitle(folderId)
        }

    fun observeNote(id: String): Flow<NoteEntity?> = noteDao.observeById(id)

    fun observeTrash(): Flow<List<NoteEntity>> = noteDao.observeTrash()

    suspend fun getNote(id: String): NoteEntity? = noteDao.getById(id)

    suspend fun getTrashed(): List<NoteEntity> = noteDao.getTrashed()

    //endregion

    //region Blocks

    fun observeBlocks(noteId: String): Flow<List<NoteBlock>> =
        blockDao.observeForNote(noteId).map { list -> list.map(::toDomain) }

    suspend fun getBlocks(noteId: String): List<NoteBlock> =
        blockDao.getForNote(noteId).map(::toDomain)

    //endregion

    //region Create / save

    suspend fun createNote(
        folderId: String? = null,
        title: String = "",
        initialBlocks: List<NoteBlock> = emptyList(),
        isLocked: Boolean = false
    ): NoteEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            folderId = folderId,
            createdAt = now,
            modifiedAt = now,
            isLocked = isLocked
        )
        val blocks = if (initialBlocks.isEmpty()) {
            listOf(NoteBlock(id = NoteBlock.newId(), type = BlockType.TEXT, position = 0))
        } else {
            initialBlocks
        }
        database.withTransaction {
            noteDao.insert(note)
            blockDao.bulkReplace(note.id, blocks.map { toEntity(it, note.id) })
        }
        note
    }

    /**
     * Atomic full-note save: replaces the block list and updates the note's
     * title/search state in one transaction.
     */
    suspend fun saveNote(
        noteId: String,
        title: String,
        blocks: List<NoteBlock>,
        thumbnailPath: String? = null
    ) = withContext(Dispatchers.IO) {
        val preview = thumbnailPath ?: blocks.firstNotNullOfOrNull { block ->
            block.image?.path?.let { File(context.filesDir, it).absolutePath }
                ?: block.drawing?.let { `in`.innovaticshub.notepad.ui.canvas.drawing.NoteThumbnailRenderer.render(context, noteId, it) }
        }
        database.withTransaction {
            val note = noteDao.getById(noteId) ?: return@withTransaction
            blockDao.bulkReplace(noteId, blocks.map { toEntity(it, noteId) })
            noteDao.updateSaveState(
                id = noteId,
                title = title,
                searchText = buildSearchText(title, blocks),
                modifiedAt = System.currentTimeMillis(),
                thumbnailPath = preview
            )
        }
    }

    /** Adds a drawing block without touching the rest of the note (migration path). */
    suspend fun addDrawingBlock(
        noteId: String,
        blockId: String,
        strokesFile: String,
        backgroundArgb: Int,
        position: Int = 0
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val entity = NoteBlockEntity(
                id = blockId,
                noteId = noteId,
                position = position,
                type = BlockType.DRAWING.name,
                metadata = BlockMetadataJson.drawingToJson(
                    DrawingPayload(strokesFile, backgroundArgb)
                )
            )
            blockDao.bulkReplace(noteId, listOf(entity))
        }
    }

    //endregion

    //region Property updates

    suspend fun setPinned(id: String, pinned: Boolean) = noteDao.setPinned(id, pinned)
    suspend fun setColor(id: String, colorArgb: Int) = noteDao.setColor(id, colorArgb)
    suspend fun setFolder(id: String, folderId: String?) = noteDao.setFolder(id, folderId)
    suspend fun setLocked(id: String, locked: Boolean) = noteDao.setLocked(id, locked)
    suspend fun setPageStyle(id: String, styleKey: String, paperColorArgb: Int) =
        noteDao.setPageStyle(id, styleKey, paperColorArgb)

    suspend fun moveNotesToFolder(noteIds: List<String>, folderId: String?) {
        noteIds.forEach { noteDao.setFolder(it, folderId) }
    }

    //endregion

    //region Trash

    suspend fun trashNote(id: String) = noteDao.setTrashed(id, System.currentTimeMillis())

    suspend fun trashNotesInFolder(folderId: String) =
        noteDao.setTrashedForFolder(folderId, System.currentTimeMillis())

    suspend fun restoreNote(id: String) = noteDao.setTrashed(id, null)

    /** Purges one note: rows cascade, then block payload files are removed. */
    suspend fun purgeNote(id: String) = withContext(Dispatchers.IO) {
        val blocks = blockDao.getForNote(id)
        val files = blocks.flatMap { blockFilePaths(it) }
        database.withTransaction { noteDao.purgeById(id) }
        files.forEach { relative -> File(context.filesDir, relative).delete() }
        File(context.filesDir, "thumbnails/$id.png").delete()
    }

    suspend fun purgeTrashOlderThan(cutoff: Long) = withContext(Dispatchers.IO) {
        noteDao.getTrashOlderThan(cutoff).forEach { note -> purgeNote(note.id) }
    }

    /** Relative file paths owned by a block, for cleanup on purge. */
    suspend fun blockFilePaths(block: NoteBlockEntity): List<String> {
        val paths = mutableListOf<String>()
        when (BlockType.fromName(block.type)) {
            BlockType.IMAGE ->
                BlockMetadataJson.imageFrom(block.metadata)?.let { paths.add(it.path) }
            BlockType.DRAWING ->
                BlockMetadataJson.drawingFrom(block.metadata)?.let { drawing ->
                    paths.add(drawing.strokesFile)
                    runCatching { `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage.loadDocument(context,drawing.strokesFile) }
                        .getOrNull()?.assets?.filter {it.startsWith("pdfs/") || it.startsWith("images/") || it.startsWith("recordings/") }?.let(paths::addAll)
                }
            BlockType.RECORDING ->
                BlockMetadataJson.recordingFrom(block.metadata)?.let { paths.add(it.path) }
            BlockType.PDF -> {
                BlockMetadataJson.pdfFrom(block.metadata)?.let { paths.add(it.file) }
                pdfPageDao.getForBlock(block.id).forEach { page ->
                    paths.add(page.renderedBitmapPath)
                    paths.add(page.annotationsFile)
                }
            }
            else -> Unit
        }
        return paths
    }

    //endregion

    //region Search

    fun search(query: String): Flow<List<NoteEntity>> {
        val match = ftsMatchQuery(query)
        return if (match.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            noteDao.search(match)
        }
    }

    private fun ftsMatchQuery(query: String): String =
        query.trim().split(Regex("\\s+"))
            .map { term -> term.replace(Regex("[\"'*()\\-:^]"), "") }
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }

    //endregion

    //region Tags

    suspend fun tagsForNote(noteId: String): List<TagEntity> {
        // TagDao.observeTagsForNote is a Flow; use a one-shot read via the same join.
        val all = mutableListOf<TagEntity>()
        tagDao.observeTagsForNote(noteId).map { list ->
            all.clear()
            all.addAll(list)
        }.let { }
        return all
    }

    /** Finds or creates the tag by name (without '#'), then links the note. */
    suspend fun addTagToNote(noteId: String, tagName: String) = withContext(Dispatchers.IO) {
        val name = tagName.removePrefix("#").trim()
        if (name.isEmpty()) return@withContext
        val tag = tagDao.findByName(name) ?: run {
            val created = TagEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                createdAt = System.currentTimeMillis()
            )
            tagDao.insert(created)
            created
        }
        tagDao.addTagToNote(NoteTagCrossRef(noteId, tag.id))
    }

    suspend fun removeTagFromNote(noteId: String, tagId: String) =
        tagDao.removeTagFromNote(noteId, tagId)

    //endregion

    //region Mapping

    private fun toDomain(entity: NoteBlockEntity): NoteBlock = NoteBlock(
        id = entity.id,
        type = BlockType.fromName(entity.type),
        position = entity.position,
        richText = RichTextJson.fromJson(entity.textJson),
        isChecked = entity.isChecked,
        listType = ListType.fromName(entity.listType),
        indentLevel = entity.indentLevel,
        textAlign = TextAlign.fromName(entity.textAlign),
        image = if (BlockType.fromName(entity.type) == BlockType.IMAGE) {
            BlockMetadataJson.imageFrom(entity.metadata)
        } else null,
        drawing = if (BlockType.fromName(entity.type) == BlockType.DRAWING) {
            BlockMetadataJson.drawingFrom(entity.metadata)
        } else null,
        pdf = if (BlockType.fromName(entity.type) == BlockType.PDF) {
            BlockMetadataJson.pdfFrom(entity.metadata)
        } else null,
        recording = if (BlockType.fromName(entity.type) == BlockType.RECORDING) {
            BlockMetadataJson.recordingFrom(entity.metadata)
        } else null
    )

    private fun toEntity(block: NoteBlock, noteId: String): NoteBlockEntity = NoteBlockEntity(
        id = block.id,
        noteId = noteId,
        position = block.position,
        type = block.type.name,
        textJson = if (block.type == BlockType.TEXT) RichTextJson.toJson(block.richText) else null,
        isChecked = if (block.type == BlockType.CHECKLIST_ITEM) block.isChecked == true else null,
        listType = block.listType.name,
        indentLevel = block.indentLevel,
        textAlign = block.textAlign.name,
        metadata = when (block.type) {
            BlockType.IMAGE -> block.image?.let { BlockMetadataJson.imageToJson(it) }
            BlockType.DRAWING -> block.drawing?.let { BlockMetadataJson.drawingToJson(it) }
            BlockType.PDF -> block.pdf?.let { BlockMetadataJson.pdfToJson(it) }
            BlockType.RECORDING -> block.recording?.let { BlockMetadataJson.recordingToJson(it) }
            else -> null
        }
    )

    private fun buildSearchText(title: String, blocks: List<NoteBlock>): String =
        (listOf(title) + blocks.mapNotNull { block ->
            when (block.type) {
                BlockType.TEXT, BlockType.CHECKLIST_ITEM -> block.richText.text.takeIf { it.isNotBlank() }
                else -> null
            }
        }).joinToString("\n")

    //endregion
}
