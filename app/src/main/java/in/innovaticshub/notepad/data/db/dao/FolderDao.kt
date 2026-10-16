package `in`.innovaticshub.notepad.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val noteCount: Int
)

@Dao
interface FolderDao {

    @Query(
        "SELECT folders.*, " +
            "(SELECT COUNT(*) FROM notes WHERE notes.folderId = folders.id AND notes.trashedAt IS NULL) AS noteCount " +
            "FROM folders WHERE folders.trashedAt IS NULL " +
            "ORDER BY folders.name COLLATE NOCASE ASC"
    )
    fun observeFoldersWithCounts(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE trashedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeLiveFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentId = :parentId AND trashedAt IS NULL")
    suspend fun getChildren(parentId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE trashedAt IS NOT NULL")
    suspend fun getTrashed(): List<FolderEntity>

    @Insert
    suspend fun insert(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE folders SET colorArgb = :colorArgb WHERE id = :id")
    suspend fun setColor(id: String, colorArgb: Int)

    @Query("UPDATE folders SET trashedAt = :trashedAt WHERE id = :id")
    suspend fun setTrashed(id: String, trashedAt: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun purgeById(id: String)
}
