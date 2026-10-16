package `in`.innovaticshub.notepad

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.feature.editor.RichTextState
import org.junit.Assert.*
import org.junit.Test

class RichTextStateTest {
    @Test fun pendingStyleAppliesOnlyToInsertedTextAndCanBeTurnedOff() {
        val rich = RichTextState()
        rich.toggleStyle(SpanKind.BOLD)
        rich.onValueChange(TextFieldValue("Hello", TextRange(5)))
        assertEquals(listOf(RichSpan(0, 5, SpanKind.BOLD)), rich.spans)
        rich.toggleStyle(SpanKind.BOLD)
        rich.onValueChange(TextFieldValue("Hello world", TextRange(11)))
        assertEquals(listOf(RichSpan(0, 5, SpanKind.BOLD)), rich.spans)
        assertFalse(rich.currentFormatting().isBold)
    }

    @Test fun removingStyleInMiddlePreservesTheSurroundingText() {
        val rich = RichTextState(RichTextData("abcdefgh", listOf(RichSpan(0, 8, SpanKind.BOLD))))
        rich.onValueChange(TextFieldValue("abcdefgh", TextRange(2, 6)))
        rich.toggleStyle(SpanKind.BOLD)
        assertEquals(listOf(RichSpan(0, 2, SpanKind.BOLD), RichSpan(6, 8, SpanKind.BOLD)), rich.spans)
        assertEquals("abcdefgh", rich.text)
    }

    @Test fun deletingTextKeepsSpansInsideTheRemainingText() {
        val rich = RichTextState(RichTextData("hello world", listOf(RichSpan(6, 11, SpanKind.ITALIC))))
        rich.onValueChange(TextFieldValue("world", TextRange(0)))
        assertEquals(listOf(RichSpan(0, 5, SpanKind.ITALIC)), rich.spans)
        rich.onValueChange(TextFieldValue("", TextRange(0)))
        assertTrue(rich.spans.isEmpty())
    }

    @Test fun undoRedoAndNewEditPreserveTextAndFormatting() {
        val rich = RichTextState()
        rich.onValueChange(TextFieldValue("One", TextRange(3)))
        rich.onValueChange(TextFieldValue("One", TextRange(0, 3)))
        rich.toggleStyle(SpanKind.UNDERLINE)
        rich.undo()
        assertTrue(rich.spans.isEmpty())
        rich.redo()
        assertEquals(listOf(RichSpan(0, 3, SpanKind.UNDERLINE)), rich.spans)
        rich.undo()
        rich.onValueChange(TextFieldValue("Two", TextRange(3)))
        assertTrue(rich.redoStack.isEmpty())
        rich.undo()
        assertEquals("One", rich.text)
    }
}
