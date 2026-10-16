package `in`.innovaticshub.notepad

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InkStudioWorkflowTest {
    @get:Rule val compose=createComposeRule()
    @Test fun magnifiedPadAdvancesAfterPenLiftAndItsTargetCanBeMoved() {
        val model=DrawingViewModel()
        model.setTool(ToolType.LASSO)
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithContentDescription("Handwriting tools").performClick()
        compose.onNodeWithText("Easy writing pad").performClick()
        compose.runOnIdle {assertEquals(ToolType.PEN,model.uiState.value.currentTool.type)}
        compose.onNodeWithContentDescription("Automatic advance on").assertIsDisplayed()
        val before=compose.onNodeWithTag("writing-area").fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithTag("ink-canvas")[1].performTouchInput {swipe(Offset(width*0.15f,height*0.5f),Offset(width*0.94f,height*0.5f),350)}
        compose.waitUntil(5000) {compose.onNodeWithTag("writing-area").fetchSemanticsNode().boundsInRoot.left>before.left+10f}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size)}
        val advanced=compose.onNodeWithTag("writing-area").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("writing-area").performTouchInput {swipe(center,center+Offset(30f,35f),350)}
        compose.waitUntil(5000) {compose.onNodeWithTag("writing-area").fetchSemanticsNode().boundsInRoot.top>advanced.top+10f}
        compose.runOnIdle {assertEquals("Moving the target must not create ink",1,model.uiState.value.strokes.size)}
        compose.onNodeWithContentDescription("Undo pad stroke").performClick()
        compose.runOnIdle {assertTrue(model.uiState.value.strokes.isEmpty())}
    }
    @Test fun pageTemplatesAndReadingModeKeepInkAndCanBeUndone() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,200f),Offset(300f,300f),300)}
        compose.onNodeWithContentDescription("Reading mode").performClick()
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,250f),Offset(300f,350f),300)}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size)}
        compose.onNodeWithContentDescription("Edit note").performClick()
        compose.onNodeWithContentDescription("Drawing options").performClick()
        compose.onNodeWithText("Page template").performClick()
        compose.onNodeWithContentDescription("Grid paper").performClick()
        compose.runOnIdle {assertEquals(PaperStyle.GRID,model.uiState.value.paperStyle);assertEquals(1,model.uiState.value.strokes.size)}
        compose.runOnIdle {model.undo();assertEquals(PaperStyle.PLAIN,model.uiState.value.paperStyle);assertEquals(1,model.uiState.value.strokes.size)}
    }
    @Test fun pageStaysClearWhileToolPopoversConfigureRealInk() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.waitForIdle()
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        val paper=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
        assertTrue("Writing viewport must occupy at least 80% of the screen",paper.height/root.height>0.8f)
        val toolbar=compose.onNodeWithTag("handwriting-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(paper.bottom<=toolbar.top)
        listOf("Pen","Highlighter","Eraser","Select handwriting","Undo","Redo").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Pen").performClick()
        compose.onNodeWithContentDescription("Fountain").performClick()
        compose.runOnIdle {assertEquals(DrawingStyle.FOUNTAIN,model.uiState.value.currentTool.style)}
        compose.onNodeWithContentDescription("Close tool settings").performClick()
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,200f),Offset(300f,300f),300)}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size);assertEquals(DrawingStyle.FOUNTAIN,model.uiState.value.strokes.single().toolConfig.style)}
        compose.onNodeWithContentDescription("Eraser").performClick()
        compose.onNodeWithContentDescription("Eraser").performClick()
        compose.onNodeWithText("Stroke eraser").performClick()
        compose.onNodeWithContentDescription("Close tool settings").performClick()
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(180f,200f),Offset(180f,320f),300)}
        compose.runOnIdle {assertTrue(model.uiState.value.strokes.isEmpty())}
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size)}
    }

    @Test fun rectangleSelectionHonoursPartialInkAndDoesNotCreatePhantomUndo() {
        val model=DrawingViewModel()
        fun line(y:Float) {model.startStroke(50f,y);model.addStrokePoint(250f,y);model.endStroke()}
        fun rectangle() {model.startStroke(100f,50f);model.addStrokePoint(300f,150f);model.endStroke()}
        compose.runOnIdle {
            line(100f);line(300f)
            val first=model.uiState.value.strokes.first().id
            model.setTool(ToolType.LASSO);model.setSelectionShape(SelectionShape.RECTANGLE)
            model.setIncludePartialSelection(false);rectangle()
            assertTrue(model.uiState.value.selectedStrokeIds.isEmpty())
            model.setIncludePartialSelection(true);rectangle()
            assertEquals(setOf(first),model.uiState.value.selectedStrokeIds)
            model.beginSelectionTransform();model.endSelectionTransform()
            model.undo()
            assertEquals("An unmoved selection must not consume an undo step",1,model.uiState.value.strokes.size)
            model.redo();assertEquals(2,model.uiState.value.strokes.size)
            model.setTool(ToolType.ERASER);model.startStroke(900f,900f);model.addStrokePoint(950f,950f);model.endStroke()
            model.undo();assertEquals("Erasing empty space must not consume an undo step",1,model.uiState.value.strokes.size)
        }
    }
}
