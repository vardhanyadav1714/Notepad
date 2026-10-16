package `in`.innovaticshub.notepad.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.innovaticshub.notepad.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    //region Listing

    @Query(
        "SELECT * FROM notes WHERE trashedAt IS NULL " +
            "AND (:folderId IS NULL OR folderId = :folderId) " +
            "ORDER BY isPinned DESC, modifiedAt DESC"
    )
    fun observeByModified(folderId: String?): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE trashedAt IS NULL " +
            "AND (:folderId IS NULL OR folderId = :folderId) " +
            "ORDER BY isPinned DESC, createdAt DESC"
    )
    fun observeByCreated(folderId: String?): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE trashedAt IS NULL " +
            "AND (:folderId IS NULL OR folderId = :folderId) " +
            "ORDER BY isPinned DESC, title COLLATE NOCASE ASC"
    )
    fun observeByTitle(folderId: String?): Flow<List<NoteEntity>>

    //endregion

    //region Single note

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    //endregion

    //region Search (FTS)

    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid " +
            "WHERE notes_fts MATCH :matchQuery AND notes.trashedAt IS NULL " +
            "ORDER BY notes.modifiedAt DESC"
    )
    fun search(matchQuery: String): Flow<List<NoteEntity>>

    //endregion

    //region Trash

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun getTrashOlderThan(cutoff: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE trashedAt IS NOT NULL")
    suspend fun getTrashed(): List<NoteEntity>

    @Query("UPDATE notes SET trashedAt = :trashedAt WHERE id = :id")
    suspend fun setTrashed(id: String, trashedAt: Long?)

    @Query("UPDATE notes SET trashedAt = :trashedAt WHERE folderId = :folderId")
    suspend fun setTrashedForFolder(folderId: String, trashedAt: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun purgeById(id: String)

    @Query("DELETE FROM notes WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun purgeTrashOlderThan(cutoff: Long)

    //endregion

    //region Property updates

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE notes SET colorArgb = :colorArgb WHERE id = :id")
    suspend fun setColor(id: String, colorArgb: Int)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: String, folderId: String?)

    @Query("UPDATE notes SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: String, locked: Boolean)

    @Query("UPDATE notes SET pageStyleKey = :styleKey, paperColorArgb = :paperColorArgb WHERE id = :id")
    suspend fun setPageStyle(id: String, styleKey: String, paperColorArgb: Int)

    @Query(
        "UPDATE notes SET title = :title, searchText = :searchText, " +
            "modifiedAt = :modifiedAt, thumbnailPath = :thumbnailPath WHERE id = :id"
    )
    suspend fun updateSaveState(
        id: String,
        title: String,
        searchText: String,
        modifiedAt: Long,
        thumbnailPath: String?
    )

    //endregion
}
