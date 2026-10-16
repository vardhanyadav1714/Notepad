package `in`.innovaticshub.notepad.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.innovaticshub.notepad.data.db.entity.PdfPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfPageDao {

    @Query("SELECT * FROM pdf_pages WHERE blockId = :blockId ORDER BY pageIndex ASC")
    fun observeForBlock(blockId: String): Flow<List<PdfPageEntity>>

    @Query("SELECT * FROM pdf_pages WHERE blockId = :blockId ORDER BY pageIndex ASC")
    suspend fun getForBlock(blockId: String): List<PdfPageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<PdfPageEntity>)

    @Query("DELETE FROM pdf_pages WHERE blockId = :blockId")
    suspend fun deleteForBlock(blockId: String)
}
