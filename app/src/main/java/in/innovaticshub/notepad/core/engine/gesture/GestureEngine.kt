package `in`.innovaticshub.notepad.core.engine.gesture

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.engine.viewport.Viewport
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-performance gesture recognition engine.
 * Handles multi-touch gestures for pan, zoom, and drawing.
 */
class GestureEngine(
    private val viewport: Viewport
) {
    // Gesture state
    private val mutableState: MutableStateFlow<GestureState> = MutableStateFlow(GestureState.Idle)

    val state: StateFlow<GestureState> = mutableState.asStateFlow()

    // Event streams
    private val mutableEvents: MutableSharedFlow<GestureEvent> = MutableSharedFlow(
        extraBufferCapacity = 100
    )
    val events = mutableEvents.asSharedFlow()

    // Tracking for multi-touch
    private var activePointers: Map<Int, PointerData> = emptyMap()
    private var initialPanViewportOffset: PointF = PointF.Zero
    private var lastPanScreenPosition: Offset = Offset.Zero
    private var initialPinchCenter: Offset = Offset.Zero
    private var initialPinchDistance: Float = 0f
    private var initialZoom: Float = 1f

    /**
     * Process a pointer input event.
     */
    fun processEvent(event: PointerEvent) {
        when (event) {
            is PointerEvent.Down -> handlePointerDown(event)
            is PointerEvent.Move -> handlePointerMove(event)
            is PointerEvent.Up -> handlePointerUp(event)
            is PointerEvent.Cancel -> handlePointerCancel(event)
        }
    }

    private fun handlePointerDown(event: PointerEvent.Down) {
        activePointers = activePointers + (event.pointerId to PointerData(
            position = event.position,
            pressure = event.pressure,
            timestamp = event.timestamp,
            tool = event.tool
        ))

        when (activePointers.size) {
            1 -> {
                // Single pointer - could be drawing or pan start
                val data = activePointers.values.first()
                if (data.tool == PointerTool.Stylus || data.tool == PointerTool.Eraser) {
                    updateState(GestureState.Drawing(data.tool))
                    emitEvent(GestureEvent.DrawStart(event.position, event.pressure, event.timestamp))
                } else {
                    // Finger - wait to disambiguate
                    updateState(GestureState.PotentialPan(System.nanoTime()))
                    startPanTimeout(event.position)
                }
            }
            2 -> {
                // Two pointers - pinch zoom
                val pointers = activePointers.values.toList()
                initialPinchCenter = calculatePinchCenter(pointers)
                initialPinchDistance = calculatePinchDistance(pointers)
                initialZoom = viewport.state.value.zoomLevel
                updateState(GestureState.Pinch)
                emitEvent(GestureEvent.PinchStart(initialPinchCenter, initialPinchDistance))
            }
        }
    }

    private fun handlePointerMove(event: PointerEvent.Move) {
        activePointers = activePointers.toMutableMap().apply {
            this[event.pointerId]?.let { existing ->
                this[event.pointerId] = existing.copy(
                    position = event.position,
                    pressure = event.pressure,
                    timestamp = event.timestamp
                )
            }
        }

        when (val currentState = state.value) {
            is GestureState.PotentialPan -> {
                // Check if moved enough to commit to pan
                val initial = activePointers[event.pointerId]?.position ?: return
                val delta = event.position - initial
                if (delta.getDistance() > PAN_GESTURE_THRESHOLD) {
                    updateState(GestureState.Panning(System.nanoTime()))
                    initialPanViewportOffset = viewport.state.value.contentOffset
                    lastPanScreenPosition = event.position
                    emitEvent(GestureEvent.PanStart(initial))
                }
            }
            is GestureState.Panning -> {
                val dx = event.position.x - lastPanScreenPosition.x
                val dy = event.position.y - lastPanScreenPosition.y
                lastPanScreenPosition = event.position
                viewport.pan(dx, dy)
                emitEvent(GestureEvent.Pan(dx, dy))
            }
            is GestureState.Drawing -> {
                emitEvent(GestureEvent.DrawMove(
                    event.position,
                    event.pressure,
                    event.timestamp
                ))
            }
            is GestureState.Pinch -> {
                val pointers = activePointers.values.toList()
                if (pointers.size >= 2) {
                    val newCenter = calculatePinchCenter(pointers)
                    val newDistance = calculatePinchDistance(pointers)

                    // Zoom based on distance change
                    val scaleFactor = newDistance / initialPinchDistance
                    viewport.zoom(PointF(newCenter.x, newCenter.y), scaleFactor)

                    // Also pan based on center movement
                    val centerDelta = newCenter - initialPinchCenter
                    viewport.pan(centerDelta.x, centerDelta.y)

                    initialPinchCenter = newCenter
                    initialPinchDistance = newDistance

                    emitEvent(GestureEvent.Pinch(
                        newCenter,
                        newDistance,
                        viewport.state.value.zoomLevel
                    ))
                }
            }
            else -> {}
        }
    }

    private fun handlePointerUp(event: PointerEvent.Up) {
        when (val currentState = state.value) {
            is GestureState.Drawing -> {
                emitEvent(GestureEvent.DrawEnd)
                updateState(GestureState.Idle)
            }
            is GestureState.Panning -> {
                emitEvent(GestureEvent.PanEnd)
                updateState(GestureState.Idle)
            }
            is GestureState.Pinch -> {
                emitEvent(GestureEvent.PinchEnd)
                updateState(GestureState.Idle)
            }
            is GestureState.PotentialPan -> {
                // Was a tap
                emitEvent(GestureEvent.Tap(event.position))
                updateState(GestureState.Idle)
            }
            else -> {}
        }

        activePointers = activePointers - event.pointerId
    }

    private fun handlePointerCancel(event: PointerEvent.Cancel) {
        activePointers = activePointers - event.pointerId
        updateState(GestureState.Idle)
        emitEvent(GestureEvent.Cancel)
    }

    private fun startPanTimeout(position: Offset) {
        // In a real implementation, use a coroutine timeout
        // to differentiate between tap and drag
    }

    private fun calculatePinchCenter(pointers: List<PointerData>): Offset {
        return Offset(
            x = pointers.map { it.position.x }.average().toFloat(),
            y = pointers.map { it.position.y }.average().toFloat()
        )
    }

    private fun calculatePinchDistance(pointers: List<PointerData>): Float {
        if (pointers.size < 2) return 0f
        val p1 = pointers[0].position
        val p2 = pointers[1].position
        return Offset(
            p1.x - p2.x,
            p1.y - p2.y
        ).getDistance()
    }

    private fun updateState(newState: GestureState) {
        mutableState.value = newState
    }

    private fun emitEvent(event: GestureEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        const val PAN_GESTURE_THRESHOLD = 10f
        const val PINCH_ZOOM_SENSITIVITY = 1f
        const val TAP_TIMEOUT_MS = 300L
    }
}

