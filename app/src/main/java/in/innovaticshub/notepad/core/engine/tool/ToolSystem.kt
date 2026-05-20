package `in`.innovaticshub.notepad.core.engine.tool

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.domain.model.CanvasElement
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import `in`.innovaticshub.notepad.core.domain.model.StrokeStyle
import `in`.innovaticshub.notepad.core.engine.gesture.PointerEvent
import `in`.innovaticshub.notepad.core.engine.gesture.PointerTool
import `in`.innovaticshub.notepad.core.engine.viewport.Viewport
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tool system architecture for canvas interaction.
 *
 * Tools are stateless - configuration is in ToolProperties
 * Each tool handles its own input processing and command generation.
 */
sealed class Tool {
    abstract val id: String
    abstract val displayName: String
    abstract val icon: String

    data object Select : Tool() {
        override val id = "select"
        override val displayName = "Select"
        override val icon = "cursor"
    }

    data object Pan : Tool() {
        override val id = "pan"
        override val displayName = "Pan"
        override val icon = "pan"
    }

    data class Draw(
        val brushType: BrushType
    ) : Tool() {
        override val id = "draw_${brushType.name.lowercase()}"
        override val displayName = brushType.displayName
        override val icon = brushType.icon
    }

    data object Eraser : Tool() {
        override val id = "eraser"
        override val displayName = "Eraser"
        override val icon = "eraser"
    }

    data object Lasso : Tool() {
        override val id = "lasso"
        override val displayName = "Lasso"
        override val icon = "lasso"
    }

    data class Shape(val shapeType: ShapeType) : Tool() {
        override val id = "shape_${shapeType.name.lowercase()}"
        override val displayName = shapeType.displayName
        override val icon = shapeType.icon
    }

    data object Text : Tool() {
        override val id = "text"
        override val displayName = "Text"
        override val icon = "text"
    }

    data object Hand : Tool() {
        override val id = "hand"
        override val displayName = "Hand"
        override val icon = "hand"
    }
}

enum class BrushType(val displayName: String, val icon: String) {
    Pencil("Pencil", "pencil"),
    Pen("Pen", "pen"),
    Highlighter("Highlighter", "highlighter"),
    Marker("Marker", "marker"),
    Brush("Brush", "brush"),
    Airbrush("Airbrush", "airbrush"),
    Chalk("Chalk", "chalk"),
    Spray("Spray", "spray")
}

enum class ShapeType(val displayName: String, val icon: String) {
    Line("Line", "line"),
    Rectangle("Rectangle", "rectangle"),
    Circle("Circle", "circle"),
    Ellipse("Ellipse", "ellipse"),
    Triangle("Triangle", "triangle"),
    Polygon("Polygon", "polygon"),
    Arrow("Arrow", "arrow")
}

/**
 * Tool configuration properties.
 */
data class ToolProperties(
    val currentTool: Tool = Tool.Draw(BrushType.Pen),
    val primaryColor: Color = Color.Black,
    val secondaryColor: Color = Color.White,
    val strokeWidth: Float = 4f,
    val opacity: Float = 1f,
    val smoothing: Float = 0.5f,
    val pressureSensitivity: Float = 0.5f,
    val selectedLayerId: String = CanvasElement.DEFAULT_LAYER_ID
) {
    fun withTool(tool: Tool) = copy(currentTool = tool)
    fun withColor(color: Color) = copy(primaryColor = color)
    fun withStrokeWidth(width: Float) = copy(strokeWidth = width)
    fun withOpacity(opacity: Float) = copy(opacity = opacity)
    fun withSmoothing(smoothing: Float) = copy(smoothing = smoothing)
    fun withLayer(layerId: String) = copy(selectedLayerId = layerId)
}

/**
 * Tool manager handles tool switching and configuration.
 */
class ToolManager {
    private val mutableProperties: MutableStateFlow<ToolProperties> = MutableStateFlow(
        ToolProperties()
    )

    val properties: StateFlow<ToolProperties> = mutableProperties.asStateFlow()

    fun selectTool(tool: Tool) {
        mutableProperties.value = mutableProperties.value.withTool(tool)
    }

    fun setColor(color: Color) {
        mutableProperties.value = mutableProperties.value.withColor(color)
    }

    fun setStrokeWidth(width: Float) {
        mutableProperties.value = mutableProperties.value.withStrokeWidth(width)
    }

    fun setOpacity(opacity: Float) {
        mutableProperties.value = mutableProperties.value.withOpacity(opacity)
    }

    fun setSmoothing(smoothing: Float) {
        mutableProperties.value = mutableProperties.value.withSmoothing(smoothing)
    }

    fun selectLayer(layerId: String) {
        mutableProperties.value = mutableProperties.value.withLayer(layerId)
    }

    fun getStrokeStyle(brushType: BrushType): StrokeStyle {
        val props = mutableProperties.value
        return when (brushType) {
            BrushType.Pencil -> StrokeStyle.Pencil.copy(
                color = props.primaryColor,
                width = props.strokeWidth.coerceIn(1f, 10f)
            )
            BrushType.Pen -> StrokeStyle.Pen.copy(
                color = props.primaryColor,
                width = props.strokeWidth.coerceIn(1f, 20f)
            )
            BrushType.Highlighter -> StrokeStyle.Highlighter.copy(
                color = props.primaryColor.copy(alpha = 0.5f),
                width = props.strokeWidth.coerceIn(10f, 50f)
            )
            BrushType.Marker -> StrokeStyle.Marker.copy(
                color = props.primaryColor.copy(alpha = 0.7f),
                width = props.strokeWidth.coerceIn(5f, 30f)
            )
            else -> StrokeStyle.Default.copy(
                color = props.primaryColor,
                width = props.strokeWidth
            )
        }
    }
}

