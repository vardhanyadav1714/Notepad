package `in`.innovaticshub.notepad.ui.canvas

import `in`.innovaticshub.notepad.core.domain.model.CanvasElement
import `in`.innovaticshub.notepad.core.domain.model.Layer
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import `in`.innovaticshub.notepad.core.domain.model.StrokeStyle
import `in`.innovaticshub.notepad.core.engine.command.AddElementCommand
import `in`.innovaticshub.notepad.core.engine.command.CanvasState
import `in`.innovaticshub.notepad.core.engine.command.CommandHistory
import `in`.innovaticshub.notepad.core.engine.gesture.GestureEngine
import `in`.innovaticshub.notepad.core.engine.gesture.GestureEvent
import `in`.innovaticshub.notepad.core.engine.gesture.PointerEvent
import `in`.innovaticshub.notepad.core.engine.renderer.RenderingPipeline
import `in`.innovaticshub.notepad.core.engine.smoothing.RealtimeStrokeBuilder
import `in`.innovaticshub.notepad.core.engine.smoothing.VelocitySmoother
import `in`.innovaticshub.notepad.core.engine.tool.BrushType
import `in`.innovaticshub.notepad.core.engine.tool.Tool
import `in`.innovaticshub.notepad.core.engine.tool.Tool.Draw
import `in`.innovaticshub.notepad.core.engine.tool.ToolManager
import `in`.innovaticshub.notepad.core.engine.tool.ToolProperties
import `in`.innovaticshub.notepad.core.engine.viewport.Viewport
import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main view model for the infinite canvas.
 */
class InfiniteCanvasViewModel : ViewModel() {

    // ==================================================
    // Core Systems
    // ==================================================

    private val viewport: Viewport = Viewport(
        initialWidth = 1080f,
        initialHeight = 2400f
    )

    private val toolManager: ToolManager = ToolManager()
    private val commandHistory: CommandHistory = CommandHistory()
    private val gestureEngine: GestureEngine = GestureEngine(viewport)
    private val renderingPipeline: RenderingPipeline = RenderingPipeline(viewport)
    private val strokeBuilder: RealtimeStrokeBuilder = RealtimeStrokeBuilder(
        smoother = VelocitySmoother()
    )

    // ==================================================
    // Canvas State
    // ==================================================

    private val mutableCanvasState: MutableStateFlow<CanvasState> = MutableStateFlow(
        CanvasState(
            elements = emptyList(),
            selectedIds = emptySet(),
            activeLayerId = CanvasElement.DEFAULT_LAYER_ID,
            version = 0
        )
    )
    val canvasState: StateFlow<CanvasState> = mutableCanvasState.asStateFlow()

    // ==================================================
    // Layer State
    // ==================================================

    private val mutableLayers: MutableStateFlow<List<Layer>> = MutableStateFlow(
        listOf(Layer.Default)
    )
    val layers: StateFlow<List<Layer>> = mutableLayers.asStateFlow()

    // ==================================================
    // UI State
    // ==================================================

    private val mutableUiState: MutableStateFlow<CanvasUiState> = MutableStateFlow(
        CanvasUiState()
    )
    val uiState: StateFlow<CanvasUiState> = mutableUiState.asStateFlow()

    // ==================================================
    // Preview State
    // ==================================================

    private val mutablePreviewStroke: MutableStateFlow<Stroke?> = MutableStateFlow(null)
    val previewStroke: StateFlow<Stroke?> = mutablePreviewStroke.asStateFlow()

    // ==================================================
    // Initialization
    // ==================================================

    init {
        viewModelScope.launch {
            gestureEngine.events.collect { event ->
                handleGestureEvent(event)
            }
        }

        viewModelScope.launch {
            commandHistory.events.collect {
                mutableUiState.value = mutableUiState.value.copy(
                    canUndo = commandHistory.canUndo.value,
                    canRedo = commandHistory.canRedo.value
                )
            }
        }

        viewModelScope.launch {
            canvasState.collect { state ->
                renderingPipeline.updateElements(state.elements)
            }
        }
    }

    // ==================================================
    // Public API - Viewport
    // ==================================================

    fun updateScreenSize(width: Float, height: Float) {
        viewport.updateScreenSize(width, height)
        renderingPipeline.invalidateAll()
    }

