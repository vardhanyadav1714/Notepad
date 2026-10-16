package `in`.innovaticshub.notepad.data.content

/**
 * Inline rich-text span kinds supported by the note editor. COLOR and
 * HIGHLIGHT carry an ARGB value; SIZE carries a pixel size.
 */
enum class SpanKind { BOLD, ITALIC, UNDERLINE, STRIKE, COLOR, HIGHLIGHT, SIZE }

/**
 * One styled range over [RichTextData.text]. [start] is inclusive, [end]
 * exclusive, in code-point terms of the plain text string.
 */
data class RichSpan(
    val start: Int,
    val end: Int,
    val kind: SpanKind,
    val argb: Int? = null,
    val sizePx: Int? = null
)

/**
 * The authoritative rich-text model for a TEXT block. The span list is the
 * source of truth; any AnnotatedString used for display is derived from it.
 */
data class RichTextData(
    val text: String,
    val spans: List<RichSpan>
) {
    companion object {
        val EMPTY = RichTextData("", emptyList())
    }
}
