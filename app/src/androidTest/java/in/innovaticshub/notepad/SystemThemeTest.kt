package `in`.innovaticshub.notepad

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.prefs.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SystemThemeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun systemThemeChangesUpdateTheOpenNoteWithoutClosingOrRecolouringItsStorage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as DrawlyApplication
        val wasDark=compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES
        val mode=runBlocking {app.container.settingsRepository.themeMode.first()}
        val title="System theme ${System.currentTimeMillis()}"
        val note=runBlocking {app.container.settingsRepository.setThemeMode(ThemeMode.SYSTEM);app.container.noteRepository.createNote(title=title)}
        fun shell(command:String) {android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {it.readBytes()}}
        fun assertTheme(dark:Boolean) {
            shell("cmd uimode night ${if(dark) "yes" else "no"}")
            compose.waitUntil(10000) {compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO}
            compose.waitForIdle()
            compose.onNodeWithTag("note-title-editor").assertTextContains(title)
            val pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            if(dark) assertTrue(pixels[30,150].red<0.15f) else assertTrue(pixels[30,150].red>0.85f)
        }
        try {
            compose.waitUntil(15000) {compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(title).performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("page-text-editor").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("page-text-editor").performTextInput("Keep this text through both themes")
            repeat(6) {
                compose.onNodeWithContentDescription("Handwriting mode").assertIsDisplayed().performClick()
                compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Text mode").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("page-text-editor").assertDoesNotExist()
                compose.onNodeWithContentDescription("Text mode").assertIsDisplayed().performClick()
                compose.waitUntil(5000) {compose.onAllNodesWithTag("page-text-editor").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("page-text-editor").assertTextContains("Keep this text through both themes")
            }
            compose.onNodeWithContentDescription("Handwriting mode").performClick()
            compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Text mode").fetchSemanticsNodes().isNotEmpty()}
            val activity=compose.activity
            assertTheme(false);assertTheme(true);assertTheme(false)
            assertSame("A system theme change must preserve the active editor",activity,compose.activity)
            compose.onNodeWithContentDescription("Text mode").performClick()
            compose.onNodeWithTag("page-text-editor").assertTextContains("Keep this text through both themes")
            compose.onNodeWithContentDescription("Save and return").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithContentDescription("Create note").fetchSemanticsNodes().isNotEmpty()}
        } catch(failure:Throwable) {
            val directory=java.io.File(app.getExternalFilesDir("release-review"),"system-theme-failure").apply {mkdirs()}
            runCatching {java.io.File(directory,"semantics.txt").writeText(compose.onRoot(useUnmergedTree=true).printToString())}
            runCatching {compose.onRoot().captureToImage().asAndroidBitmap().let {image->
                java.io.File(directory,"screen.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }}
            throw failure
        } finally {
            shell("cmd uimode night ${if(wasDark) "yes" else "no"}")
            runBlocking {app.container.settingsRepository.setThemeMode(mode);app.container.noteRepository.purgeNote(note.id)}
        }
    }
}
