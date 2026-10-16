package `in`.innovaticshub.notepad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.ui.canvas.model.PageText
import `in`.innovaticshub.notepad.ui.theme.NoteAppearance
import org.junit.Assert.*
import org.junit.Test

class NoteAppearanceTest {
    @Test fun neutralPaperFollowsBothDirectionsOfThemeChanges() {
        for(source in listOf(Color.White,Color.Black,Color(0xFFFAFAFA))) {
            assertEquals(Color.Black,NoteAppearance(true).paper(source))
            assertEquals(Color.White,NoteAppearance(false).paper(source))
        }
    }
    @Test fun neutralInkStaysReadableWithoutChangingColouredInkOrOpacity() {
        assertTrue(NoteAppearance(true).ink(Color(0xFF202124)).red>0.8f)
        assertTrue(NoteAppearance(false).ink(Color.White).red<0.2f)
        val blue=Color(0xFF2288FF).copy(alpha=0.4f)
        assertEquals(blue,NoteAppearance(true).ink(blue));assertEquals(blue,NoteAppearance(false).ink(blue))
        assertEquals(0.4f,NoteAppearance(true).ink(Color.Black.copy(alpha=0.4f)).alpha,0.01f)
    }
    @Test fun richTextAdaptationDoesNotMutateSavedTextOrHighlights() {
        val original=PageText(color=Color.Black.toArgb(),content=RichTextData("Test",listOf(
            RichSpan(0,2,SpanKind.COLOR,argb=Color.Black.toArgb()),RichSpan(2,4,SpanKind.HIGHLIGHT,argb=Color.Yellow.toArgb()))))
        val displayed=NoteAppearance(true).text(original)
        assertNotEquals(original.color,displayed.color)
        assertNotEquals(original.content.spans.first().argb,displayed.content.spans.first().argb)
        assertEquals(original.content.spans.last(),displayed.content.spans.last())
        assertEquals(Color.Black.toArgb(),original.color);assertEquals(Color.Black.toArgb(),original.content.spans.first().argb)
    }
    @Test fun pdfAndExplicitColouredPaperRetainTheirDocumentColours() {
        val dark=NoteAppearance(true);val blue=Color(0xFF224488)
        assertEquals(blue,dark.paper(blue));assertEquals(Color.White,dark.paper(Color.White,pdf=true))
        val cream=Color(0xFFFFF8E8);assertEquals(cream,dark.paper(cream))
        assertEquals(Color.Black,dark.ink(Color.Black,pdf=true))
        val text=PageText();assertSame(text,dark.text(text,pdf=true))
    }
}