/**
 * Represents the current gesture state.
 */
sealed class GestureState {
    data object Idle : GestureState()
    data class PotentialPan(val startTime: Long) : GestureState()
    data class Panning(val startTime: Long) : GestureState()
    data class Drawing(val tool: PointerTool) : GestureState()
    data object Pinch : GestureState()
}

/**
 * Gesture events emitted by the engine.
 */
sealed class GestureEvent {
    data class Tap(val position: Offset) : GestureEvent()
    data class DoubleTap(val position: Offset) : GestureEvent()
    data class LongPress(val position: Offset) : GestureEvent()

    data class PanStart(val position: Offset) : GestureEvent()
    data class Pan(val dx: Float, val dy: Float) : GestureEvent()
    data object PanEnd : GestureEvent()

    data class PinchStart(val center: Offset, val distance: Float) : GestureEvent()
    data class Pinch(val center: Offset, val distance: Float, val zoom: Float) : GestureEvent()
    data object PinchEnd : GestureEvent()

    data class DrawStart(val position: Offset, val pressure: Float, val timestamp: Long) : GestureEvent()
    data class DrawMove(val position: Offset, val pressure: Float, val timestamp: Long) : GestureEvent()
    data object DrawEnd : GestureEvent()

    data object Cancel : GestureEvent()
}

/**
 * Raw pointer input events.
 */
sealed class PointerEvent {
    abstract val pointerId: Int
    abstract val timestamp: Long

    data class Down(
        override val pointerId: Int,
        val position: Offset,
        val pressure: Float = 0f,
        val tool: PointerTool = PointerTool.Unknown,
        override val timestamp: Long = System.nanoTime()
    ) : PointerEvent()

    data class Move(
        override val pointerId: Int,
        val position: Offset,
        val pressure: Float = 0f,
        override val timestamp: Long = System.nanoTime()
    ) : PointerEvent()

    data class Up(
        override val pointerId: Int,
        val position: Offset,
        override val timestamp: Long = System.nanoTime()
    ) : PointerEvent()

    data class Cancel(
        override val pointerId: Int,
        override val timestamp: Long = System.nanoTime()
    ) : PointerEvent()
}

/**
 * Pointer input tools.
 */
enum class PointerTool {
    Unknown,
    Finger,
    Stylus,
    Eraser,
    Mouse
}

/**
 * Tracking data for active pointers.
 */
data class PointerData(
    val position: Offset,
    val pressure: Float,
    val timestamp: Long,
    val tool: PointerTool
)

/**
 * Extension for calculating distance between offsets.
 */
private fun Offset.getDistance(): Float {
    return kotlin.math.sqrt(x * x + y * y)
}

private operator fun Offset.minus(other: Offset): Offset {
    return Offset(x - other.x, y - other.y)
}
