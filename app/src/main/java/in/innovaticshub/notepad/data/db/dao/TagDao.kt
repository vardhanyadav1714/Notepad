package `in`.innovaticshub.notepad.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import `in`.innovaticshub.notepad.data.db.entity.NoteTagCrossRef
import `in`.innovaticshub.notepad.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

data class TagWithCount(
    @Embedded val tag: TagEntity,
    val noteCount: Int
)

@Dao
interface TagDao {

    @Query(
        "SELECT tags.*, " +
            "(SELECT COUNT(*) FROM note_tags WHERE note_tags.tagId = tags.id) AS noteCount " +
            "FROM tags ORDER BY tags.name COLLATE NOCASE ASC"
    )
    fun observeTagsWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query(
        "SELECT tags.* FROM tags JOIN note_tags ON note_tags.tagId = tags.id " +
            "WHERE note_tags.noteId = :noteId ORDER BY tags.name COLLATE NOCASE ASC"
    )
    fun observeTagsForNote(noteId: String): Flow<List<TagEntity>>

    @Query(
        "SELECT notes.* FROM notes JOIN note_tags ON note_tags.noteId = notes.id " +
            "WHERE note_tags.tagId = :tagId AND notes.trashedAt IS NULL " +
            "ORDER BY notes.isPinned DESC, notes.modifiedAt DESC"
    )
    fun observeNotesWithTag(tagId: String): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(tag: TagEntity)

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTagToNote(crossRef: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun removeTagFromNote(noteId: String, tagId: String)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun removeAllTagsFromNote(noteId: String)
}
