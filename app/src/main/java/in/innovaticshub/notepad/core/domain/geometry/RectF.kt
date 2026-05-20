package `in`.innovaticshub.notepad.core.domain.geometry

/**
 * Immutable rectangle using Float coordinates.
 * Used for bounds, hit testing, and viewport calculations.
 */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    val center: PointF get() = PointF(centerX, centerY)

    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    val isValid: Boolean get() = left <= right && top <= bottom

    companion object {
        fun fromLTRB(left: Float, top: Float, right: Float, bottom: Float) =
            RectF(left, top, right, bottom)

        fun fromSize(width: Float, height: Float, origin: PointF = PointF.Zero) =
            RectF(origin.x, origin.y, origin.x + width, origin.y + height)

        fun fromPoints(points: List<PointF>): RectF? {
            if (points.isEmpty()) return null
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            points.forEach {
                minX = minOf(minX, it.x)
                maxX = maxOf(maxX, it.x)
                minY = minOf(minY, it.y)
                maxY = maxOf(maxY, it.y)
            }
            return RectF(minX, minY, maxX, maxY)
        }

        val Zero = RectF(0f, 0f, 0f, 0f)
    }

    infix fun contains(point: PointF): Boolean {
        return point.x >= left && point.x <= right &&
                point.y >= top && point.y <= bottom
    }

    infix fun intersects(other: RectF): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }

    infix fun union(other: RectF): RectF {
        return RectF(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom)
        )
    }

    infix fun expand(amount: Float): RectF {
        return RectF(
            left = left - amount,
            top = top - amount,
            right = right + amount,
            bottom = bottom + amount
        )
    }

    fun scale(factor: Float, origin: PointF = center): RectF {
        val newWidth = width * factor
        val newHeight = height * factor
        return RectF(
            left = origin.x - newWidth / 2,
            top = origin.y - newHeight / 2,
            right = origin.x + newWidth / 2,
            bottom = origin.y + newHeight / 2
        )
    }

    fun translate(delta: PointF): RectF {
        return RectF(
            left = left + delta.x,
            top = top + delta.y,
            right = right + delta.x,
            bottom = bottom + delta.y
        )
    }

    fun roundToInt(): android.graphics.Rect {
        return android.graphics.Rect(
            left.toInt(),
            top.toInt(),
            right.toInt(),
            bottom.toInt()
        )
    }

    fun toAndroidRectF(): android.graphics.RectF {
        return android.graphics.RectF(left, top, right, bottom)
    }
}
