package `in`.innovaticshub.notepad.ui.canvas.engine

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import `in`.innovaticshub.notepad.ui.canvas.model.*
import kotlin.math.*

/** The same smoothed centerline and pressure envelope feed preview, storage, and export. */
object InkGeometry {
    data class InkSample(val x: Float, val y: Float, val radius: Float, val pressure: Float)
    data class RenderData(val samples: List<InkSample>, val outline: Path)

    fun samples(points: List<StrokePoint>, smoothing: Float): List<StrokePoint> {
        if (points.size < 3 || smoothing <= 0f) return points
        val tension = smoothing.coerceIn(0f, 1f) * 0.5f
        return buildList {
            add(points.first())
            for (i in 0 until points.lastIndex) {
                val a = points[(i - 1).coerceAtLeast(0)]
                val b = points[i]
                val c = points[i + 1]
                val d = points[(i + 2).coerceAtMost(points.lastIndex)]
                val steps = (hypot(c.x - b.x, c.y - b.y) / 3f).toInt().coerceIn(1, 20)
                for (step in 1..steps) {
                    val t = step.toFloat() / steps
                    val t2 = t * t
                    val t3 = t2 * t
                    fun coordinate(p0: Float, p1: Float, p2: Float, p3: Float) =
                        (2 * t3 - 3 * t2 + 1) * p1 + (t3 - 2 * t2 + t) * (p2 - p0) * tension +
                            (-2 * t3 + 3 * t2) * p2 + (t3 - t2) * (p3 - p1) * tension
                    add(b.copy(x = coordinate(a.x, b.x, c.x, d.x), y = coordinate(a.y, b.y, c.y, d.y),
                        pressure = b.pressure + (c.pressure - b.pressure) * t,
                        timestamp = b.timestamp + ((c.timestamp - b.timestamp) * t).toLong(),
                        tilt = b.tilt + (c.tilt - b.tilt) * t,
                        orientation = b.orientation + (c.orientation - b.orientation) * t))
                }
            }
        }
    }

    fun path(points: List<StrokePoint>): Path = Path().apply {
        points.firstOrNull()?.let { moveTo(it.x, it.y) }
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }

    fun build(points: List<StrokePoint>, tool: ToolConfig): RenderData {
        val smoothed = samples(points, tool.smoothing)
        val ink = smoothed.mapIndexed { index, point ->
            val previous = smoothed[(index - 1).coerceAtLeast(0)]
            val next = smoothed[(index + 1).coerceAtMost(smoothed.lastIndex)]
            val angle = atan2(next.y - previous.y, next.x - previous.x)
            val calligraphy = tool.type == ToolType.CALLIGRAPHY || tool.style == DrawingStyle.CALIGRAPHY
            val fountain = tool.style == DrawingStyle.FOUNTAIN
            val nib = if (calligraphy || fountain || tool.type == ToolType.MARKER) {
                val relative = angle - Math.toRadians(tool.nibAngle.toDouble()).toFloat()
                val thin = if (calligraphy) 0.22f else if (fountain) 0.5f else 0.65f
                sqrt(sin(relative).pow(2) + thin * thin * cos(relative).pow(2))
            } else 1f
            val taper = if (fountain || tool.type == ToolType.BRUSH) {
                val fromEnd = minOf(index, smoothed.lastIndex - index)
                (0.4f + fromEnd / 8f).coerceAtMost(1f)
            } else 1f
            val pencilTilt = if (tool.type == ToolType.PENCIL) 1f + sin(point.tilt.coerceIn(0f, 1.57f)) * 2f else 1f
            InkSample(point.x, point.y, (tool.strokeWidth(point.pressure) * nib * taper * pencilTilt / 2f).coerceAtLeast(0.06f), point.pressure)
        }
        val outline = Path()
        if (ink.size == 1) {
            val point = ink.single()
            outline.addOval(Rect(point.x - point.radius, point.y - point.radius, point.x + point.radius, point.y + point.radius))
        } else if (ink.isNotEmpty()) {
            val left = mutableListOf<Pair<Float, Float>>()
            val right = mutableListOf<Pair<Float, Float>>()
            ink.forEachIndexed { index, point ->
                val before = ink[(index - 1).coerceAtLeast(0)]
                val after = ink[(index + 1).coerceAtMost(ink.lastIndex)]
                val dx = after.x - before.x
                val dy = after.y - before.y
                val length = hypot(dx, dy).coerceAtLeast(0.001f)
                val nx = -dy / length * point.radius
                val ny = dx / length * point.radius
                left += (point.x + nx) to (point.y + ny)
                right += (point.x - nx) to (point.y - ny)
            }
            outline.moveTo(left.first().first, left.first().second)
            left.drop(1).forEach { outline.lineTo(it.first, it.second) }
            right.asReversed().forEach { outline.lineTo(it.first, it.second) }
            outline.close()
            if (tool.type != ToolType.HIGHLIGHTER && tool.type != ToolType.MARKER) {
                listOf(ink.first(), ink.last()).forEach { p -> outline.addOval(Rect(p.x-p.radius, p.y-p.radius, p.x+p.radius, p.y+p.radius)) }
            }
        }
        return RenderData(ink, outline)
    }
}
