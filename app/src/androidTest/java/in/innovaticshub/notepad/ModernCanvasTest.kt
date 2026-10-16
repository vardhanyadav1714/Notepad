package `in`.innovaticshub.notepad

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModernCanvasTest {
    @get:Rule val compose=createComposeRule()

    @Test fun darkInkReusesOriginalGeometryAcrossNewStrokesAndThemeChanges() {
        val model=DrawingViewModel()
        repeat(160) {index->
            val y=100f+(index%60)*10f
            model.startStroke(70f,y)
            repeat(20) {step->model.addStrokePoint(70f+step*15f,y+step%3*3f)}
            model.endStroke()
        }
        val original=model.uiState.value.strokes
        assertTrue(original.none {it.hasCachedRenderData})
        var dark by mutableStateOf(true)
        compose.setContent {NotepadTheme(darkTheme=dark) {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("ink-canvas").captureToImage()
        compose.runOnIdle {assertTrue("Dark rendering must cache the original strokes, not temporary colour copies",original.all {it.hasCachedRenderData})}
        val geometry=original.map {it.renderData}
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(120f,300f),Offset(300f,350f),250)}
        compose.runOnIdle {dark=false}
        compose.onNodeWithTag("ink-canvas").captureToImage()
        compose.runOnIdle {
            assertEquals(161,model.uiState.value.strokes.size)
            original.forEachIndexed {index,stroke->assertSame(geometry[index],stroke.renderData)}
            assertEquals(original.map {it.toolConfig.color},model.uiState.value.strokes.take(160).map {it.toolConfig.color})
        }
    }

    @Test fun toolPaletteKeepsAdvancedToolsAccessibleAndWritingModeExplicit() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithContentDescription("Finger drawing: on").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Finger drawing: off").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Handwriting tools").performClick()
        listOf("Ink colour","Easy writing pad","Zoom lock","Auto-fix shapes","Pages").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
        compose.onNodeWithText("Zoom lock").performClick()
        compose.runOnIdle {assertTrue(model.uiState.value.zoomLocked)}
        compose.onNodeWithContentDescription("Collaboration").assertIsDisplayed()
    }
}
