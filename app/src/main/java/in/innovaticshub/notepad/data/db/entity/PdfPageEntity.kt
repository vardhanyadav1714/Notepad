package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One rendered page of an imported PDF block. Annotations per page reuse the
 * stroke JSON format; the rendered page bitmap is a PNG under filesDir/pages/.
 */
@Entity(
    tableName = "pdf_pages",
    foreignKeys = [
        ForeignKey(
            entity = NoteBlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("blockId"), Index(value = ["blockId", "pageIndex"])]
)
data class PdfPageEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val pageIndex: Int,
    val annotationsFile: String,
    val renderedBitmapPath: String
)