    fun pan(deltaX: Float, deltaY: Float) {
        viewport.pan(deltaX, deltaY)
        renderingPipeline.invalidateAll()
    }

    fun zoom(screenPoint: PointF, factor: Float) {
        viewport.zoom(screenPoint, factor)
        renderingPipeline.invalidateAll()
    }

    fun setZoom(zoom: Float) {
        viewport.setZoom(zoom)
        renderingPipeline.invalidateAll()
    }

    fun centerOn(point: PointF) {
        viewport.centerOn(point)
        renderingPipeline.invalidateAll()
    }

    fun fitRect(bounds: `in`.innovaticshub.notepad.core.domain.geometry.RectF) {
        viewport.fitRect(bounds)
        renderingPipeline.invalidateAll()
    }

    fun resetViewport() {
        viewport.reset()
        renderingPipeline.invalidateAll()
    }

    // ==================================================
    // Public API - Tools
    // ==================================================

    fun selectTool(tool: Tool) {
        toolManager.selectTool(tool)
        mutableUiState.value = mutableUiState.value.copy(currentTool = tool)
    }

    fun setColor(color: Color) {
        toolManager.setColor(color)
    }

    fun setStrokeWidth(width: Float) {
        toolManager.setStrokeWidth(width)
    }

    fun setOpacity(opacity: Float) {
        toolManager.setOpacity(opacity)
    }

    // ==================================================
    // Public API - Input
    // ==================================================

    fun handlePointerDown(
        pointerId: Int,
        position: androidx.compose.ui.geometry.Offset,
        pressure: Float = 0f,
        tool: `in`.innovaticshub.notepad.core.engine.gesture.PointerTool = `in`.innovaticshub.notepad.core.engine.gesture.PointerTool.Unknown
    ) {
        val event = PointerEvent.Down(
            pointerId = pointerId,
            position = position,
            pressure = pressure,
            tool = tool
        )
        gestureEngine.processEvent(event)
    }

    fun handlePointerMove(
        pointerId: Int,
        position: androidx.compose.ui.geometry.Offset,
        pressure: Float = 0f
    ) {
        val event = PointerEvent.Move(
            pointerId = pointerId,
            position = position,
            pressure = pressure
        )
        gestureEngine.processEvent(event)
    }

    fun handlePointerUp(
        pointerId: Int,
        position: androidx.compose.ui.geometry.Offset
    ) {
        val event = PointerEvent.Up(
            pointerId = pointerId,
            position = position
        )
        gestureEngine.processEvent(event)
    }

    fun handlePointerCancel(pointerId: Int) {
        val event = PointerEvent.Cancel(pointerId = pointerId)
        gestureEngine.processEvent(event)
    }

    // ==================================================
    // Public API - Undo/Redo
    // ==================================================

    fun undo() {
        val newState = commandHistory.undo(canvasState.value)
        mutableCanvasState.value = newState
        renderingPipeline.invalidateAll()
    }

    fun redo() {
        val newState = commandHistory.redo(canvasState.value)
        mutableCanvasState.value = newState
        renderingPipeline.invalidateAll()
    }

    // ==================================================
    // Public API - Layers
    // ==================================================

    fun createLayer(name: String): Layer {
        val newLayer = Layer(
            name = name,
            zIndex = layers.value.size
        )
        mutableLayers.value = mutableLayers.value + newLayer
        return newLayer
    }

    fun toggleLayerVisibility(id: String) {
        mutableLayers.value = layers.value.map { layer ->
            if (layer.id == id) {
                layer.copy(isVisible = !layer.isVisible)
            } else {
                layer
            }
        }
        renderingPipeline.invalidateAll()
    }

    fun toggleLayerLock(id: String) {
        mutableLayers.value = layers.value.map { layer ->
            if (layer.id == id) {
                layer.copy(isLocked = !layer.isLocked)
            } else {
                layer
            }
        }
    }

    fun selectLayer(id: String) {
        mutableCanvasState.value = canvasState.value.copy(activeLayerId = id)
        toolManager.selectLayer(id)
    }

    // ==================================================
    // Public API - Selection
    // ==================================================

