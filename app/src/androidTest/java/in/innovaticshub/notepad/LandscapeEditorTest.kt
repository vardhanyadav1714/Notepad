package `in`.innovaticshub.notepad

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.BlockType
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LandscapeEditorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun rotatingExposesExpandedToolsAndPreservesBothPagesOnReopen() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DrawlyApplication
        val preferences=`in`.innovaticshub.notepad.ui.canvas.storage.DrawingPreferences(app)
        val zoomLocked=preferences.zoomLocked
        val title="Rotation ${System.currentTimeMillis()}"
        val note=runBlocking {app.container.noteRepository.createNote(title=title)}
        try {
            compose.waitUntil(15000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("page-text-editor").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("page-text-editor").performTextInput("Portrait page")
            compose.onNodeWithContentDescription("Handwriting mode").performClick()
            compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(120f,250f),Offset(350f,300f),200)}
            compose.onNodeWithContentDescription("Insert").performClick();compose.onAllNodesWithText("Add page").onLast().performClick()
            compose.runOnIdle {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            compose.waitUntil(10000) {compose.activity.resources.configuration.let {it.screenWidthDp>it.screenHeightDp}}
            compose.onNodeWithContentDescription("Ink colour").assertIsDisplayed()
            compose.onNodeWithContentDescription("Handwriting tools").performClick()
            listOf("Easy writing pad","Auto-fix shapes","Zoom lock").forEach {compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()}
            compose.onNodeWithText("Zoom lock").performScrollTo().performClick()
            compose.onAllNodesWithContentDescription("Pages").onFirst().assertIsDisplayed()
            val canvas=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
            val toolbar=compose.onNodeWithTag("handwriting-toolbar").fetchSemanticsNode().boundsInRoot
            assertTrue(canvas.bottom<=toolbar.top);assertTrue(canvas.width>canvas.height)
            compose.onNodeWithContentDescription("Text mode").performClick()
            compose.onNodeWithTag("page-text-editor").performTextInput("Landscape page")
            listOf("Bold","Italic","Underline","Bulleted list","Align").forEach {compose.onNodeWithContentDescription(it).assertIsDisplayed()}
            compose.onNodeWithContentDescription("Handwriting mode").performClick()
            compose.runOnIdle {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            compose.waitUntil(10000) {compose.activity.resources.configuration.let {it.screenHeightDp>it.screenWidthDp}}
            compose.onNodeWithTag("note-title-editor").assertTextContains(title)
            compose.onNodeWithContentDescription("Collaboration").assertIsDisplayed()
            compose.onNodeWithContentDescription("Save and return").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Create note").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(10000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            val document=runBlocking {
                val payload=app.container.noteRepository.getBlocks(note.id).single {it.type==BlockType.DRAWING}.drawing!!
                DrawingStorage.loadDocument(app,payload.strokesFile)!!
            }
            assertEquals(2,document.pages.size);assertEquals(1,document.strokes.size)
            assertEquals(listOf("Portrait page","Landscape page"),document.texts.map {it.content.text})
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("ink-canvas").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("Save and return").performClick()
        } finally {
            preferences.zoomLocked=zoomLocked
            compose.runOnIdle {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}
            runBlocking {app.container.noteRepository.purgeNote(note.id)}
        }
    }
}
