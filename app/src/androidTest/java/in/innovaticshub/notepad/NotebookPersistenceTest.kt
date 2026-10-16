package `in`.innovaticshub.notepad

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.Color
import `in`.innovaticshub.notepad.data.NoteRepository
import `in`.innovaticshub.notepad.data.content.*
import `in`.innovaticshub.notepad.data.db.DrawlyDatabase
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.storage.StrokeJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookPersistenceTest {
    @Test fun noteContentSearchAndTrashRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, DrawlyDatabase::class.java).build()
        try {
            val notes = NoteRepository(database, context)
            notes.createNote(title = "Other note")
            val note = notes.createNote(title = "A plan")
            val block = NoteBlock(NoteBlock.newId(), BlockType.TEXT, 0,
                richText = RichTextData("Botanical research", listOf(RichSpan(0, 9, SpanKind.BOLD))))
            notes.saveNote(note.id, "Garden ideas", listOf(block))
            assertEquals(listOf(block), notes.getBlocks(note.id))
            assertEquals(listOf(note.id), notes.search("Botanical").first().map { it.id })
            notes.trashNote(note.id)
            assertEquals(listOf(note.id), notes.observeTrash().first().map { it.id })
            notes.restoreNote(note.id)
            assertTrue(notes.observeTrash().first().isEmpty())
            assertEquals("Garden ideas", notes.getNote(note.id)?.title)
        } finally { database.close() }
    }

    @Test fun multipleDrawingStrokesKeepTheirIdsColoursAndPoints() {
        val strokes = listOf(
            StrokeBuilder(ToolConfig.MEDIUM_PEN.copy(color = Color.Red), 0f).apply {
                addPoint(StrokePoint(1f, 2f, 0.5f)); addPoint(StrokePoint(30f, 40f, 0.75f))
            }.build(),
            StrokeBuilder(ToolConfig.defaultForType(ToolType.PENCIL), 0f).apply {
                addPoint(StrokePoint(100f, 200f, 1f)); addPoint(StrokePoint(300f, 400f, 0.5f))
            }.build())
        val restored = StrokeJson.strokesFromJson(StrokeJson.strokesToJson(strokes))
        assertEquals(strokes.map { it.id }, restored.map { it.id })
        assertEquals(strokes.map { it.points }, restored.map { it.points })
        assertEquals(strokes.map { it.toolConfig }, restored.map { it.toolConfig })
    }
}
