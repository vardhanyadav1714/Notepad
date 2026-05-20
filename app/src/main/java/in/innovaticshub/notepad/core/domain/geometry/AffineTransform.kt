package `in`.innovaticshub.notepad.core.domain.geometry

import android.graphics.Matrix

/**
 * Represents a 2D affine transformation for viewport mapping.
 * Handles: translation, scale, rotation around arbitrary origin.
 *
 * Used to transform between:
 * - World Space: Infinite canvas coordinates
 * - Screen Space: Device display coordinates
 */
data class AffineTransform(
    val scaleX: Float = 1f,
    val skewX: Float = 0f,
    val transX: Float = 0f,
    val skewY: Float = 0f,
    val scaleY: Float = 1f,
    val transY: Float = 0f,
    val persp0: Float = 0f,
    val persp1: Float = 0f,
    val persp2: Float = 1f
) {
    val zoomLevel: Float get() = scaleX

    val isIdentity: Boolean
        get() = scaleX == 1f && scaleY == 1f &&
                transX == 0f && transY == 0f &&
                skewX == 0f && skewY == 0f

    val isInvertible: Boolean
        get() = determinant() != 0f

    companion object {
        val Identity = AffineTransform()

        /**
         * Create a viewport transform centered on a world point.
         * @param zoom Zoom level (1.0 = 100%)
         * @param center World point that should be at screen center
         * @param screenSize Screen dimensions
         */
        fun centeredViewport(
            zoom: Float,
            center: PointF,
            screenSize: SizeF
        ): AffineTransform {
            return AffineTransform(
                scaleX = zoom,
                scaleY = zoom,
                transX = screenSize.width / 2 - center.x * zoom,
                transY = screenSize.height / 2 - center.y * zoom
            )
        }

        fun translation(x: Float, y: Float) = AffineTransform(transX = x, transY = y)
        fun scale(zoom: Float) = AffineTransform(scaleX = zoom, scaleY = zoom)
        fun rotation(angleDegrees: Float): AffineTransform {
            val rad = Math.toRadians(angleDegrees.toDouble()).toFloat()
            val cos = kotlin.math.cos(rad)
            val sin = kotlin.math.sin(rad)
            return AffineTransform(
                scaleX = cos,
                skewX = -sin,
                skewY = sin,
                scaleY = cos
            )
        }
    }

    fun determinant(): Float {
        return scaleX * scaleY - skewX * skewY
    }

    /**
     * Transform a point from world space to screen space.
     */
    fun transform(point: PointF): PointF {
        return PointF(
            x = point.x * scaleX + point.y * skewX + transX,
            y = point.x * skewY + point.y * scaleY + transY
        )
    }

    /**
     * Transform a point from screen space to world space (inverse transform).
     */
    fun inverseTransform(point: PointF): PointF {
        val det = determinant()
        if (det == 0f) return point

        val invDet = 1f / det
        val invScaleX = scaleY * invDet
        val invScaleY = scaleX * invDet
        val invSkewX = -skewX * invDet
        val invSkewY = -skewY * invDet

        val dx = point.x - transX
        val dy = point.y - transY

        return PointF(
            x = dx * invScaleX + dy * invSkewX,
            y = dx * invSkewY + dy * invScaleY
        )
    }

    /**
     * Transform a rectangle from world space to screen space.
     * Returns the bounding box of the transformed corners.
     */
    fun transform(rect: RectF): RectF {
        val corners = listOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.left to rect.bottom,
            rect.right to rect.bottom
        ).map { (x, y) -> transform(PointF(x, y)) }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        corners.forEach {
            minX = minOf(minX, it.x)
            maxX = maxOf(maxX, it.x)
            minY = minOf(minY, it.y)
            maxY = maxOf(maxY, it.y)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Transform a length from world space to screen space.
     */
    fun transformLength(length: Float): Float {
        // Approximate by averaging X and Y scale
        return length * (scaleX + scaleY) / 2f
    }

    /**
     * Transform a length from screen space to world space.
     */
    fun inverseTransformLength(length: Float): Float {
        return length / ((scaleX + scaleY) / 2f)
    }

    fun withTranslation(x: Float, y: Float): AffineTransform {
        return copy(transX = transX + x, transY = transY + y)
    }

    fun withScale(newScale: Float, origin: PointF = PointF.Zero): AffineTransform {
        val scaledOrigin = transform(origin)
        return copy(
            scaleX = newScale,
            scaleY = newScale,
            transX = transX + scaledOrigin.x - origin.x * newScale,
            transY = transY + scaledOrigin.y - origin.y * newScale
        )
    }

    fun withRotation(angleDegrees: Float, origin: PointF = PointF.Zero): AffineTransform {
        val rad = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(rad)
        val sin = kotlin.math.sin(rad)

        // Translate origin to zero, rotate, translate back
        val originTransformed = transform(origin)

        return copy(
            scaleX = scaleX * cos + skewY * sin,
            skewX = skewX * cos + scaleY * sin,
            transX = transX + originTransformed.x - (origin.x * (scaleX * cos + skewY * sin) + origin.y * (skewX * cos + scaleY * sin)),
            skewY = scaleX * -sin + skewY * cos,
            scaleY = skewX * -sin + scaleY * cos,
            transY = transY + originTransformed.y - (origin.x * (scaleX * -sin + skewY * cos) + origin.y * (skewX * -sin + scaleY * cos))
        )
    }

    /**
     * Compose this transform with another (this ○ other).
     * Result transforms by other first, then by this.
     */
    fun compose(other: AffineTransform): AffineTransform {
        return AffineTransform(
            scaleX = scaleX * other.scaleX + skewY * other.skewX,
            skewX = skewX * other.scaleX + scaleY * other.skewX,
            transX = scaleX * other.transX + skewY * other.transY + transX,
            skewY = scaleX * other.skewY + skewY * other.scaleY,
            scaleY = skewX * other.skewY + scaleY * other.scaleY,
            transY = skewX * other.transX + scaleY * other.transY + transY
        )
    }

    fun toAndroidMatrix(): Matrix {
        val matrix = Matrix()
        // Android Matrix expects values in column-major order:
        // [MSCALE_X, MSKEW_X, MTRANS_X]
        // [MSKEW_Y, MSCALE_Y, MTRANS_Y]
        // [MPERSP_0, MPERSP_1, MPERSP_2]
        val values = floatArrayOf(
            scaleX, skewX, transX,
            skewY, scaleY, transY,
            persp0, persp1, persp2
        )
        matrix.setValues(values)
        return matrix
    }
}

/**
 * Extension to create AffineTransform from Android Matrix.
 */
fun AffineTransform.Companion.fromAndroidMatrix(matrix: Matrix): AffineTransform {
    val values = FloatArray(9)
    matrix.getValues(values)
    return AffineTransform(
        scaleX = values[0],
        skewX = values[1],
        transX = values[2],
        skewY = values[3],
        scaleY = values[4],
        transY = values[5],
        persp0 = values[6],
        persp1 = values[7],
        persp2 = values[8]
    )
}
