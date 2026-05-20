package `in`.innovaticshub.notepad.core.domain.model

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.domain.geometry.RectF
import `in`.innovaticshub.notepad.core.domain.util.generateId
import `in`.innovaticshub.notepad.core.engine.spatial.SpatialElement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

/**
 * Base class for all canvas elements.
 * Elements are immutable - mutations create new instances.
 */
sealed class CanvasElement : SpatialElement {
    abstract override val id: String
    abstract override val bounds: RectF
    abstract val layerId: String
    abstract val isVisible: Boolean
    abstract val opacity: Float

    /**
     * Check if element can be selected at given point.
     */
    open fun hitTest(point: PointF, tolerance: Float = 10f): Boolean {
        return bounds.expand(tolerance).contains(point)
    }

    /**
     * Get a more precise hit test bounds (for selection).
     */
    open fun getHitBounds(tolerance: Float = 10f): RectF {
        return bounds.expand(tolerance)
    }

    companion object {
        const val DEFAULT_LAYER_ID = "default_layer"
    }
}

/**
 * A stroke drawn on the canvas.
 * Optimized for efficient rendering and storage.
 */
data class Stroke(
    override val id: String = generateId("stroke"),
    override val layerId: String = DEFAULT_LAYER_ID,
    val points: List<PointF>,
    val style: StrokeStyle,
    val pressures: List<Float> = emptyList(),
    val timestamps: List<Long> = emptyList(),
    override val isVisible: Boolean = true,
    override val opacity: Float = 1f,
    val isClosed: Boolean = false
) : CanvasElement() {
    override val bounds: RectF by lazy {
        RectF.fromPoints(points) ?: RectF.Zero
    }

    val isEmpty: Boolean get() = points.size < 2

    val strokeLength: Float by lazy {
        var total = 0f
        for (i in 1 until points.size) {
            total += points[i].distanceTo(points[i - 1])
        }
        total
    }

    fun hasPressureData(): Boolean = pressures.size == points.size

    fun getPressureAt(index: Int): Float {
        return pressures.getOrNull(index) ?: 1f
    }

    fun getVelocityAt(index: Int): Float {
        if (index == 0 || timestamps.isEmpty()) return 0f
        val dt = timestamps[index] - timestamps[index - 1]
        if (dt <= 0) return 0f
        val distance = points[index].distanceTo(points[index - 1])
        return distance / dt
    }

    override fun hitTest(point: PointF, tolerance: Float): Boolean {
        if (!isVisible) return false
        if (!bounds.expand(tolerance).contains(point)) return false

        // Check each segment
        val hitRadius = kotlin.math.max(tolerance, style.width / 2)
        for (i in 1 until points.size) {
            if (pointToSegmentDistance(point, points[i - 1], points[i]) <= hitRadius) {
                return true
            }
        }
        return false
    }

    override fun getHitBounds(tolerance: Float): RectF {
        return bounds.expand(kotlin.math.max(tolerance, style.width / 2))
    }

    fun withPoints(newPoints: List<PointF>): Stroke {
        return copy(points = newPoints)
    }

    fun withStyle(newStyle: StrokeStyle): Stroke {
        return copy(style = newStyle)
    }

    fun withLayer(newLayerId: String): Stroke {
        return copy(layerId = newLayerId)
    }
}

/**
 * Stroke rendering properties.
 */
