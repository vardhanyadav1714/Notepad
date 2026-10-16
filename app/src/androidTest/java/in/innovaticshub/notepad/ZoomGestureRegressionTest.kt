package `in`.innovaticshub.notepad

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.data.content.RichTextData
import `in`.innovaticshub.notepad.ui.canvas.drawing.*
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.screens.DrawingStudioScreen
import `in`.innovaticshub.notepad.ui.canvas.storage.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ZoomGestureRegressionTest {
    @get:Rule val compose=createComposeRule()
    private fun canvas(model:DrawingViewModel,text:Boolean=false) {
        compose.setContent {NotepadTheme {ZoomableCanvas(model,Modifier.fillMaxSize(),zoomState=model.zoomState,isTextMode=text,onDocumentTap={model.editTextAt(it.x,it.y)})}}
        compose.waitForIdle()
    }
    private fun pinch(liftFirst:Boolean=false,third:Boolean=false) {
        compose.onNodeWithTag("ink-canvas").performTouchInput {
            down(0,Offset(160f,300f));moveTo(0,Offset(180f,320f),32)
            down(1,Offset(500f,700f))
            moveTo(0,Offset(120f,250f),32);moveTo(1,Offset(650f,850f),32)
            if(third) {down(2,Offset(350f,600f));up(0);moveTo(1,Offset(600f,800f),32);up(2)}
            else if(liftFirst) up(0) else up(1)
            val remaining=if(liftFirst || third) 1 else 0
            moveTo(remaining,Offset(400f,650f),32);moveTo(remaining,Offset(450f,700f),32);up(remaining)
        }
    }
    @Test fun staggeredPinchesNeverCommitInkOrHistoryAndTheNextStrokeWorks() {
        val model=DrawingViewModel();canvas(model)
        repeat(6) {pinch(liftFirst=it%2==0,third=it%3==0)}
        compose.runOnIdle {
            assertTrue(model.uiState.value.strokes.isEmpty());assertFalse(model.uiState.value.canUndo);assertFalse(model.uiState.value.isDrawing)
            model.zoomState.reset()
        }
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,200f),Offset(300f,300f),200)}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size)}
    }
    @Test fun rapidRawSecondFingerAndAndroidCancelCannotLeaveGhostInk() {
        val model=DrawingViewModel();canvas(model)
        val root=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
        val instrumentation=InstrumentationRegistry.getInstrumentation();val downTime=SystemClock.uptimeMillis()
        var eventTime=downTime
        fun inject(action:Int,count:Int=1,x:Float=160f) {
            eventTime+=16
            val properties=Array(count) {i->MotionEvent.PointerProperties().apply {id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coords=Array(count) {i->MotionEvent.PointerCoords().apply {this.x=root.left+x+i*250;this.y=root.top+300f+i*250;pressure=1f;size=0.1f}}
            val event=MotionEvent.obtain(downTime,eventTime,action,count,properties,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
        }
        inject(MotionEvent.ACTION_DOWN);inject(MotionEvent.ACTION_MOVE,x=190f)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2)
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2)
        inject(MotionEvent.ACTION_MOVE,x=400f);inject(MotionEvent.ACTION_UP,x=450f)
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(model.uiState.value.strokes.isEmpty());assertFalse(model.uiState.value.canUndo)}
        inject(MotionEvent.ACTION_DOWN);inject(MotionEvent.ACTION_MOVE,x=300f);inject(MotionEvent.ACTION_CANCEL)
        compose.waitForIdle()
        compose.runOnIdle {assertTrue(model.uiState.value.strokes.isEmpty());assertFalse(model.uiState.value.isDrawing)}
    }
    @Test fun pinchWhileErasingOrDraggingSelectionRollsBackTheProvisionalEdit() {
        val model=DrawingViewModel()
        model.startStroke(160f,300f);model.addStrokePoint(180f,320f);model.endStroke()
        val original=model.uiState.value.strokes
        model.setTool(ToolType.ERASER);canvas(model);pinch()
        compose.runOnIdle {assertEquals(original,model.uiState.value.strokes);model.zoomState.reset();model.setTool(ToolType.LASSO);model.selectAll()}
        pinch(liftFirst=true)
        compose.runOnIdle {
            assertEquals(original,model.uiState.value.strokes)
            model.undo();assertTrue("Pinch must not consume an undo entry",model.uiState.value.strokes.isEmpty())
        }
    }
    @Test fun textModePinchDoesNotCreateAnEmptyTextBox() {
        val model=DrawingViewModel();canvas(model,text=true);pinch()
        compose.runOnIdle {assertTrue(model.uiState.value.texts.isEmpty());assertNull(model.uiState.value.activeTextId)}
    }
    @Test fun continuousPagesKeepInkPageLocalAndPersistPageOrder() {
        val model=DrawingViewModel();val first=model.uiState.value.currentPageId;model.addPage(true)
        val second=model.uiState.value.currentPageId
        compose.setContent {NotepadTheme {ZoomableCanvas(model,Modifier.fillMaxSize(),zoomState=model.zoomState,continuousPages=true)}}
        compose.runOnIdle {
            val stack=PageStackLayout(model.uiState.value.pages);val origin=stack.origin(second)
            model.zoomState.setScale(0.7f);model.zoomState.setOffset(Offset(0f,100f)-origin*0.7f)
        }
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,200f),Offset(300f,250f),200)}
        compose.runOnIdle {
            val stroke=model.uiState.value.strokes.single();assertEquals(second,stroke.pageId)
            assertTrue("Coordinates must stay local to page 2",stroke.points.all {it.y<500f})
            val restored=DrawingStorage.decodeSession(DrawingStorage.encodeSession(model.snapshot()))
            assertEquals(listOf(first,second),restored.pages.map {it.id})
            assertEquals(stroke.id,restored.strokes.single().id);assertEquals(stroke.points,restored.strokes.single().points)
            assertEquals(stroke.pageId,restored.strokes.single().pageId);assertEquals(stroke.toolConfig,restored.strokes.single().toolConfig)
            assertTrue(restored.pages[1].width>restored.pages[1].height)
            val stack=PageStackLayout(restored.pages);assertNull(stack.pageAt(Offset(stack.width/2,restored.pages[0].height+12f)))
            model.openPage(first,false);model.zoomState.setOffset(Offset.Zero);model.zoomState.setScale(1f)
        }
        compose.onNodeWithTag("ink-canvas").performTouchInput {click(Offset(300f,200f))}
        compose.runOnIdle {assertEquals(first,model.uiState.value.strokes.last().pageId)}
    }
    @Test fun insertAddsPagesWithoutErasingInkAndPagesOverviewNavigatesBack() {
        val model=DrawingViewModel()
        compose.setContent {NotepadTheme {DrawingStudioScreen(model,onBack={},onSaveText={})}}
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(100f,200f),Offset(300f,300f),200)}
        compose.onNodeWithContentDescription("Insert").performClick();compose.onAllNodesWithText("Add page").onLast().performClick()
        compose.runOnIdle {assertEquals(2,model.uiState.value.pages.size);assertEquals(1,model.uiState.value.strokes.size)}
        compose.onNodeWithTag("ink-canvas").performTouchInput {click(Offset(200f,250f))}
        compose.onNodeWithContentDescription("Drawing options").performClick();compose.onNodeWithText("Pages").performClick()
        compose.onNodeWithText("Page 1").performClick()
        compose.runOnIdle {assertEquals(model.uiState.value.pages.first().id,model.uiState.value.currentPageId);assertEquals(2,model.uiState.value.strokes.size)}
        compose.onNodeWithContentDescription("Collaboration").assertIsDisplayed()
    }
    @Test fun zoomBoundsCentroidAndTextLayoutCacheStayStable() {
        val zoom=ZoomState();zoom.updateViewportSize(1000f,1800f);zoom.setLimits(0.2f,4f)
        zoom.setOffset(Offset(25f,40f));val old=Offset(300f,600f);val world=zoom.screenToCanvas(old)
        zoom.transform(old+Offset(20f,30f),1.4f,Offset(20f,30f))
        assertEquals(world.x,zoom.screenToCanvas(old+Offset(20f,30f)).x,0.01f)
        assertEquals(world.y,zoom.screenToCanvas(old+Offset(20f,30f)).y,0.01f)
        repeat(200) {zoom.zoomBy(10f)};assertEquals(4f,zoom.scale,0.001f)
        zoom.transform(Offset.Zero,Float.NaN,Offset.Zero);zoom.setScale(0f);zoom.panBy(Offset(Float.POSITIVE_INFINITY,0f))
        assertTrue(zoom.offset.x.isFinite());assertTrue(zoom.offset.y.isFinite());assertEquals(4f,zoom.scale,0.001f)
        repeat(200) {zoom.zoomBy(0.1f)};assertEquals(0.2f,zoom.scale,0.001f)
        val small=ZoomState();small.setLimits(0.05f,0.5f);small.reset();small.zoomBy(0.5f)
        assertEquals("Reset must keep the logarithmic scale consistent with custom bounds",0.25f,small.scale,0.001f)
        val text=PageText(content=RichTextData("A paragraph that should retain its layout while the page moves",emptyList()))
        val layout=textLayout(text)
        repeat(100) {assertSame(layout,textLayout(text.copy(x=it.toFloat(),y=it*10f)))}
        assertNotSame(layout,textLayout(text.copy(width=200f)))
        assertNotSame(layout,textLayout(text.copy(fontSize=60f)))
        val centered=ZoomState();centered.updateViewportSize(1000f,1800f);centered.constrainDocument(800f,1000f,0f,0f)
        assertEquals(Offset(100f,400f),centered.offset)
    }
    @Test fun retainedLayerRendersLowerPageInkAfterPanningInAShortViewport() {
        val model=DrawingViewModel();model.setBackground(Color.White);model.setToolConfig(ToolConfig.MEDIUM_PEN.copy(baseWidth=28f,color=Color.Black))
        model.startStroke(100f,1200f);model.addStrokePoint(100f,1400f);model.endStroke()
        var dark by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {NotepadTheme(darkTheme=dark) {Box(Modifier.fillMaxSize()) {
            ZoomableCanvas(model,Modifier.fillMaxWidth().height(220.dp),zoomState=model.zoomState)
        }}}
        compose.waitForIdle()
        for(night in listOf(false,true)) {
            compose.runOnIdle {dark=night;model.zoomState.setScale(1f);model.zoomState.setOffset(Offset(0f,-1100f))}
            fun assertInkVisible() {
                val pixels=compose.onNodeWithTag("ink-canvas").captureToImage().toPixelMap()
                val ink=pixels[100,150].red
                val paper=pixels[160,150].red
                assertTrue("Lower-page ink must contrast with paper after pan/zoom (dark=$night, ink=$ink, paper=$paper)",
                    if(night) ink>0.8f && paper<0.2f else ink<0.2f && paper>0.8f)
            }
            assertInkVisible()
            compose.runOnIdle {model.zoomState.zoomCentroid(Offset(100f,150f),0.5f)}
            assertInkVisible()
        }
    }
}
