package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A note folder. [parentId] allows one level of subfolders (depth is enforced
 * in the repository, not the schema).
 */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentId"), Index("name")]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val colorArgb: Int,
    val createdAt: Long,
    val trashedAt: Long? = null
)