data class StrokeStyle(
    val color: Color,
    val width: Float,
    val cap: StrokeCap = StrokeCap.Round,
    val join: StrokeJoin = StrokeJoin.Round,
    val blendMode: BlendMode = BlendMode.SrcOver
) {
    companion object {
        val Default = StrokeStyle(
            color = Color.Black,
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val Pencil = StrokeStyle(
            color = Color(0xFF2B2B2B),
            width = 2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val Pen = StrokeStyle(
            color = Color(0xFF0066CC),
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val Highlighter = StrokeStyle(
            color = Color(0xFFFFEB3B),
            width = 24f,
            cap = StrokeCap.Square,
            join = StrokeJoin.Miter,
            blendMode = BlendMode.Multiply
        )

        val Marker = StrokeStyle(
            color = Color(0xFFFF0000),
            width = 8f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
}

/**
 * Blend modes for stroke rendering.
 */
enum class BlendMode {
    SrcOver,
    Multiply,
    Screen,
    Overlay,
    Darken,
    Lighten
}

/**
 * Text block element.
 */
data class TextBlock(
    override val id: String = generateId("text"),
    override val layerId: String = DEFAULT_LAYER_ID,
    val position: PointF,
    val text: String,
    val fontSize: Float = 16f,
    val fontFamily: String = "sans-serif",
    val color: Color = Color.Black,
    val width: Float = 200f,
    val height: Float = 100f,
    val alignment: TextAlignment = TextAlignment.Start,
    override val isVisible: Boolean = true,
    override val opacity: Float = 1f
) : CanvasElement() {
    override val bounds: RectF
        get() = RectF(
            left = position.x,
            top = position.y,
            right = position.x + width,
            bottom = position.y + height
        )

    enum class TextAlignment { Start, Center, End }
}

/**
 * Image element.
 */
data class ImageElement(
    override val id: String = generateId("image"),
    override val layerId: String = DEFAULT_LAYER_ID,
    val position: PointF,
    val imageUri: String,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    override val isVisible: Boolean = true,
    override val opacity: Float = 1f
) : CanvasElement() {
    override val bounds: RectF by lazy {
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        RectF(
            left = position.x - scaledWidth / 2,
            top = position.y - scaledHeight / 2,
            right = position.x + scaledWidth / 2,
            bottom = position.y + scaledHeight / 2
        )
    }
}

/**
 * Shape element (rectangle, circle, line, etc.).
 */
data class ShapeElement(
    override val id: String = generateId("shape"),
    override val layerId: String = DEFAULT_LAYER_ID,
    val type: ShapeType,
    override val bounds: RectF,
    val style: ShapeStyle,
    val rotation: Float = 0f,
    override val isVisible: Boolean = true,
    override val opacity: Float = 1f
) : CanvasElement() {

    override fun hitTest(point: PointF, tolerance: Float): Boolean {
        if (!isVisible) return false
        if (type is ShapeType.Line) {
            val linePoints = getLinePoints()
            if (linePoints.size >= 2) {
                return pointToSegmentDistance(point, linePoints[0], linePoints[1]) <= kotlin.math.max(tolerance, style.strokeWidth / 2f)
            }
        }
        return super.hitTest(point, tolerance)
    }

    private fun getLinePoints(): List<PointF> {
        return if (type is ShapeType.Line) {
            listOf(
                PointF(bounds.left, bounds.top),
                PointF(bounds.right, bounds.bottom)
            )
        } else emptyList()
    }
}

sealed class ShapeType {
    data object Rectangle : ShapeType()
    data object Circle : ShapeType()
    data object Ellipse : ShapeType()
    data object Line : ShapeType()
    data object Triangle : ShapeType()
    data object Polygon : ShapeType()
    data class Freeform(val points: List<PointF>) : ShapeType()
}

data class ShapeStyle(
    val fillColor: Color? = null,
    val strokeColor: Color = Color.Black,
    val strokeWidth: Float = 2f,
    val strokeDashArray: List<Float> = emptyList()
)

/**
 * Helper function for point-to-segment distance.
 */
private fun pointToSegmentDistance(point: PointF, start: PointF, end: PointF): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy

    if (lengthSquared == 0f) {
        return point.distanceTo(start)
    }

    val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared
    val clampedT = t.coerceIn(0f, 1f)

    val closestX = start.x + clampedT * dx
    val closestY = start.y + clampedT * dy

    return point.distanceTo(PointF(closestX, closestY))
}
