package `in`.innovaticshub.notepad.core.domain.geometry

import kotlin.math.sqrt

/**
 * Immutable 2D point using Float for precision.
 * All spatial calculations use Float for GPU compatibility.
 */
data class PointF(
    val x: Float,
    val y: Float
) {
    companion object {
        val Zero = PointF(0f, 0f)
        operator fun invoke(x: Number, y: Number) = PointF(x.toFloat(), y.toFloat())
    }

    operator fun plus(other: PointF) = PointF(x + other.x, y + other.y)
    operator fun minus(other: PointF) = PointF(x - other.x, y - other.y)
    operator fun times(scalar: Float) = PointF(x * scalar, y * scalar)
    operator fun div(scalar: Float) = PointF(x / scalar, y / scalar)
    operator fun unaryMinus() = PointF(-x, -y)

    infix fun distanceTo(other: PointF): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    infix fun distanceSquaredTo(other: PointF): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    fun toOffset(): androidx.compose.ui.geometry.Offset =
        androidx.compose.ui.geometry.Offset(x, y)

    fun toMatrix(): FloatArray = floatArrayOf(x, y)

    override fun toString() = "PointF($x, $y)"
}
