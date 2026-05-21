package `in`.innovaticshub.notepad.ui.canvas.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.copy

/**
 * Represents the type of drawing tool with unique characteristics.
 */
enum class ToolType {
    PEN,
    PENCIL,
    MARKER,
    HIGHLIGHTER,
    BRUSH,
    CALLIGRAPHY,
    ERASER,
    LASSO,
    SHAPE,
    TEXT
}

/**
 * Drawing tool style for different stroke effects.
 */
enum class DrawingStyle {
    SOLID,           // Continuous solid line
    SKETCHY,         // Multiple rough lines
    CALIGRAPHY,      // Variable width based on pressure
    DOTTED,          // Dashed/dotted line
    GLOW             // Glowing effect
}

/**
 * Configuration for a drawing tool.
 *
 * @param type The tool type
 * @param style Drawing style
 * @param color Stroke color
 * @param baseWidth Base stroke width
 * @param opacity Alpha value (0-1)
 * @param usePressure Whether to use pressure sensitivity
 */
data class ToolConfig(
    val type: ToolType = ToolType.PEN,
    val style: DrawingStyle = DrawingStyle.SOLID,
    val color: Color = Color.Black,
    val baseWidth: Float = 4f,
    val opacity: Float = 1f,
    val usePressure: Boolean = true
) {
    /**
     * Calculate actual stroke width based on pressure.
     */
    fun strokeWidth(pressure: Float = 1f): Float {
        val base = when (type) {
            ToolType.PEN -> baseWidth
            ToolType.PENCIL -> baseWidth * 0.5f
            ToolType.MARKER -> baseWidth * 2.5f
            ToolType.HIGHLIGHTER -> baseWidth * 4f
            ToolType.BRUSH -> baseWidth * 1.5f
            ToolType.CALLIGRAPHY -> baseWidth * 1.2f
            ToolType.ERASER -> baseWidth
            ToolType.LASSO, ToolType.SHAPE, ToolType.TEXT -> baseWidth
        }
        return if (usePressure && type != ToolType.ERASER) {
            base * (0.3f + pressure * 0.7f)
        } else {
            base
        }
    }

    /**
     * Get the effective color with opacity applied.
     */
    fun effectiveColor(): Color {
        return when (type) {
            ToolType.ERASER -> Color.Transparent
            else -> color.copy(alpha = opacity)
        }
    }

    /**
     * Whether this tool acts as an eraser.
     */
    val isEraser: Boolean
        get() = type == ToolType.ERASER

    /**
     * Whether this tool should have a glowing effect.
     */
    val hasGlow: Boolean
        get() = style == DrawingStyle.GLOW

    companion object {
        // Pen presets
        val FINE_PEN = ToolConfig(
            type = ToolType.PEN,
            style = DrawingStyle.SOLID,
            color = Color(0xFF1C1C1E),
            baseWidth = 2f,
            opacity = 1f,
            usePressure = true
        )

        val MEDIUM_PEN = ToolConfig(
            type = ToolType.PEN,
            style = DrawingStyle.SOLID,
            color = Color(0xFF1C1C1E),
            baseWidth = 4f,
            opacity = 1f,
            usePressure = true
        )

        val BOLD_PEN = ToolConfig(
            type = ToolType.PEN,
            style = DrawingStyle.SOLID,
            color = Color(0xFF1C1C1E),
            baseWidth = 8f,
            opacity = 1f,
            usePressure = true
        )

        // Pencil presets
        val GRAPHITE_PENCIL = ToolConfig(
            type = ToolType.PENCIL,
            style = DrawingStyle.SOLID,
            color = Color(0xFF4A4A4A),
            baseWidth = 2f,
            opacity = 0.8f,
            usePressure = true
        )

        val SKETCHY_PENCIL = ToolConfig(
            type = ToolType.PENCIL,
            style = DrawingStyle.SKETCHY,
            color = Color(0xFF2C2C2E),
            baseWidth = 1.5f,
            opacity = 0.7f,
            usePressure = true
        )

        // Marker presets
        val HIGHLIGHTER_YELLOW = ToolConfig(
            type = ToolType.MARKER,
            style = DrawingStyle.SOLID,
            color = Color(0xFFFFD60A),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        val HIGHLIGHTER_PINK = ToolConfig(
            type = ToolType.MARKER,
            style = DrawingStyle.SOLID,
            color = Color(0xFFFF6B9D),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        val HIGHLIGHTER_BLUE = ToolConfig(
            type = ToolType.MARKER,
            style = DrawingStyle.SOLID,
            color = Color(0xFF7DD3FC),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        // Brush presets
        val INK_BRUSH = ToolConfig(
            type = ToolType.BRUSH,
            style = DrawingStyle.CALIGRAPHY,
            color = Color(0xFF1C1C1E),
            baseWidth = 6f,
            opacity = 0.95f,
            usePressure = true
        )

        val WATERCOLOR_BRUSH = ToolConfig(
            type = ToolType.BRUSH,
            style = DrawingStyle.SOLID,
            color = Color(0xFF007AFF),
            baseWidth = 15f,
            opacity = 0.3f,
            usePressure = true
        )

        // Eraser presets
        val SMALL_ERASER = ToolConfig(
            type = ToolType.ERASER,
            style = DrawingStyle.SOLID,
            color = Color.Transparent,
            baseWidth = 10f,
            opacity = 1f,
            usePressure = false
        )

        val MEDIUM_ERASER = ToolConfig(
            type = ToolType.ERASER,
            style = DrawingStyle.SOLID,
            color = Color.Transparent,
            baseWidth = 25f,
            opacity = 1f,
            usePressure = false
        )

        val LARGE_ERASER = ToolConfig(
            type = ToolType.ERASER,
            style = DrawingStyle.SOLID,
            color = Color.Transparent,
            baseWidth = 50f,
            opacity = 1f,
            usePressure = false
        )

        /**
         * Get default configuration for a tool type.
         */
        fun defaultForType(type: ToolType): ToolConfig {
            return when (type) {
                ToolType.PEN -> MEDIUM_PEN
                ToolType.PENCIL -> GRAPHITE_PENCIL
                ToolType.MARKER -> HIGHLIGHTER_YELLOW
                ToolType.HIGHLIGHTER -> HIGHLIGHTER_YELLOW
                ToolType.BRUSH -> INK_BRUSH
                ToolType.CALLIGRAPHY -> INK_BRUSH.copy(style = DrawingStyle.CALIGRAPHY)
                ToolType.ERASER -> MEDIUM_ERASER
                ToolType.LASSO -> ToolConfig(
                    type = ToolType.LASSO,
                    style = DrawingStyle.DOTTED,
                    color = Color(0xFF007AFF),
                    baseWidth = 2f,
                    opacity = 0.95f,
                    usePressure = false
                )
                ToolType.SHAPE -> ToolConfig(type = ToolType.SHAPE, color = Color(0xFF007AFF), baseWidth = 3f, usePressure = false)
                ToolType.TEXT -> ToolConfig(type = ToolType.TEXT, color = Color(0xFF1C1C1E), baseWidth = 2f, usePressure = false)
            }
        }
    }
}

/**
 * Represents a single point in a stroke.
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a complete stroke.
 */
data class Stroke(
    val id: String = generateId(),
    val points: List<StrokePoint>,
    val path: androidx.compose.ui.graphics.Path,
    val toolConfig: ToolConfig
) {
    companion object {
        private const val ID_PREFIX = "stroke_"
        private var idCounter = 0L

        fun generateId(): String {
            return "${ID_PREFIX}${System.currentTimeMillis()}_${idCounter++}"
        }
    }

    val isEmpty: Boolean
        get() = points.size < 2

    val bounds: RectF
        get() = computeBounds(points)
}

/**
 * Rectangle bounds for strokes.
 */
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    companion object {
        val EMPTY = RectF(0f, 0f, 0f, 0f)
    }

    fun intersects(other: RectF): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }
}

private fun computeBounds(points: List<StrokePoint>): RectF {
    if (points.isEmpty()) return RectF.EMPTY
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    points.forEach {
        minX = minOf(minX, it.x)
        minY = minOf(minY, it.y)
        maxX = maxOf(maxX, it.x)
        maxY = maxOf(maxY, it.y)
    }
    return RectF(minX, minY, maxX, maxY)
}

/**
 * Builder for constructing strokes with smooth curves.
 */
class StrokeBuilder(
    private val toolConfig: ToolConfig,
    private val minPointDistance: Float = 0.35f
) {
    private val _points = mutableListOf<StrokePoint>()
    private var _path = androidx.compose.ui.graphics.Path()

    val points: List<StrokePoint> get() = _points.toList()
    val path: androidx.compose.ui.graphics.Path get() = _path
    val isEmpty: Boolean get() = _points.size < 2

    fun addPoint(point: StrokePoint) {
        if (_points.isEmpty()) {
            _points.add(point)
            _path.moveTo(point.x, point.y)
            return
        }

        val last = _points.last()
        val dx = point.x - last.x
        val dy = point.y - last.y
        if (dx * dx + dy * dy < minPointDistance * minPointDistance) return

        _points.add(point)

        if (_points.size >= 3) {
            val p1 = _points[_points.size - 2]
            val p2 = _points[_points.size - 1]
            _path.quadraticTo(
                p1.x, p1.y,
                (p1.x + p2.x) / 2f,
                (p1.y + p2.y) / 2f
            )
        } else {
            _path.lineTo(point.x, point.y)
        }
    }

    fun buildPreview(): Stroke {
        return Stroke(
            points = points,
            path = pathWithFinalSegment(),
            toolConfig = toolConfig
        )
    }

    fun build(): Stroke = buildPreview()

    fun reset() {
        _points.clear()
        _path = androidx.compose.ui.graphics.Path()
    }

    private fun pathWithFinalSegment(): androidx.compose.ui.graphics.Path {
        val previewPath = _path.copy()
        if (_points.size >= 2) {
            val last = _points.last()
            previewPath.lineTo(last.x, last.y)
        }
        return previewPath
    }
}
