package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A note's stable metadata. The note *content* lives in [NoteBlockEntity] rows
 * plus per-block files (strokes/images/PDFs/recordings).
 *
 * [rowId] exists only to give the FTS4 external-content table
 * ([NoteFtsEntity]) an integer rowid to join on; [id] is the public identity
 * used by every foreign key.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["id"], unique = true),
        Index("folderId"),
        Index("isPinned"),
        Index("modifiedAt"),
        Index("trashedAt")
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    @ColumnInfo(name = "id") val id: String,
    val title: String = "",
    /** Denormalized title + all text/checklist text, rebuilt on every save. */
    @ColumnInfo(name = "searchText") val searchText: String = "",
    val folderId: String? = null,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val isPinned: Boolean = false,
    val isLocked: Boolean = false,
    val pageStyleKey: String = "PLAIN",
    val paperColorArgb: Int = 0xFFFFFFFF.toInt(),
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    /** Null = live note; non-null = in trash since that timestamp. */
    val trashedAt: Long? = null
)
