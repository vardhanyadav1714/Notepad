package `in`.innovaticshub.notepad

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RestoredAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun matchingBarsLeaveThePaperUncoveredInBothThemes() {
        val model = DrawingViewModel().apply { setDocumentTitle("Field notes") }
        var dark by mutableStateOf(false)
        compose.setContent { NotepadTheme(darkTheme=dark) {
            DrawingStudioScreen(model,onBack={},onSaveText={})
        } }
        for (night in listOf(false,true)) {
            compose.runOnIdle { dark=night }
            val header = compose.onNodeWithTag("editor-header")
            val toolbar = compose.onNodeWithTag("handwriting-toolbar")
            val canvas = compose.onNodeWithTag("ink-canvas")
            val top = header.captureToImage().toPixelMap()
            val bottom = toolbar.captureToImage().toPixelMap()
            assertEquals("Header and tool row must use the same surface",top[1,top.height/2],bottom[1,bottom.height/2])
            val paper = canvas.captureToImage().toPixelMap()
            if (night) {
                assertTrue(top[1,top.height/2].red<0.15f)
                assertTrue(paper[paper.width/2,80].red<0.15f)
            } else {
                assertTrue(top[1,top.height/2].red>0.9f)
                assertTrue(paper[paper.width/2,80].red>0.9f)
            }
            assertTrue(header.fetchSemanticsNode().boundsInRoot.bottom<=canvas.fetchSemanticsNode().boundsInRoot.top)
            assertTrue(canvas.fetchSemanticsNode().boundsInRoot.bottom<=toolbar.fetchSemanticsNode().boundsInRoot.top)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir("release-review"),"editor-${if(night) "dark" else "light"}.png").outputStream().use {
                assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
            }
        }
    }
}
