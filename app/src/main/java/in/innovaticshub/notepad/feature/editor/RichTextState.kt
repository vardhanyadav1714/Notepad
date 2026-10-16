package `in`.innovaticshub.notepad.feature.editor

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import `in`.innovaticshub.notepad.data.content.*

data class TextFormatting(
    val isBold: Boolean = false, val isItalic: Boolean = false,
    val isUnderline: Boolean = false, val isStrikethrough: Boolean = false,
    val colorArgb: Int? = null, val highlightArgb: Int? = null, val sizePx: Int? = null
)

/** Owns text, selection, styles, and undo history for one editable paragraph. */
class RichTextState(initial: RichTextData = RichTextData.EMPTY) {
    var textFieldValue by mutableStateOf(TextFieldValue(initial.text))
        private set
    var spans by mutableStateOf(initial.spans)
        private set
    private var pendingFormatting by mutableStateOf<TextFormatting?>(null)
    val text get() = textFieldValue.text
    val undoStack = ArrayDeque<Pair<String, List<RichSpan>>>()
    val redoStack = ArrayDeque<Pair<String, List<RichSpan>>>()

    fun onValueChange(value: TextFieldValue) {
        if (value.text == text) {
            if (value.selection != textFieldValue.selection) pendingFormatting = null
            textFieldValue = value
            return
        }
        snapshot()
        val inherited = currentFormatting()
        val old = text
        var prefix = 0
        val common = minOf(old.length, value.text.length)
        while (prefix < common && old[prefix] == value.text[prefix]) prefix++
        var suffix = 0
        while (suffix < common - prefix && old[old.lastIndex - suffix] == value.text[value.text.lastIndex - suffix]) suffix++
        val oldEnd = old.length - suffix
        val newEnd = value.text.length - suffix
        val delta = value.text.length - old.length
        val updated = mutableListOf<RichSpan>()
        spans.forEach { span ->
            if (span.start < prefix) updated += span.copy(end = minOf(span.end, prefix))
            if (span.end > oldEnd) updated += span.copy(start = maxOf(span.start, oldEnd) + delta, end = span.end + delta)
        }
        if (newEnd > prefix) updated += inherited.toSpans(prefix, newEnd)
        spans = normalize(updated, value.text.length)
        textFieldValue = value
    }

    fun toggleStyle(kind: SpanKind, argb: Int? = null, sizePx: Int? = null) {
        val selection = textFieldValue.selection
        if (selection.collapsed) {
            val current = currentFormatting()
            pendingFormatting = when (kind) {
                SpanKind.BOLD -> current.copy(isBold = !current.isBold)
                SpanKind.ITALIC -> current.copy(isItalic = !current.isItalic)
                SpanKind.UNDERLINE -> current.copy(isUnderline = !current.isUnderline)
                SpanKind.STRIKE -> current.copy(isStrikethrough = !current.isStrikethrough)
                SpanKind.COLOR -> current.copy(colorArgb = argb)
                SpanKind.HIGHLIGHT -> current.copy(highlightArgb = argb)
                SpanKind.SIZE -> current.copy(sizePx = sizePx)
            }
            return
        }
        snapshot()
        val start = selection.min
        val end = selection.max
        val active = spans.any { it.kind == kind && it.start <= start && it.end >= end }
        val updated = mutableListOf<RichSpan>()
        spans.forEach { span ->
            if (span.kind != kind || span.end <= start || span.start >= end) updated += span
            else {
                if (span.start < start) updated += span.copy(end = start)
                if (span.end > end) updated += span.copy(start = end)
            }
        }
        if (!active || kind in setOf(SpanKind.COLOR, SpanKind.HIGHLIGHT, SpanKind.SIZE))
            updated += RichSpan(start, end, kind, argb, sizePx)
        spans = normalize(updated, text.length)
        pendingFormatting = null
    }

    fun currentFormatting(): TextFormatting {
        if (textFieldValue.selection.collapsed) pendingFormatting?.let { return it }
        val selection = textFieldValue.selection
        val start = if (selection.collapsed) (selection.start - 1).coerceAtLeast(0) else selection.min
        val end = if (selection.collapsed) (start + 1).coerceAtMost(text.length) else selection.max
        fun covering(kind: SpanKind) = spans.lastOrNull { it.kind == kind && it.start <= start && it.end >= end && end > start }
        return TextFormatting(covering(SpanKind.BOLD) != null, covering(SpanKind.ITALIC) != null,
            covering(SpanKind.UNDERLINE) != null, covering(SpanKind.STRIKE) != null,
            covering(SpanKind.COLOR)?.argb, covering(SpanKind.HIGHLIGHT)?.argb, covering(SpanKind.SIZE)?.sizePx)
    }

    fun toRichTextData() = RichTextData(text, spans)
    fun toJson() = RichTextJson.toJson(toRichTextData())
    fun toAnnotatedString(): AnnotatedString = buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (end > start) addStyle(when (span.kind) {
                SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                SpanKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                SpanKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                SpanKind.COLOR -> SpanStyle(color = Color(span.argb ?: 0xFF242C29.toInt()))
                SpanKind.HIGHLIGHT -> SpanStyle(background = Color(span.argb ?: 0xFFFFFF00.toInt()))
                SpanKind.SIZE -> SpanStyle(fontSize = (span.sizePx ?: 16).sp)
            }, start, end)
        }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(text to spans)
        restore(previous)
    }
    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(text to spans)
        restore(next)
    }
    private fun restore(snapshot: Pair<String, List<RichSpan>>) {
        textFieldValue = TextFieldValue(snapshot.first, TextRange(textFieldValue.selection.start.coerceIn(0, snapshot.first.length)))
        spans = snapshot.second
        pendingFormatting = null
    }
    private fun snapshot() {
        val current = text to spans
        if (undoStack.lastOrNull() != current) undoStack.addLast(current)
        if (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
    }
    private fun TextFormatting.toSpans(start: Int, end: Int) = buildList {
        if (isBold) add(RichSpan(start, end, SpanKind.BOLD))
        if (isItalic) add(RichSpan(start, end, SpanKind.ITALIC))
        if (isUnderline) add(RichSpan(start, end, SpanKind.UNDERLINE))
        if (isStrikethrough) add(RichSpan(start, end, SpanKind.STRIKE))
        colorArgb?.let { add(RichSpan(start, end, SpanKind.COLOR, argb = it)) }
        highlightArgb?.let { add(RichSpan(start, end, SpanKind.HIGHLIGHT, argb = it)) }
        sizePx?.let { add(RichSpan(start, end, SpanKind.SIZE, sizePx = it)) }
    }
    private fun normalize(input: List<RichSpan>, length: Int): List<RichSpan> {
        val result = mutableListOf<RichSpan>()
        input.map { it.copy(start = it.start.coerceIn(0, length), end = it.end.coerceIn(0, length)) }
            .filter { it.end > it.start }
            .groupBy { Triple(it.kind, it.argb, it.sizePx) }.values.forEach { group ->
                group.sortedBy { it.start }.forEach { span ->
                    val previous = result.lastOrNull()
                    if (previous != null && previous.kind == span.kind && previous.argb == span.argb &&
                        previous.sizePx == span.sizePx && previous.end >= span.start)
                        result[result.lastIndex] = previous.copy(end = maxOf(previous.end, span.end))
                    else result += span
                }
            }
        return result
    }
}
