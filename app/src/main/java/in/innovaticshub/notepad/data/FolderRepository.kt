package `in`.innovaticshub.notepad.data

import androidx.room.withTransaction
import `in`.innovaticshub.notepad.data.db.DrawlyDatabase
import `in`.innovaticshub.notepad.data.db.dao.FolderWithCount
import `in`.innovaticshub.notepad.data.db.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Maximum folder depth: a top-level folder plus one subfolder level,
 * matching the depth limit Samsung Notes enforces.
 */
private const val MAX_DEPTH = 2

class FolderRepository(
    private val database: DrawlyDatabase,
    private val noteRepository: NoteRepository
) {
    private val folderDao = database.folderDao()

    fun observeFoldersWithCounts(): Flow<List<FolderWithCount>> =
        folderDao.observeFoldersWithCounts()

    fun observeLiveFolders(): Flow<List<FolderEntity>> = folderDao.observeLiveFolders()

    suspend fun getById(id: String): FolderEntity? = folderDao.getById(id)

    /**
     * Creates a folder, enforcing the depth cap. Returns null when the parent
     * is already at maximum depth.
     */
    suspend fun create(name: String, colorArgb: Int, parentId: String?): FolderEntity? {
        if (name.isBlank()) return null
        if (parentId != null) {
            val parent = folderDao.getById(parentId) ?: return null
            if (parent.parentId != null) return null // parent is already a subfolder
        }
        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            parentId = parentId,
            colorArgb = colorArgb,
            createdAt = System.currentTimeMillis()
        )
        folderDao.insert(folder)
        return folder
    }

    suspend fun rename(id: String, name: String) {
        if (name.isNotBlank()) folderDao.rename(id, name.trim())
    }

    suspend fun setColor(id: String, colorArgb: Int) = folderDao.setColor(id, colorArgb)

    /** Soft-deletes a folder and all subfolders, trashing their notes. */
    suspend fun trashSubtree(folderId: String) {
        val children = folderDao.getChildren(folderId)
        folderDao.setTrashed(folderId, System.currentTimeMillis())
        children.forEach { child ->
            folderDao.setTrashed(child.id, System.currentTimeMillis())
        }
        noteRepository.trashNotesInFolder(folderId)
        children.forEach { noteRepository.trashNotesInFolder(it.id) }
    }

    suspend fun restore(folderId: String) {
        folderDao.setTrashed(folderId, null)
        folderDao.getChildren(folderId).forEach { child ->
            folderDao.setTrashed(child.id, null)
        }
    }

    /** Permanently deletes a trashed folder subtree and purges its notes. */
    suspend fun purgeSubtree(folderId: String) {
        val children = folderDao.getChildren(folderId)
        database.withTransaction { folderDao.purgeById(folderId) }
        children.forEach { child ->
            database.withTransaction { folderDao.purgeById(child.id) }
        }
    }

    suspend fun getTrashed(): List<FolderEntity> = folderDao.getTrashed()

    suspend fun purgeTrashOlderThan(cutoff: Long) {
        folderDao.getTrashed().filter { (it.trashedAt ?: 0L) < cutoff }.forEach { folder ->
            purgeSubtree(folder.id)
        }
    }
}
