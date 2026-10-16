package `in`.innovaticshub.notepad

import `in`.innovaticshub.notepad.ui.canvas.engine.*
import `in`.innovaticshub.notepad.ui.canvas.model.*
import org.junit.Assert.*
import org.junit.Test

class InkGeometryTest {
    @Test fun pressureResponseIsBoundedAndCanBeDisabled() {
        val pen=ToolConfig.MEDIUM_PEN.copy(baseWidth=10f)
        assertTrue(pen.strokeWidth(0.1f)<pen.strokeWidth(0.9f))
        assertEquals(pen.strokeWidth(0f),pen.strokeWidth(-1f),0f)
        assertEquals(pen.strokeWidth(1f),pen.strokeWidth(5f),0f)
        assertTrue(pen.strokeWidth(Float.NaN).isFinite())
        assertEquals(pen.copy(usePressure=false).strokeWidth(0f),pen.copy(usePressure=false).strokeWidth(1f),0f)
    }
    @Test fun sparseCrossingEraserPreservesBothSidesAtExactBoundaries() {
        val kept=StrokeEraser.survivingIntervals(StrokePoint(0f,0f),StrokePoint(100f,0f),listOf(StrokePoint(50f,-50f),StrokePoint(50f,50f)),10f)
        assertEquals(2,kept.size)
        assertEquals(0.4f,kept[0].end,0.0001f)
        assertEquals(0.6f,kept[1].start,0.0001f)
    }
    @Test fun tapEraserAndParallelSweepsClipCapsules() {
        val tap=StrokeEraser.survivingIntervals(StrokePoint(0f,0f),StrokePoint(100f,0f),listOf(StrokePoint(50f,0f)),10f)
        assertEquals(2,tap.size)
        assertEquals(0.4f,tap[0].end,0.0001f)
        val swept=StrokeEraser.survivingIntervals(StrokePoint(0f,0f),StrokePoint(100f,0f),listOf(StrokePoint(20f,0f),StrokePoint(80f,0f)),5f)
        assertEquals(0.15f,swept[0].end,0.0001f)
        assertEquals(0.85f,swept[1].start,0.0001f)
    }
    @Test fun missesAndOverlappingCutsDoNotRemoveUntouchedInk() {
        val missed=StrokeEraser.survivingIntervals(StrokePoint(0f,0f),StrokePoint(100f,0f),listOf(StrokePoint(200f,0f)),10f)
        assertEquals(listOf(StrokeEraser.Interval(0f,1f)),missed)
        val covered=StrokeEraser.survivingIntervals(StrokePoint(0f,0f),StrokePoint(100f,0f),listOf(StrokePoint(-10f,0f),StrokePoint(110f,0f)),5f)
        assertTrue(covered.isEmpty())
    }
    @Test fun constrainedShapesCloseAndSnapWithoutLosingEndpoints() {
        val square=ShapeGeometry.points(StrokePoint(10f,10f),StrokePoint(80f,40f),ShapeKind.RECTANGLE,true)
        assertEquals(square.first(),square.last())
        assertEquals(70f,square.maxOf {it.y}-square.minOf {it.y},0f)
        val line=ShapeGeometry.points(StrokePoint(0f,0f),StrokePoint(100f,2f),ShapeKind.LINE,true)
        assertEquals(0f,line.last().y,0.001f)
        val ellipse=ShapeGeometry.points(StrokePoint(0f,0f),StrokePoint(100f,50f),ShapeKind.ELLIPSE)
        assertEquals(ellipse.first().x,ellipse.last().x,0.001f)
    }
    @Test fun smoothingKeepsPressureTimingAndEndpoints() {
        val source=listOf(StrokePoint(0f,0f,0.1f,10),StrokePoint(40f,50f,0.4f,20),StrokePoint(80f,0f,0.9f,30))
        val smoothed=InkGeometry.samples(source,0.8f)
        assertEquals(source.first(),smoothed.first())
        assertEquals(source.last().x,smoothed.last().x,0f)
        assertEquals(source.last().timestamp,smoothed.last().timestamp)
        assertTrue(smoothed.all {it.pressure in 0.1f..0.9f})
        assertEquals(source,InkGeometry.samples(source,0f))
    }
    @Test fun historySupportsWholeDocumentUndoAndNewBranches() {
        val history=CanvasHistory<String>(2)
        history.pushBeforeChange("one");history.pushBeforeChange("two")
        assertEquals("two",history.undo("three"))
        assertEquals("three",history.redo("two"))
        assertEquals("two",history.undo("three"))
        history.pushBeforeChange("two")
        assertFalse(history.canRedo)
    }
}