    fun selectElement(id: String) {
        mutableCanvasState.value = canvasState.value.copy(
            selectedIds = canvasState.value.selectedIds + id
        )
    }

    fun deselectElement(id: String) {
        mutableCanvasState.value = canvasState.value.copy(
            selectedIds = canvasState.value.selectedIds - id
        )
    }

    fun clearSelection() {
        mutableCanvasState.value = canvasState.value.copy(
            selectedIds = emptySet()
        )
    }

    fun deleteSelected() {
        val selectedIds = canvasState.value.selectedIds
        if (selectedIds.isEmpty()) return

        selectedIds.forEach { id ->
            val element = canvasState.value.getElementById(id) ?: return@forEach
            val command = `in`.innovaticshub.notepad.core.engine.command.RemoveElementCommand(
                id = "remove_$id",
                elementId = id,
                element = element
            )
            mutableCanvasState.value = commandHistory.execute(command, canvasState.value)
        }

        renderingPipeline.invalidateAll()
    }

    // ==================================================
    // Gesture Event Handling
    // ==================================================

    private fun handleGestureEvent(event: GestureEvent) {
        when (event) {
            is GestureEvent.DrawStart -> handleDrawStart(event)
            is GestureEvent.DrawMove -> handleDrawMove(event)
            is GestureEvent.DrawEnd -> handleDrawEnd()
            is GestureEvent.Pan -> pan(-event.dx, -event.dy)
            is GestureEvent.Tap -> handleTap(event.position)
            else -> {}
        }
    }

    private fun handleDrawStart(event: GestureEvent.DrawStart) {
        val worldPoint = viewport.screenToWorld(
            PointF(event.position.x, event.position.y)
        )
        strokeBuilder.addPoint(worldPoint, event.pressure)
    }

    private fun handleDrawMove(event: GestureEvent.DrawMove) {
        val worldPoint = viewport.screenToWorld(
            PointF(event.position.x, event.position.y)
        )
        strokeBuilder.addPoint(worldPoint, event.pressure)

        val smoothed = strokeBuilder.getSmoothedStroke()
        if (smoothed.points.size >= 2) {
            val currentTool = toolManager.properties.value.currentTool
            val brushType = (currentTool as? Draw)?.brushType ?: BrushType.Pen
            val style = toolManager.getStrokeStyle(brushType)

            mutablePreviewStroke.value = Stroke(
                id = "preview",
                points = smoothed.points,
                pressures = smoothed.pressures,
                style = style,
                layerId = canvasState.value.activeLayerId
            )
        }
    }

    private fun handleDrawEnd() {
        val smoothed = strokeBuilder.finish()
        if (smoothed.points.size >= 2) {
            val currentTool = toolManager.properties.value.currentTool
            val brushType = (currentTool as? Draw)?.brushType ?: BrushType.Pen
            val style = toolManager.getStrokeStyle(brushType)

            val stroke = Stroke(
                points = smoothed.points,
                pressures = smoothed.pressures,
                style = style,
                layerId = canvasState.value.activeLayerId
            )

            val command = AddElementCommand(
                id = "add_stroke_${System.nanoTime()}",
                element = stroke
            )

            mutableCanvasState.value = commandHistory.execute(command, canvasState.value)
            renderingPipeline.invalidateAll()
        }

        mutablePreviewStroke.value = null
        strokeBuilder.clear()
    }

    private fun handleTap(position: androidx.compose.ui.geometry.Offset) {
        val worldPoint = viewport.screenToWorld(PointF(position.x, position.y))
        val hitElements = renderingPipeline.hitTest(position)

        if (hitElements.isNotEmpty()) {
            val topElement = hitElements.first()
            selectElement(topElement.id)
        } else {
            clearSelection()
        }
    }

    // ==================================================
    // Getters for Compose
    // ==================================================

    fun getViewport(): Viewport = viewport

    fun getToolProperties(): StateFlow<ToolProperties> = toolManager.properties

    fun getRenderState() = renderingPipeline.renderState
}

/**
 * UI state for the canvas.
 */
data class CanvasUiState(
    val currentTool: Tool = Tool.Draw(BrushType.Pen),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showGrid: Boolean = true,
    val showMinimap: Boolean = false,
    val isPlaying: Boolean = false
)
