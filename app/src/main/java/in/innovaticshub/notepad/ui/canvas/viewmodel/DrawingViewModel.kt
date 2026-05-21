package `in`.innovaticshub.notepad.ui.canvas.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationRepository
import `in`.innovaticshub.notepad.ui.canvas.collab.CollaborationSubscription
import `in`.innovaticshub.notepad.ui.canvas.collab.toLocalStroke
import `in`.innovaticshub.notepad.ui.canvas.collab.toRemoteStroke
import `in`.innovaticshub.notepad.ui.canvas.engine.CanvasHistory
import `in`.innovaticshub.notepad.ui.canvas.engine.StrokeEraser
import `in`.innovaticshub.notepad.ui.canvas.model.RectF
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.StrokeBuilder
import `in`.innovaticshub.notepad.ui.canvas.model.StrokePoint
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig.Companion.defaultForType
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrawingUiState(
    val strokes: List<Stroke> = emptyList(),
    val currentTool: ToolConfig = ToolConfig.MEDIUM_PEN,
    val isDrawing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canvasBackgroundColor: Color = Color(0xFFFAFAFA),
    val showGrid: Boolean = true,
    val recentColors: List<Color> = defaultRecentColors(),
    val showToolSettings: Boolean = false,
    val selectedStrokeIds: Set<String> = emptySet(),
    val collaborationRoomCode: String? = null,
    val collaborationStatus: String? = null,
    val isCollaborationBusy: Boolean = false
) {
    companion object {
        fun defaultRecentColors() = listOf(
            Color(0xFF1C1C1E),
            Color(0xFF007AFF),
            Color(0xFFFF3B30),
            Color(0xFF34C759),
            Color(0xFFFF9500)
        )
    }
}

class DrawingViewModel : ViewModel() {

    val zoomState = ZoomState()
    private val history = CanvasHistory()
    private val collaborationRepository = CollaborationRepository()

    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    private var currentStrokeBuilder: StrokeBuilder? = null
    private var currentStroke: Stroke? = null
    private var strokeListener: CollaborationSubscription? = null
    private var selectionTransformActive = false
    private val appliedRemoteStrokeIds = mutableSetOf<String>()

    fun startStroke(
        x: Float,
        y: Float,
        pressure: Float = 1f,
        minPointDistance: Float = 0.35f
    ) {
        currentStrokeBuilder = StrokeBuilder(_uiState.value.currentTool, minPointDistance)
        currentStrokeBuilder?.addPoint(StrokePoint(x, y, pressure))
        _uiState.update { it.copy(isDrawing = true) }
    }

    fun addStrokePoint(x: Float, y: Float, pressure: Float = 1f): Stroke? {
        currentStrokeBuilder?.addPoint(StrokePoint(x, y, pressure))
        currentStroke = currentStrokeBuilder?.buildPreview()
        return currentStroke
    }

    fun endStroke() {
        val stroke = currentStroke ?: return
        if (stroke.isEmpty) {
            cancelStroke()
            return
        }

        val before = _uiState.value.strokes

        if (stroke.toolConfig.type == ToolType.LASSO) {
            selectStrokesWithLasso(stroke)
            currentStrokeBuilder = null
            currentStroke = null
            _uiState.update { it.copy(isDrawing = false) }
            return
        }

        history.pushBeforeChange(before)

        val after = if (stroke.toolConfig.isEraser) {
            StrokeEraser.erase(before, stroke)
        } else {
            before + stroke
        }

        applyStrokes(after)
        uploadCollaborativeStroke(stroke)
        currentStrokeBuilder = null
        currentStroke = null
        _uiState.update { it.copy(isDrawing = false) }
    }

    fun cancelStroke() {
        currentStrokeBuilder = null
        currentStroke = null
        _uiState.update { it.copy(isDrawing = false) }
    }

    fun undo() {
        val restored = history.undo(_uiState.value.strokes) ?: return
        applyStrokes(restored)
        clearSelection()
    }

    fun redo() {
        val restored = history.redo(_uiState.value.strokes) ?: return
        applyStrokes(restored)
        clearSelection()
    }

    fun clearCanvas() {
        val state = _uiState.value
        if (state.selectedStrokeIds.isNotEmpty()) {
            deleteSelectedStrokes()
            return
        }
        if (state.strokes.isEmpty()) return
        history.pushBeforeChange(state.strokes)
        applyStrokes(emptyList())
        clearSelection()
    }

    fun setTool(type: ToolType) {
        val config = defaultForType(type).copy(
            color = _uiState.value.currentTool.color,
            baseWidth = defaultForType(type).baseWidth,
            opacity = defaultForType(type).opacity
        )
        _uiState.update {
            it.copy(
                currentTool = config,
                showToolSettings = type != ToolType.LASSO && type != ToolType.SHAPE && type != ToolType.TEXT,
                selectedStrokeIds = if (type == ToolType.LASSO) it.selectedStrokeIds else emptySet()
            )
        }
    }

    fun setToolConfig(toolConfig: ToolConfig) {
        _uiState.update { it.copy(currentTool = toolConfig) }
    }

