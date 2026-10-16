package `in`.innovaticshub.notepad

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import `in`.innovaticshub.notepad.data.prefs.NoteViewMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryReconstructionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun searchSelectionAndFavouritesUseTheVisibleLibraryControls() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DrawlyApplication
        val previousView=runBlocking {app.container.settingsRepository.viewMode.first()}
        runBlocking {app.container.settingsRepository.setViewMode(NoteViewMode.GRID)}
        val prefix="Library ${System.currentTimeMillis()}"
        val notes=runBlocking {listOf(app.container.noteRepository.createNote(title="$prefix Alpha"),app.container.noteRepository.createNote(title="$prefix Beta"))}
        try {
            compose.waitUntil(15000) {compose.onAllNodesWithText(notes.first().title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Search notes").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("$prefix Alp")
            compose.onNodeWithText(notes.first().title).assertExists()
            compose.onNodeWithText(notes.last().title).assertDoesNotExist()
            compose.onNodeWithContentDescription("Close search").performClick()
            compose.onNodeWithContentDescription("Search notes").performClick()
            compose.onNode(hasSetTextAction()).performTextInput(prefix)
            compose.onNodeWithText(notes.first().title).performTouchInput {longClick()}
            compose.onNodeWithText("All").performClick()
            compose.onNodeWithText("2 selected").assertExists()
            compose.onNodeWithText("Favourite").performClick()
            compose.waitUntil(10000) {runBlocking {notes.all {app.container.noteRepository.getNote(it.id)?.isPinned==true}}}
            compose.onNodeWithContentDescription("Open navigation").performClick()
            compose.onNodeWithText("Favourites").performClick()
            compose.onNodeWithText(notes.first().title).assertExists()
            compose.onNodeWithText(notes.last().title).assertExists()
            compose.onNodeWithContentDescription("Library options").performClick()
            compose.onNodeWithText("List view").performClick()
            compose.waitForIdle()
            compose.onNodeWithText(notes.first().title).assertIsDisplayed()
        } finally {runBlocking {notes.forEach {app.container.noteRepository.purgeNote(it.id)};app.container.settingsRepository.setViewMode(previousView)}}
    }
}
