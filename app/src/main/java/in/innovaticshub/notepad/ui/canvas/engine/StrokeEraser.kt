package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import kotlin.math.sqrt

/**
 * Vector stroke eraser: removes or splits strokes intersecting the eraser path.
 */
object StrokeEraser {

    fun erase(strokes: List<Stroke>, eraserStroke: Stroke): List<Stroke> {
        if (strokes.isEmpty() || eraserStroke.points.size < 2) return strokes

        val eraserRadius = eraserStroke.toolConfig.strokeWidth() / 2f
        val result = mutableListOf<Stroke>()

        strokes.forEach { stroke ->
            if (stroke.toolConfig.isEraser) return@forEach
            val segments = splitStroke(stroke, eraserStroke.points, eraserRadius)
            result.addAll(segments)
        }
        return result
    }

    private fun splitStroke(
        stroke: Stroke,
        eraserPoints: List<StrokePoint>,
        eraserRadius: Float
    ): List<Stroke> {
        val runs = mutableListOf<MutableList<StrokePoint>>()
        var current = mutableListOf<StrokePoint>()

        stroke.points.forEach { point ->
            val hit = eraserPoints.any { ep ->
                distance(ep.x, ep.y, point.x, point.y) <= eraserRadius
            }
            if (hit) {
                if (current.size >= 2) runs.add(current)
                current = mutableListOf()
            } else {
                current.add(point)
            }
        }
        if (current.size >= 2) runs.add(current)

        return runs.map { points ->
            val builder = StrokeBuilder(stroke.toolConfig)
            points.forEach { builder.addPoint(it) }
            builder.build()
        }.filter { !it.isEmpty }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