/**
 * Base interface for tool handlers.
 * Each tool implements this to handle input events.
 */
interface ToolHandler {
    val tool: Tool
    val properties: StateFlow<ToolProperties>

    fun handleEvent(event: PointerEvent, viewport: Viewport): ToolResult?

    fun onActivate(viewport: Viewport) {}
    fun onDeactivate() {}
}

/**
 * Result of tool processing.
 */
sealed class ToolResult {
    data class ElementCreated(val element: CanvasElement) : ToolResult()
    data class ElementsModified(val elements: List<CanvasElement>) : ToolResult()
    data class ElementsDeleted(val elementIds: List<String>) : ToolResult()
    data object None : ToolResult()
    data class RequestPan(val delta: PointF) : ToolResult()
    data class RequestZoom(val center: PointF, val factor: Float) : ToolResult()
}

/**
 * Handler for drawing tools.
 */
class DrawToolHandler(
    override val tool: Tool.Draw,
    override val properties: StateFlow<ToolProperties>,
    private val viewport: Viewport
) : ToolHandler {

    private var currentStroke: MutableList<PointF> = mutableListOf()
    private var currentPressures: MutableList<Float> = mutableListOf()
    private var currentTimestamps: MutableList<Long> = mutableListOf()
    private var isDrawing = false

    override fun handleEvent(event: PointerEvent, viewport: Viewport): ToolResult? {
        val worldPoint = viewport.screenToWorld(
            PointF(
                when (event) {
                    is PointerEvent.Down -> event.position.x
                    is PointerEvent.Move -> event.position.x
                    is PointerEvent.Up -> event.position.x
                    else -> return null
                },
                when (event) {
                    is PointerEvent.Down -> event.position.y
                    is PointerEvent.Move -> event.position.y
                    is PointerEvent.Up -> event.position.y
                    else -> return null
                }
            )
        )

        return when (event) {
            is PointerEvent.Down -> {
                currentStroke.clear()
                currentPressures.clear()
                currentTimestamps.clear()
                currentStroke.add(worldPoint)
                currentPressures.add(event.pressure)
                currentTimestamps.add(event.timestamp)
                isDrawing = true
                ToolResult.None
            }
            is PointerEvent.Move -> {
                if (isDrawing) {
                    currentStroke.add(worldPoint)
                    currentPressures.add(event.pressure)
                    currentTimestamps.add(event.timestamp)

                    // Emit intermediate result for preview
                    val previewStroke = createStroke(preview = true)
                    ToolResult.ElementCreated(previewStroke)
                } else {
                    null
                }
            }
            is PointerEvent.Up -> {
                if (isDrawing) {
                    isDrawing = false
                    val finalStroke = createStroke(preview = false)
                    currentStroke.clear()
                    currentPressures.clear()
                    currentTimestamps.clear()
                    ToolResult.ElementCreated(finalStroke)
                } else {
                    null
                }
            }
            is PointerEvent.Cancel -> {
                isDrawing = false
                currentStroke.clear()
                currentPressures.clear()
                currentTimestamps.clear()
                ToolResult.None
            }
        }
    }

    private fun createStroke(preview: Boolean): Stroke {
        val props = properties.value
        val style = (tool as? Tool.Draw)?.let {
            (properties.value as? ToolProperties)?.let { p ->
                // Get style from ToolManager
                StrokeStyle.Default
            }
        } ?: StrokeStyle.Default

        return Stroke(
            id = if (preview) "preview" else "",
            points = currentStroke.toList(),
            style = style,
            pressures = currentPressures.toList(),
            timestamps = currentTimestamps.toList(),
            layerId = CanvasElement.DEFAULT_LAYER_ID
        )
    }
}

/**
 * Handler for select/pan tools.
 */
class SelectToolHandler(
    override val tool: Tool,
    override val properties: StateFlow<ToolProperties>,
    private val viewport: Viewport
) : ToolHandler {
    private var dragStart: PointF? = null
    private var isDragging = false

    override fun handleEvent(event: PointerEvent, viewport: Viewport): ToolResult? {
        return when (event) {
            is PointerEvent.Down -> {
                dragStart = PointF(event.position.x, event.position.y)
                isDragging = true
                ToolResult.None
            }
            is PointerEvent.Move -> {
                if (isDragging && dragStart != null) {
                    val current = PointF(event.position.x, event.position.y)
                    val delta = current - dragStart!!
                    dragStart = current
                    when (tool) {
                        is Tool.Pan, is Tool.Hand -> ToolResult.RequestPan(delta)
                        else -> null
                    }
                } else {
                    null
                }
            }
            is PointerEvent.Up -> {
                isDragging = false
                dragStart = null
                ToolResult.None
            }
            is PointerEvent.Cancel -> {
                isDragging = false
                dragStart = null
                ToolResult.None
            }
        }
    }
}
