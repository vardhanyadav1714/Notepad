package `in`.innovaticshub.notepad.ui.canvas.model

import androidx.compose.ui.graphics.Color

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
    GLOW,            // Glowing effect
    FOUNTAIN,
    DASHED
}

enum class EraserMode { SEGMENT, STROKE }
enum class ShapeKind { LINE, ARROW, RECTANGLE, ELLIPSE, TRIANGLE, DIAMOND }
enum class PaperStyle { PLAIN, RULED, GRID, DOTTED }
enum class SelectionShape { LASSO, RECTANGLE }

data class CanvasPage(
    val id: String = "page-1",
    val pdfPath: String? = null,
    val pdfPageIndex: Int = 0,
    val width: Float = 1000f,
    val height: Float = 1414f
)

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
    val usePressure: Boolean = true,
    val pressureSensitivity: Float = 1f,
    val smoothing: Float = 0.55f,
    val nibAngle: Float = 45f,
    val eraserMode: EraserMode = EraserMode.SEGMENT,
    val eraseHighlighterOnly: Boolean = false,
    val shape: ShapeKind = ShapeKind.LINE,
    val constrainShape: Boolean = false
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
            val p = pressure.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
            base * (1f - pressureSensitivity.coerceIn(0f, 1f) * (1f - (0.12f + p * 0.88f)))
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
            else -> color.copy(alpha = color.alpha * opacity.coerceIn(0f, 1f))
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
        val MARKER_YELLOW = ToolConfig(
            type = ToolType.MARKER,
            style = DrawingStyle.SOLID,
            color = Color(0xFFFFD60A),
            baseWidth = 8f,
            opacity = 0.55f,
            usePressure = false
        )

        val HIGHLIGHTER_YELLOW = ToolConfig(
            type = ToolType.HIGHLIGHTER,
            style = DrawingStyle.SOLID,
            color = Color(0xFFFFD60A),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        val HIGHLIGHTER_PINK = ToolConfig(
            type = ToolType.HIGHLIGHTER,
            style = DrawingStyle.SOLID,
            color = Color(0xFFFF6B9D),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        val HIGHLIGHTER_BLUE = ToolConfig(
            type = ToolType.HIGHLIGHTER,
            style = DrawingStyle.SOLID,
            color = Color(0xFF7DD3FC),
            baseWidth = 12f,
            opacity = 0.4f,
            usePressure = false
        )

        // Brush presets
        val INK_BRUSH = ToolConfig(
            type = ToolType.BRUSH,
            style = DrawingStyle.SOLID,
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
                ToolType.MARKER -> MARKER_YELLOW
                ToolType.HIGHLIGHTER -> HIGHLIGHTER_YELLOW
                ToolType.BRUSH -> INK_BRUSH
                ToolType.CALLIGRAPHY -> INK_BRUSH.copy(type = ToolType.CALLIGRAPHY, style = DrawingStyle.CALIGRAPHY)
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
    val timestamp: Long = System.currentTimeMillis(),
    val tilt: Float = 0f,
    val orientation: Float = 0f
)

/**
 * Represents a complete stroke.
 */
data class Stroke(
    val id: String = generateId(),
    val points: List<StrokePoint>,
    val path: androidx.compose.ui.graphics.Path,
    val toolConfig: ToolConfig,
    val pageId: String = "page-1"
) {
    private val cachedRenderData = lazy(LazyThreadSafetyMode.PUBLICATION) {
        `in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry.build(points, toolConfig)
    }
    internal val renderData get()=cachedRenderData.value
    internal val hasCachedRenderData get()=cachedRenderData.isInitialized()
    companion object {
        fun generateId(): String = "stroke_${java.util.UUID.randomUUID()}"
    }

    val isEmpty: Boolean
        get() = points.isEmpty()

    val bounds: RectF by lazy(LazyThreadSafetyMode.PUBLICATION) {
        computeBounds(points).let { bounds ->
            val radius = toolConfig.strokeWidth() / 2f
            RectF(bounds.left - radius, bounds.top - radius, bounds.right + radius, bounds.bottom + radius)
        }
    }
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
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
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
    private var strokeId = Stroke.generateId()
    private val _points = mutableListOf<StrokePoint>()

    val points: List<StrokePoint> get() = _points.toList()
    val isEmpty: Boolean get() = _points.isEmpty()

    fun addPoint(point: StrokePoint) {
        if (!point.x.isFinite() || !point.y.isFinite()) return
        if (_points.isEmpty()) {
            _points.add(point)
            return
        }

        val last = _points.last()
        val dx = point.x - last.x
        val dy = point.y - last.y
        if (dx * dx + dy * dy < minPointDistance * minPointDistance) return

        _points.add(point)

    }

    fun buildPreview(): Stroke {
        if(toolConfig.type==ToolType.HIGHLIGHTER && toolConfig.constrainShape && _points.size>1) {
            val points=listOf(_points.first(),_points.last())
            return Stroke(strokeId,points,`in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry.path(points),toolConfig.copy(smoothing=0f))
        }
        if (toolConfig.type == ToolType.SHAPE && _points.size > 1) {
            val shaped = `in`.innovaticshub.notepad.ui.canvas.engine.ShapeGeometry.points(_points.first(), _points.last(), toolConfig.shape, toolConfig.constrainShape)
            val ink = toolConfig.copy(type = ToolType.PEN, style = DrawingStyle.SOLID, usePressure = false, smoothing = 0f)
            return Stroke(strokeId, shaped, `in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry.path(shaped), ink)
        }
        val snapshot=points
        // Filled ink uses renderData's smoothed envelope. Only line effects and
        // lasso actually draw this centreline; avoid interpolating it twice.
        val linePoints=if(toolConfig.type==ToolType.LASSO || toolConfig.style in listOf(DrawingStyle.DASHED,DrawingStyle.DOTTED,DrawingStyle.GLOW))
            `in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry.samples(snapshot,toolConfig.smoothing) else snapshot
        return Stroke(
            id = strokeId,
            points = snapshot,
            path = `in`.innovaticshub.notepad.ui.canvas.engine.InkGeometry.path(linePoints),
            toolConfig = toolConfig
        )
    }

    fun build(): Stroke = buildPreview()

    fun reset() {
        _points.clear()
        strokeId = Stroke.generateId()
    }
}
