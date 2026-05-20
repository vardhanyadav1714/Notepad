package `in`.innovaticshub.notepad.core.domain.geometry

/**
 * Immutable size using Float dimensions.
 */
data class SizeF(
    val width: Float,
    val height: Float
) {
    companion object {
        operator fun invoke(width: Number, height: Number) =
            SizeF(width.toFloat(), height.toFloat())

        val Zero = SizeF(0f, 0f)
        val Unspecified = SizeF(Float.NaN, Float.NaN)
    }

    val area: Float get() = width * height
    val isEmpty: Boolean get() = width <= 0f || height <= 0f
    val isValid: Boolean get() = width > 0f && height > 0f

    val aspectRatio: Float get() = if (height != 0f) width / height else 1f

    fun toRect(origin: PointF = PointF.Zero) = RectF(
        left = origin.x,
        top = origin.y,
        right = origin.x + width,
        bottom = origin.y + height
    )

    fun scale(factor: Float) = SizeF(width * factor, height * factor)
    fun scale(widthFactor: Float, heightFactor: Float) =
        SizeF(width * widthFactor, height * heightFactor)

    operator fun times(scalar: Float) = scale(scalar)
    operator fun div(scalar: Float) = SizeF(width / scalar, height / scalar)
}
