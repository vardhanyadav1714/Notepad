package `in`.innovaticshub.notepad.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import `in`.innovaticshub.notepad.data.db.entity.NoteBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBlockDao {

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    fun observeForNote(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY position ASC")
    suspend fun getForNote(noteId: String): List<NoteBlockEntity>

    @Query("SELECT * FROM note_blocks WHERE id = :id")
    suspend fun getById(id: String): NoteBlockEntity?

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId AND id NOT IN (:retainedIds)")
    suspend fun deleteMissing(noteId: String, retainedIds: List<String>)

    @Upsert
    suspend fun insertAll(blocks: List<NoteBlockEntity>)

    /**
     * The editor always saves the complete block list, so replace-all inside
     * one transaction keeps saves atomic.
     */
    @Transaction
    suspend fun bulkReplace(noteId: String, blocks: List<NoteBlockEntity>) {
        // Updating retained rows preserves their PDF pages and annotations.
        deleteMissing(noteId, blocks.map { it.id })
        insertAll(blocks)
    }
}
