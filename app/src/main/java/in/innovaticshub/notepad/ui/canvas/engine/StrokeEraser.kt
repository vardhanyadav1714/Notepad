package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import kotlin.math.max
import kotlin.math.min
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

        stroke.points.forEachIndexed { index, point ->
            val hit = pointHitsEraser(point, eraserPoints, eraserRadius) ||
                index > 0 && segmentHitsEraser(stroke.points[index - 1], point, eraserPoints, eraserRadius)
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

    private fun pointHitsEraser(
        point: StrokePoint,
        eraserPoints: List<StrokePoint>,
        eraserRadius: Float
    ): Boolean {
        return eraserPoints.zipWithNext().any { (a, b) ->
            distancePointToSegment(point.x, point.y, a.x, a.y, b.x, b.y) <= eraserRadius
        } || eraserPoints.any { ep ->
            distance(ep.x, ep.y, point.x, point.y) <= eraserRadius
        }
    }

    private fun segmentHitsEraser(
        a: StrokePoint,
        b: StrokePoint,
        eraserPoints: List<StrokePoint>,
        eraserRadius: Float
    ): Boolean {
        return eraserPoints.zipWithNext().any { (e1, e2) ->
            segmentDistance(a.x, a.y, b.x, b.y, e1.x, e1.y, e2.x, e2.y) <= eraserRadius
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun distancePointToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0f) return distance(px, py, ax, ay)

        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        return distance(px, py, ax + t * dx, ay + t * dy)
    }

    private fun segmentDistance(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float
    ): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
        return min(
            min(distancePointToSegment(ax, ay, cx, cy, dx, dy), distancePointToSegment(bx, by, cx, cy, dx, dy)),
            min(distancePointToSegment(cx, cy, ax, ay, bx, by), distancePointToSegment(dx, dy, ax, ay, bx, by))
        )
    }

    private fun segmentsIntersect(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        dx: Float,
        dy: Float
    ): Boolean {
        if (max(ax, bx) < min(cx, dx) || max(cx, dx) < min(ax, bx) ||
            max(ay, by) < min(cy, dy) || max(cy, dy) < min(ay, by)
        ) return false

        fun orientation(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float): Float {
            return (qy - py) * (rx - qx) - (qx - px) * (ry - qy)
        }

        val o1 = orientation(ax, ay, bx, by, cx, cy)
        val o2 = orientation(ax, ay, bx, by, dx, dy)
        val o3 = orientation(cx, cy, dx, dy, ax, ay)
        val o4 = orientation(cx, cy, dx, dy, bx, by)
        return o1 * o2 <= 0f && o3 * o4 <= 0f
    }
}
