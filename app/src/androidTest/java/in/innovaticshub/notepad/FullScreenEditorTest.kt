package `in`.innovaticshub.notepad

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.BlockType
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FullScreenEditorTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun fullScreenKeepsBottomToolsAndBackRestoresChromeWithoutLeavingTheNote() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as DrawlyApplication
        val orientation=compose.activity.requestedOrientation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {String(it.readBytes()).trim()}
        val autoRotate=shell("settings get system accelerometer_rotation")
        val userRotation=shell("settings get system user_rotation").toIntOrNull() ?: 0
        val title="Full screen ${System.currentTimeMillis()}"
        val note=runBlocking {app.container.noteRepository.createNote(title=title)}
        fun statusVisible()=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars())
        fun screenshot(name:String) {
            compose.waitForIdle()
            // Configuration updates precede the system's rotation animation.
            // Wait for accessibility/window events to settle before capturing it.
            instrumentation.uiAutomation.waitForIdle(750,5000)
            val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
            try {File(app.getExternalFilesDir("release-review"),"$name.png").outputStream().use {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }} finally {bitmap.recycle()}
        }
        fun enterFullScreen() {
            compose.onNodeWithContentDescription("Drawing options").performClick()
            compose.onNodeWithText("Full screen").performClick()
            compose.onNodeWithTag("editor-header").assertDoesNotExist()
            compose.onNodeWithContentDescription("Exit full screen").assertIsDisplayed()
            compose.onNodeWithTag("handwriting-toolbar").assertIsDisplayed()
            val canvas=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
            val tools=compose.onNodeWithTag("handwriting-toolbar").fetchSemanticsNode().boundsInRoot
            assertTrue("Full-screen tools must stay below the writing area",canvas.bottom<=tools.top)
            compose.waitUntil(5000) {statusVisible()==false}
        }
        fun rotate(landscape:Boolean) {
            // Android 16 ignores app orientation requests on large screens.
            // Rotate the display as a user would, then restore its original settings.
            shell("wm user-rotation lock ${if(landscape) 1 else 0}")
            compose.waitUntil(10000) {compose.activity.resources.configuration.let {if(landscape) it.screenWidthDp>it.screenHeightDp else it.screenHeightDp>it.screenWidthDp}}
        }
        try {
            compose.runOnIdle {compose.activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
            rotate(false)
            compose.waitUntil(15000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("page-text-editor").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("page-text-editor").performTextInput("Space for your ideas")
            compose.onNodeWithContentDescription("Handwriting mode").performClick()
            compose.onNodeWithTag("editor-header").assertIsDisplayed()
            compose.waitUntil(5000) {statusVisible()==true}
            val tablet=compose.activity.resources.configuration.smallestScreenWidthDp>=600
            if(tablet) {
                compose.onAllNodesWithContentDescription("Pages").onFirst().performClick()
                compose.onNodeWithTag("page-sorter").assertIsDisplayed()
            }
            screenshot("normal-portrait")
            val normalHeight=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot.height
            enterFullScreen()
            compose.onNodeWithTag("page-sorter").assertDoesNotExist()
            if(tablet) {
                compose.onAllNodesWithContentDescription("Pages").onFirst().performClick()
                compose.onNodeWithText("Your pages").assertIsDisplayed()
                compose.onNodeWithText("Add page").assertIsDisplayed()
                androidx.test.espresso.Espresso.pressBack()
                compose.onNodeWithContentDescription("Exit full screen").assertIsDisplayed()
            }
            assertTrue(compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot.height>normalHeight)
            compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(width*0.25f,height*0.4f),Offset(width*0.65f,height*0.5f),250)}
            compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
            compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
            screenshot("full-screen-portrait")
            compose.onNodeWithContentDescription("Text mode").performClick()
            compose.onNodeWithContentDescription("Handwriting mode").assertIsDisplayed()
            compose.onNodeWithTag("editor-header").assertDoesNotExist()
            compose.onNodeWithTag("page-text-editor").assertTextContains("Space for your ideas")
            compose.onNodeWithContentDescription("Handwriting mode").performClick()
            rotate(true)
            compose.onNodeWithTag("editor-header").assertDoesNotExist()
            compose.onNodeWithTag("handwriting-toolbar").assertIsDisplayed()
            compose.waitUntil(5000) {statusVisible()==false}
            val canvas=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
            assertTrue(canvas.bottom<=compose.onNodeWithTag("handwriting-toolbar").fetchSemanticsNode().boundsInRoot.top)
            screenshot("full-screen-landscape")
            compose.onNodeWithContentDescription("Exit full screen").performClick()
            compose.onNodeWithTag("editor-header").assertIsDisplayed()
            compose.onNodeWithTag("handwriting-toolbar").assertIsDisplayed()
            compose.waitUntil(5000) {statusVisible()==false}
            screenshot("normal-landscape")
            if(tablet) {
                compose.onNodeWithTag("page-sorter").assertIsDisplayed()
                compose.onNodeWithContentDescription("Close page sorter").performClick()
            }
            enterFullScreen()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("editor-header").assertIsDisplayed()
            compose.onNodeWithTag("note-title-editor").assertTextContains(title)
            rotate(false)
            compose.waitUntil(5000) {statusVisible()==true}
            compose.onNodeWithContentDescription("Save and return").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Create note").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(5000) {statusVisible()==true}
            val document=runBlocking {
                val payload=app.container.noteRepository.getBlocks(note.id).single {it.type==BlockType.DRAWING}.drawing!!
                DrawingStorage.loadDocument(app,payload.strokesFile)!!
            }
            assertEquals(1,document.strokes.size)
            assertTrue(document.texts.any {it.content.text=="Space for your ideas"})
        } finally {
            compose.runOnIdle {compose.activity.requestedOrientation=orientation}
            shell("wm user-rotation lock $userRotation")
            if(autoRotate=="1") shell("wm user-rotation free")
            runBlocking {app.container.noteRepository.purgeNote(note.id)}
        }
    }
}
