package `in`.innovaticshub.notepad

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.drawing.ZoomableCanvas
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class InkVisibilityTest {
    @get:Rule val compose=createComposeRule()

    @Test fun freshPreferencesProduceVisibleFingerInkInBothThemesAndAfterReload() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="ink-visibility-${UUID.randomUUID()}-"
        val isolated=object:ContextWrapper(context) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences(prefix+name,mode)
        }
        val model=DrawingViewModel().apply {loadPreferences(isolated);setToolConfig(ToolConfig.MEDIUM_PEN.copy(baseWidth=18f))}
        var dark by mutableStateOf(true)
        compose.setContent {NotepadTheme(darkTheme=dark) {ZoomableCanvas(model,Modifier.fillMaxSize(),zoomState=model.zoomState)}}
        try {
            compose.runOnIdle {assertTrue("Writing must work after loading fresh preferences",model.uiState.value.fingerDrawing)}
            compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(150f,300f),Offset(450f,300f),300)}
            compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size)}
            var pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            assertTrue("Dark paper must show neutral ink",pixels[300,300].red>0.8f)
            assertTrue(pixels[300,360].red<0.1f)
            val saved=DrawingStorage.encodeSession(model.snapshot())
            compose.runOnIdle {dark=false;model.restoreSession(DrawingStorage.decodeSession(saved))}
            pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            assertTrue("Reloaded ink must be visible on light paper",pixels[300,300].red<0.3f)
            assertTrue(pixels[300,360].red>0.9f)
            compose.runOnIdle {model.setColor(Color.Red)}
            compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(150f,450f),Offset(450f,450f),300)}
            pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            assertTrue("Chosen red ink must render",pixels[300,450].red>0.8f && pixels[300,450].green<0.2f)
            compose.runOnIdle {dark=true}
            pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            assertTrue("Chosen red must remain visible in dark mode",pixels[300,450].red>0.8f && pixels[300,450].green<0.2f)
            compose.runOnIdle {assertEquals(2,model.uiState.value.strokes.size)}
        } finally {context.deleteSharedPreferences(prefix+"drawing_tools")}
    }
}