    fun setColor(color: Color) {
        addRecentColor(color)
        _uiState.update { current ->
            current.copy(currentTool = current.currentTool.copy(color = color))
        }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { current ->
            current.copy(currentTool = current.currentTool.copy(baseWidth = width))
        }
    }

    fun setOpacity(opacity: Float) {
        _uiState.update { current ->
            current.copy(
                currentTool = current.currentTool.copy(opacity = opacity.coerceIn(0.05f, 1f))
            )
        }
    }

    fun toggleToolSettings() {
        _uiState.update { it.copy(showToolSettings = !it.showToolSettings) }
    }

    fun dismissToolSettings() {
        _uiState.update { it.copy(showToolSettings = false) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleDarkCanvas() {
        _uiState.update { current ->
            val newBg = if (current.canvasBackgroundColor == Color(0xFFFAFAFA)) {
                Color(0xFF1C1C1E)
            } else {
                Color(0xFFFAFAFA)
            }
            current.copy(canvasBackgroundColor = newBg)
        }
    }

    fun beginSelectionTransform() {
        val state = _uiState.value
        if (selectionTransformActive || state.selectedStrokeIds.isEmpty()) return
        history.pushBeforeChange(state.strokes)
        selectionTransformActive = true
    }

    fun endSelectionTransform() {
        selectionTransformActive = false
        _uiState.update {
            it.copy(
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun moveSelectedStrokesBy(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        transformSelectedStrokes { point ->
            point.copy(x = point.x + dx, y = point.y + dy)
        }
    }

    fun scaleSelectedStrokesBy(scale: Float, pivotX: Float, pivotY: Float) {
        val safeScale = scale.coerceIn(0.08f, 12f)
        if (safeScale == 1f) return
        transformSelectedStrokes { point ->
            point.copy(
                x = pivotX + (point.x - pivotX) * safeScale,
                y = pivotY + (point.y - pivotY) * safeScale
            )
        }
    }

    fun nudgeSelection(dx: Float, dy: Float) {
        if (_uiState.value.selectedStrokeIds.isEmpty()) return
        beginSelectionTransform()
        moveSelectedStrokesBy(dx, dy)
        endSelectionTransform()
    }

    fun scaleSelectionFromCenter(scale: Float) {
        val bounds = selectedBounds() ?: return
        beginSelectionTransform()
        scaleSelectedStrokesBy(
            scale = scale,
            pivotX = (bounds.left + bounds.right) / 2f,
            pivotY = (bounds.top + bounds.bottom) / 2f
        )
        endSelectionTransform()
    }

    fun createCollaborationRoom() {
        if (_uiState.value.isCollaborationBusy) return
        viewModelScope.launch {
            setCollaborationBusy("Creating room...")
            runCatching {
                val roomCode = collaborationRepository.createRoom()
                startListeningToRoom(roomCode)
                roomCode
            }.onSuccess { roomCode ->
                _uiState.update {
                    it.copy(
                        collaborationRoomCode = roomCode,
                        collaborationStatus = "Room $roomCode is ready to share.",
                        isCollaborationBusy = false
                    )
                }
                uploadExistingStrokes(roomCode)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        collaborationStatus = error.message ?: "Could not create room.",
                        isCollaborationBusy = false
                    )
                }
            }
        }
    }

    fun joinCollaborationRoom(roomCode: String) {
        if (_uiState.value.isCollaborationBusy) return
        viewModelScope.launch {
            setCollaborationBusy("Joining room...")
            runCatching {
                val joinedRoomCode = collaborationRepository.joinRoom(roomCode)
                startListeningToRoom(joinedRoomCode)
                joinedRoomCode
            }.onSuccess { joinedRoomCode ->
                _uiState.update {
                    it.copy(
                        collaborationRoomCode = joinedRoomCode,
                        collaborationStatus = "Joined room $joinedRoomCode.",
                        isCollaborationBusy = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        collaborationStatus = error.message ?: "Could not join room.",
                        isCollaborationBusy = false
                    )
                }
            }
        }
    }

    fun leaveCollaborationRoom() {
        strokeListener?.remove()
        strokeListener = null
        appliedRemoteStrokeIds.clear()
        _uiState.update {
            it.copy(
                collaborationRoomCode = null,
                collaborationStatus = "Left collaboration room.",
                isCollaborationBusy = false
            )
        }
    }

    private fun applyStrokes(strokes: List<Stroke>) {
        val ids = strokes.mapTo(mutableSetOf()) { it.id }
        _uiState.update {
            it.copy(
                strokes = strokes,
                selectedStrokeIds = it.selectedStrokeIds.filterTo(mutableSetOf()) { id -> id in ids },
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    private fun transformSelectedStrokes(transform: (StrokePoint) -> StrokePoint) {
        val selectedIds = _uiState.value.selectedStrokeIds
        if (selectedIds.isEmpty()) return

        val transformed = _uiState.value.strokes.map { stroke ->
            if (stroke.id !in selectedIds) {
                stroke
            } else {
                rebuildStroke(stroke, stroke.points.map(transform))
            }
        }
        applyStrokes(transformed)
    }

    private fun rebuildStroke(stroke: Stroke, points: List<StrokePoint>): Stroke {
        val builder = StrokeBuilder(stroke.toolConfig, minPointDistance = 0f)
        points.forEach(builder::addPoint)
        return builder.build().copy(id = stroke.id)
    }

    private fun deleteSelectedStrokes() {
        val state = _uiState.value
        if (state.selectedStrokeIds.isEmpty()) return
        history.pushBeforeChange(state.strokes)
        applyStrokes(state.strokes.filterNot { it.id in state.selectedStrokeIds })
        clearSelection()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStrokeIds = emptySet()) }
    }

    private fun selectedBounds(): RectF? {
        val selected = _uiState.value.strokes.filter { it.id in _uiState.value.selectedStrokeIds }
        if (selected.isEmpty()) return null
        return RectF(
            left = selected.minOf { it.bounds.left },
            top = selected.minOf { it.bounds.top },
            right = selected.maxOf { it.bounds.right },
            bottom = selected.maxOf { it.bounds.bottom }
        )
    }

    private fun selectStrokesWithLasso(lassoStroke: Stroke) {
        val polygon = lassoStroke.points
        if (polygon.size < 3) {
            clearSelection()
            return
        }

        val selected = _uiState.value.strokes
            .filterNot { it.toolConfig.isEraser || it.toolConfig.type == ToolType.LASSO }
            .filter { stroke ->
                stroke.points.any { point -> pointInPolygon(point, polygon) } ||
                    pointInPolygon(
                        StrokePoint(
                            x = (stroke.bounds.left + stroke.bounds.right) / 2f,
                            y = (stroke.bounds.top + stroke.bounds.bottom) / 2f
                        ),
                        polygon
                    )
            }
            .mapTo(mutableSetOf()) { it.id }

        _uiState.update { it.copy(selectedStrokeIds = selected) }
    }

    private fun pointInPolygon(point: StrokePoint, polygon: List<StrokePoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val xAtY = (previous.x - current.x) * (point.y - current.y) /
                    ((previous.y - current.y).takeIf { it != 0f } ?: 0.0001f) + current.x
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun setCollaborationBusy(status: String) {
        _uiState.update {
            it.copy(
                collaborationStatus = status,
                isCollaborationBusy = true
            )
        }
    }

    private fun startListeningToRoom(roomCode: String) {
        strokeListener?.remove()
        strokeListener = collaborationRepository.listenToStrokes(
            roomCode = roomCode,
            onStrokes = { remoteStrokes ->
                var addedCount = 0
                remoteStrokes.forEach { remoteStroke ->
                    if (remoteStroke.id.isBlank() || !appliedRemoteStrokeIds.add(remoteStroke.id)) {
                        return@forEach
                    }

                    val localStroke = remoteStroke.toLocalStroke()
                    val current = _uiState.value.strokes
                    val updated = if (localStroke.toolConfig.isEraser) {
                        StrokeEraser.erase(current, localStroke)
                    } else {
                        current + localStroke
                    }
                    applyStrokes(updated)
                    addedCount++
                }
                if (addedCount > 0) {
                    _uiState.update {
                        it.copy(collaborationStatus = "Synced $addedCount new stroke${if (addedCount == 1) "" else "s"}.")
                    }
                }
            },
            onConnected = {
                _uiState.update {
                    it.copy(
                        collaborationStatus = "Connected to room $roomCode.",
                        isCollaborationBusy = false
                    )
                }
            },
            onError = { error ->
                _uiState.update {
                    it.copy(collaborationStatus = error.message ?: "Collaboration sync failed.")
                }
            }
        )
    }

    private fun uploadCollaborativeStroke(stroke: Stroke) {
        val roomCode = _uiState.value.collaborationRoomCode ?: return
        val userId = collaborationRepository.currentUserId
        val remoteStroke = stroke.toRemoteStroke(userId)
        appliedRemoteStrokeIds.add(remoteStroke.id)
        viewModelScope.launch {
            runCatching {
                collaborationRepository.uploadStroke(roomCode, remoteStroke)
                remoteStroke
            }.onSuccess {
                _uiState.update {
                    it.copy(collaborationStatus = "Stroke synced to room $roomCode.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(collaborationStatus = error.message ?: "Could not sync stroke.")
                }
            }
        }
    }

    private fun uploadExistingStrokes(roomCode: String) {
        val userId = collaborationRepository.currentUserId
        _uiState.value.strokes.forEach { stroke ->
            val remoteStroke = stroke.toRemoteStroke(userId)
            appliedRemoteStrokeIds.add(remoteStroke.id)
            viewModelScope.launch {
                runCatching {
                    collaborationRepository.uploadStroke(roomCode, remoteStroke)
                }
            }
        }
    }

    private fun addRecentColor(color: Color) {
        _uiState.update { state ->
            val updated = (listOf(color) + state.recentColors.filter { it != color }).take(8)
            state.copy(recentColors = updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        strokeListener?.remove()
        history.clear()
    }
}
