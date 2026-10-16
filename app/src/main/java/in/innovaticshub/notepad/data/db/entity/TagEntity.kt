package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A hashtag-style tag. [name] is stored without the leading '#'. */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

/** Many-to-many join between notes and tags. */
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String
)
