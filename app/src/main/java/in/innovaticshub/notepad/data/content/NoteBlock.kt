package `in`.innovaticshub.notepad.data.content

/**
 * The kind of content a block holds. Names are persisted verbatim in
 * `note_blocks.type`.
 */
enum class BlockType {
    TEXT, CHECKLIST_ITEM, IMAGE, DRAWING, PDF, RECORDING;

    companion object {
        fun fromName(name: String?): BlockType =
            entries.firstOrNull { it.name == name } ?: TEXT
    }
}

/** Block-level list decoration for TEXT blocks. */
enum class ListType { NONE, BULLET, NUMBERED;

    companion object {
        fun fromName(name: String?): ListType =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Paragraph alignment for TEXT blocks. */
enum class TextAlign { LEFT, CENTER, RIGHT;

    companion object {
        fun fromName(name: String?): TextAlign =
            entries.firstOrNull { it.name == name } ?: LEFT
    }
}

/** Payload for IMAGE blocks. [path] is relative to filesDir. */
data class ImagePayload(
    val path: String,
    val width: Int,
    val height: Int
)

/**
 * Payload for DRAWING blocks. Strokes live in [strokesFile] (relative to
 * filesDir) in the same JSON format the canvas autosave uses; [collabRoom]
 * links a realtime collaboration session to this drawing.
 */
data class DrawingPayload(
    val strokesFile: String,
    val backgroundArgb: Int,
    val collabRoom: String? = null
)

/** Payload for PDF blocks. */
data class PdfPayload(
    val file: String,
    val pageCount: Int,
    val pageWidthPt: Float,
    val pageHeightPt: Float
)

/** Payload for RECORDING blocks. [path] is relative to filesDir. */
data class RecordingPayload(
    val path: String,
    val durationMs: Long
)

/**
 * One ordered content block of a note, the domain-level view of
 * `NoteBlockEntity`. Exactly one payload field is non-null, matching [type].
 */
data class NoteBlock(
    val id: String,
    val type: BlockType,
    val position: Int,
    val richText: RichTextData = RichTextData.EMPTY,
    val isChecked: Boolean? = null,
    val listType: ListType = ListType.NONE,
    val indentLevel: Int = 0,
    val textAlign: TextAlign = TextAlign.LEFT,
    val image: ImagePayload? = null,
    val drawing: DrawingPayload? = null,
    val pdf: PdfPayload? = null,
    val recording: RecordingPayload? = null
) {
    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}
