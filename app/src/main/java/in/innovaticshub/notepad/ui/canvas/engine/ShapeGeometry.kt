package `in`.innovaticshub.notepad.ui.canvas.engine

import `in`.innovaticshub.notepad.ui.canvas.model.*
import kotlin.math.*

object ShapeGeometry {
    fun points(start: StrokePoint, end: StrokePoint, shape: ShapeKind, constrain: Boolean = false): List<StrokePoint> {
        var dx = end.x - start.x
        var dy = end.y - start.y
        if (constrain) {
            if (shape == ShapeKind.LINE || shape == ShapeKind.ARROW) {
                val distance = hypot(dx, dy)
                val angle = (atan2(dy, dx) / (PI / 12)).roundToInt() * (PI / 12)
                dx = (cos(angle) * distance).toFloat(); dy = (sin(angle) * distance).toFloat()
            } else {
                val length = maxOf(abs(dx), abs(dy)); dx = length * if (dx < 0) -1 else 1; dy = length * if (dy < 0) -1 else 1
            }
        }
        fun p(x: Float, y: Float) = start.copy(x = x, y = y, pressure = 1f)
        val last = p(start.x + dx, start.y + dy)
        val l = minOf(start.x, last.x); val r = maxOf(start.x, last.x)
        val t = minOf(start.y, last.y); val b = maxOf(start.y, last.y)
        val cx = (l + r) / 2; val cy = (t + b) / 2
        return when (shape) {
            ShapeKind.LINE -> listOf(start, last)
            ShapeKind.ARROW -> {
                val angle = atan2(dy, dx); val size = (hypot(dx, dy) * 0.22f).coerceIn(12f, 60f)
                listOf(start, last, p(last.x - cos(angle - 0.5f) * size, last.y - sin(angle - 0.5f) * size), last,
                    p(last.x - cos(angle + 0.5f) * size, last.y - sin(angle + 0.5f) * size))
            }
            ShapeKind.RECTANGLE -> listOf(p(l,t),p(r,t),p(r,b),p(l,b),p(l,t))
            ShapeKind.TRIANGLE -> listOf(p(cx,t),p(r,b),p(l,b),p(cx,t))
            ShapeKind.DIAMOND -> listOf(p(cx,t),p(r,cy),p(cx,b),p(l,cy),p(cx,t))
            ShapeKind.ELLIPSE -> (0..96).map { i -> val a = i * 2 * PI / 96; p(cx + cos(a).toFloat()*(r-l)/2, cy + sin(a).toFloat()*(b-t)/2) }
        }
    }

    /** Conservative recognition: leave handwriting alone unless the geometry is confident. */
    fun recognize(stroke: Stroke): Stroke {
        val points = stroke.points
        if (points.size < 6) return stroke
        val first = points.first(); val last = points.last()
        val length = points.zipWithNext().sumOf { (a,b) -> hypot(b.x-a.x,b.y-a.y).toDouble() }.toFloat()
        val chord = hypot(last.x-first.x,last.y-first.y)
        val left = points.minOf { it.x }; val top = points.minOf { it.y }
        val right = points.maxOf { it.x }; val bottom = points.maxOf { it.y }
        val diagonal = hypot(right-left,bottom-top)
        if (diagonal < 24f) return stroke
        val shape = if (chord > diagonal * 0.85f && length / chord < 1.07f) ShapeKind.LINE
        else if (chord < diagonal * 0.18f && right-left > 15f && bottom-top > 15f) {
            val cx=(left+right)/2; val cy=(top+bottom)/2
            val error = points.map { abs(hypot((it.x-cx)/((right-left)/2), (it.y-cy)/((bottom-top)/2))-1f) }.average()
            val perimeter = 2*(right-left+bottom-top)
            val edgeError = points.map { minOf(abs(it.x-left),abs(it.x-right),abs(it.y-top),abs(it.y-bottom))/diagonal }.average()
            when { edgeError < 0.025 && length/perimeter in 0.85f..1.15f -> ShapeKind.RECTANGLE
                error < 0.1 -> ShapeKind.ELLIPSE
                else -> return stroke }
        } else return stroke
        val from = if (shape == ShapeKind.LINE) first else first.copy(x=left,y=top)
        val to = if (shape == ShapeKind.LINE) last else last.copy(x=right,y=bottom)
        val snapped = points(from,to,shape)
        return stroke.copy(points=snapped, path=InkGeometry.path(snapped), toolConfig=stroke.toolConfig.copy(usePressure=false,smoothing=0f))
    }
}
