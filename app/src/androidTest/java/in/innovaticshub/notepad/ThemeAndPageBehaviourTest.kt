package `in`.innovaticshub.notepad

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.storage.DrawingStorage
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeAndPageBehaviourTest {
    @get:Rule val compose=createComposeRule()
    @Test fun unevenTwoFingerSwipeAddsAPageWithoutChangingZoomOrWriting() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.waitForIdle()
        val scale=model.zoomState.scale
        compose.onNodeWithTag("ink-canvas").performTouchInput {
            down(0,Offset(width*0.35f,height*0.85f));down(1,Offset(width*0.65f,height*0.85f))
            for(step in 1..40) {
                val t=step/40f;val y=height*(0.85f-t*0.65f)
                updatePointerTo(0,Offset(width*(0.35f-t*0.018f),y))
                updatePointerTo(1,Offset(width*(0.65f+t*0.018f),y));move(16)
            }
            up(1)
            moveTo(0,Offset(width*0.332f,height*0.1f),100)
            up(0)
        }
        compose.runOnIdle {
            assertEquals(2,model.uiState.value.pages.size)
            assertEquals(scale,model.zoomState.scale,0.001f)
            assertTrue(model.uiState.value.strokes.isEmpty())
            assertTrue(model.zoomState.offset.y < -model.zoomState.viewportHeight*0.55f)
        }
    }
    @Test fun scrollingHasMomentumAndANewContactStopsItWithoutAddingPages() {
        val model=DrawingViewModel();model.setFingerDrawing(false)
        repeat(3) {model.addPage()}
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.waitForIdle()
        compose.runOnIdle {model.zoomState.setOffset(Offset(0f,-200f))}
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("ink-canvas").performTouchInput {
            down(Offset(width*0.5f,height*0.7f))
            for(step in 1..8) moveTo(Offset(width*0.5f,height*(0.7f-step/8f*0.3f)),16)
            up()
        }
        var released=0f
        compose.runOnIdle {released=model.zoomState.offset.y}
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {assertTrue("Scroll must continue after lift: released=$released, now=${model.zoomState.offset.y}",model.zoomState.offset.y<released-10f)}
        compose.onNodeWithTag("ink-canvas").performTouchInput {down(Offset(width*0.5f,height*0.4f))}
        var stopped=0f
        compose.runOnIdle {stopped=model.zoomState.offset.y}
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle {assertEquals(stopped,model.zoomState.offset.y,0.01f)}
        compose.onNodeWithTag("ink-canvas").performTouchInput {up()}
        compose.mainClock.autoAdvance=true
        compose.runOnIdle {assertEquals(4,model.uiState.value.pages.size);assertTrue(model.uiState.value.strokes.isEmpty())}
    }
    @Test fun lightAndDarkPaperAndInkChangeWithoutChangingSavedContent() {
        val model=DrawingViewModel();var dark by mutableStateOf(false)
        model.startStroke(150f,300f);model.addStrokePoint(350f,300f);model.endStroke()
        compose.setContent {NotepadTheme(darkTheme=dark) {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.waitForIdle();val saved=DrawingStorage.encodeSession(model.snapshot())
        fun assertPaperAndInk(darkPaper:Boolean) {
            val pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
            val ink=model.zoomState.canvasToScreen(Offset(250f,300f))
            val paper=pixels[20,200];val stroke=pixels[ink.x.toInt(),ink.y.toInt()]
            if(darkPaper) {assertTrue(paper.red<0.1f);assertTrue(stroke.red>0.8f)}
            else {assertTrue(paper.red>0.9f);assertTrue(stroke.red<0.3f)}
        }
        assertPaperAndInk(false)
        compose.runOnIdle {dark=true};assertPaperAndInk(true)
        compose.runOnIdle {dark=false};assertPaperAndInk(false)
        compose.runOnIdle {assertEquals(saved,DrawingStorage.encodeSession(model.snapshot()))}
    }
    @Test fun upwardScrollAppendsOneMatchingPageWithoutInkAndUndoRemovesIt() {
        val model=DrawingViewModel();model.setFingerDrawing(false)
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("add-page").assertDoesNotExist()
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(width*0.5f,height*0.85f),Offset(width*0.5f,height*0.1f),600)}
        compose.runOnIdle {
            assertEquals(2,model.uiState.value.pages.size);assertTrue(model.uiState.value.strokes.isEmpty())
            assertEquals(model.uiState.value.pages[0].height,model.uiState.value.pages[1].height,0.01f)
            assertTrue(model.zoomState.offset.y<0f)
            model.undo();assertEquals(1,model.uiState.value.pages.size)
        }
        compose.waitForIdle()
        compose.runOnIdle {assertTrue("Undo must leave the remaining page reachable",model.zoomState.offset.y>=-1f)}
    }
    @Test fun readingModeScrollAndPinchingDoNotAddPagesOrLeaveInk() {
        val model=DrawingViewModel();model.setFingerDrawing(false)
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithContentDescription("Reading mode").performClick()
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(width*0.5f,height*0.85f),Offset(width*0.5f,height*0.1f),600)}
        compose.runOnIdle {assertEquals(1,model.uiState.value.pages.size)}
        compose.onNodeWithContentDescription("Edit note").performClick()
        repeat(3) {
            compose.onNodeWithTag("ink-canvas").performTouchInput {
                down(0,Offset(width*0.15f,height*0.65f));down(1,Offset(width*0.85f,height*0.65f))
                moveTo(0,Offset(width*0.45f,height*0.25f),400);moveTo(1,Offset(width*0.55f,height*0.25f),16)
                up(0);up(1)
            }
        }
        compose.runOnIdle {
            assertEquals(1,model.uiState.value.pages.size);assertTrue(model.uiState.value.strokes.isEmpty())
            val fit=model.zoomState.viewportWidth/model.uiState.value.currentPage.width
            assertTrue(model.zoomState.scale>=fit*0.75f-0.01f);assertTrue(model.zoomState.scale<=fit*6f+0.01f)
        }
    }
    @Test fun twoFingerScrollCreatesOneUndoablePageAndPreservesLocalPageCoordinates() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("ink-canvas").performTouchInput {
            down(0,Offset(width*0.35f,height*0.85f));down(1,Offset(width*0.65f,height*0.85f))
            for(step in 1..30) {
                val y=height*(0.85f-step/30f*0.7f)
                updatePointerTo(0,Offset(width*0.35f,y));updatePointerTo(1,Offset(width*0.65f,y));move(16)
            }
            up(0);up(1)
        }
        compose.runOnIdle {
            assertEquals(2,model.uiState.value.pages.size);assertTrue(model.uiState.value.strokes.isEmpty())
            model.undo();assertEquals(1,model.uiState.value.pages.size)
            model.redo();assertEquals(2,model.uiState.value.pages.size)
            assertEquals(2,DrawingStorage.decodeSession(DrawingStorage.encodeSession(model.snapshot())).pages.size)
        }
    }
    @Test fun aLateSecondFingerCancelsAProvisionalPagePullWithoutAnUndoEntry() {
        val model=DrawingViewModel();model.setFingerDrawing(false)
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("ink-canvas").performTouchInput {
            down(0,Offset(width*0.4f,height*0.85f));moveTo(0,Offset(width*0.4f,height*0.4f),400)
            down(1,Offset(width*0.8f,height*0.4f))
            updatePointerTo(0,Offset(width*0.55f,height*0.25f));updatePointerTo(1,Offset(width*0.65f,height*0.25f));move(32)
            up(0);up(1)
        }
        compose.runOnIdle {
            assertEquals(1,model.uiState.value.pages.size);assertTrue(model.uiState.value.strokes.isEmpty());assertFalse(model.uiState.value.canUndo)
        }
    }
}
