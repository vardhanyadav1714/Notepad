package `in`.innovaticshub.notepad

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import `in`.innovaticshub.notepad.ui.canvas.drawing.ZoomableCanvas
import `in`.innovaticshub.notepad.ui.canvas.model.*
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.NotepadTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InkInputTest {
    @get:Rule val compose=createComposeRule()
    private fun canvas(model:DrawingViewModel) {
        compose.setContent { NotepadTheme { ZoomableCanvas(model,Modifier.fillMaxSize(),zoomState=model.zoomState) } }
        compose.waitForIdle()
    }
    @Test fun tappingMakesADotAndFingerOnlyPanDoesNotMakeInk() {
        val model=DrawingViewModel();canvas(model)
        compose.onNodeWithTag("ink-canvas").performTouchInput {click(Offset(100f,200f))}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size);assertEquals(1,model.uiState.value.strokes.single().points.size);model.setFingerDrawing(false)}
        val before=model.zoomState.offset
        compose.onNodeWithTag("ink-canvas").performTouchInput {swipe(Offset(200f,300f),Offset(300f,450f),300)}
        compose.runOnIdle {assertEquals(1,model.uiState.value.strokes.size);assertNotEquals(before,model.zoomState.offset)}
    }
    @Test fun stylusPressureSurvivesAPalmContactAndPenOnlyMode() {
        val model=DrawingViewModel();model.setFingerDrawing(false);canvas(model)
        val bounds=compose.onNodeWithTag("ink-canvas").fetchSemanticsNode().boundsInRoot
        val originX=bounds.left+200f;val originY=bounds.top+350f
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val downTime=SystemClock.uptimeMillis()
        fun inject(action:Int,x:Float,pressure:Float,palm:Boolean=false) {
            val properties=mutableListOf(MotionEvent.PointerProperties().apply {id=0;toolType=MotionEvent.TOOL_TYPE_STYLUS})
            val coordinates=mutableListOf(MotionEvent.PointerCoords().apply {this.x=originX+x;this.y=originY;this.pressure=pressure;size=0.02f;setAxisValue(MotionEvent.AXIS_TILT,0.4f)})
            if(palm) {
                properties+=MotionEvent.PointerProperties().apply {id=1;toolType=MotionEvent.TOOL_TYPE_FINGER}
                coordinates+=MotionEvent.PointerCoords().apply {this.x=originX+300f;this.y=originY+300f;this.pressure=1f;size=0.5f}
            }
            val event=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,properties.size,properties.toTypedArray(),coordinates.toTypedArray(),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
            try {assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))} finally {event.recycle()}
        }
        inject(MotionEvent.ACTION_DOWN,0f,0.15f)
        inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),30f,0.3f,true)
        inject(MotionEvent.ACTION_MOVE,100f,0.7f,true)
        inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),160f,0.85f,true)
        inject(MotionEvent.ACTION_UP,200f,0.95f)
        compose.waitUntil(5000) {model.uiState.value.strokes.isNotEmpty()}
        compose.runOnIdle {
            val stroke=model.uiState.value.strokes.single()
            assertTrue(stroke.points.any {it.pressure<0.3f})
            assertTrue(stroke.points.any {it.pressure>0.8f})
            assertTrue(stroke.points.any {it.tilt>0.3f})
            assertFalse(model.uiState.value.isDrawing)
        }
    }
}
