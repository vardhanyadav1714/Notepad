package `in`.innovaticshub.notepad.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ordered content block inside a note: a rich-text paragraph, a checklist
 * item, an image, a drawing, an imported PDF, or a voice recording.
 *
 * Per-type payloads live in [metadata] as JSON (see the payload data classes
 * in `data.content.NoteBlock`); strokes always live in a separate file
 * referenced by that JSON, never in the database.
 */
@Entity(
    tableName = "note_blocks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index(value = ["noteId", "position"])]
)
data class NoteBlockEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val position: Int,
    /** One of BlockType names: TEXT, CHECKLIST_ITEM, IMAGE, DRAWING, PDF, RECORDING. */
    val type: String,
    /** TEXT blocks: rich-text span JSON produced by RichTextJson. */
    val textJson: String? = null,
    /** CHECKLIST_ITEM only. */
    val isChecked: Boolean? = null,
    /** TEXT blocks: NONE, BULLET, or NUMBERED. */
    val listType: String = "NONE",
    /** TEXT blocks: 0..2. */
    val indentLevel: Int = 0,
    /** TEXT blocks: LEFT, CENTER, or RIGHT. */
    val textAlign: String = "LEFT",
    /** Per-type payload JSON; null for plain text blocks. */
    val metadata: String? = null
)
