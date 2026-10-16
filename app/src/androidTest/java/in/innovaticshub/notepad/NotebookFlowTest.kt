package `in`.innovaticshub.notepad

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotebookFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editsSaveWhenReturningToTheLibrary() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DrawlyApplication
        val title = "Notebook check ${System.currentTimeMillis()}"
        val note = runBlocking { app.container.noteRepository.createNote(title = title) }
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
            compose.onNodeWithTag("note-title-editor").assertIsDisplayed().performTextReplacement("$title edited")
            compose.onNodeWithTag("page-text-editor").assertIsDisplayed().performTextInput("A thought that survives reopening.")
            compose.onNodeWithContentDescription("Save and return").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Create note").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(10000) { compose.onAllNodesWithText("$title edited").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                assertEquals("$title edited", app.container.noteRepository.getNote(note.id)?.title)
                val blocks=app.container.noteRepository.getBlocks(note.id)
                assertEquals("A thought that survives reopening.", blocks.single {it.type==`in`.innovaticshub.notepad.data.content.BlockType.TEXT}.richText.text)
                val drawing=blocks.single {it.type==`in`.innovaticshub.notepad.data.content.BlockType.DRAWING}.drawing!!
                val document=`in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage.loadDocument(app,drawing.strokesFile)!!
                assertEquals("A thought that survives reopening.",document.texts.single().content.text)
            }
            compose.onNodeWithText("$title edited").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("A thought that survives reopening.").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("A thought that survives reopening.").assertExists()
            compose.onNodeWithContentDescription("Save and return").performClick()
        } finally { runBlocking { app.container.noteRepository.purgeNote(note.id) } }
    }
}
