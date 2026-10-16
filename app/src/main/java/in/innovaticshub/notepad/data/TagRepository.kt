package `in`.innovaticshub.notepad.data

import `in`.innovaticshub.notepad.data.db.DrawlyDatabase
import `in`.innovaticshub.notepad.data.db.dao.TagWithCount
import `in`.innovaticshub.notepad.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(private val database: DrawlyDatabase) {
    private val tagDao = database.tagDao()

    fun observeTagsWithCounts(): Flow<List<TagWithCount>> = tagDao.observeTagsWithCounts()

    suspend fun getById(id: String): TagEntity? = tagDao.getById(id)

    suspend fun rename(id: String, name: String) {
        if (name.isNotBlank()) tagDao.rename(id, name.trim().removePrefix("#"))
    }

    suspend fun delete(id: String) = tagDao.delete(id)
}
