package `in`.innovaticshub.notepad.ui.canvas.zoom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.ln

/**
 * Camera state for an infinite canvas: pan, zoom, and screen-aware transforms.
 *
 * Uses logarithmic scale internally so pinch zoom feels even at 0.1% and 400%.
 */
@Stable
class ZoomState {
    private var logScale: Float = 0f

    private val _scale = mutableFloatStateOf(1f)
    val scale: Float get() = _scale.floatValue

    private val _offset = mutableStateOf(Offset.Zero)
    val offset: Offset get() = _offset.value

    private val _isTransformMode = mutableStateOf(false)
    val isTransformMode: Boolean get() = _isTransformMode.value

    var viewportWidth: Float = 0f
        private set
    var viewportHeight: Float = 0f
        private set

    val minScale: Float = MIN_SCALE
    val maxScale: Float = MAX_SCALE

    private fun syncScaleFromLog() {
        _scale.floatValue = exp(logScale).coerceIn(minScale, maxScale)
    }

    private fun clampLogScale(log: Float): Float =
        log.coerceIn(ln(minScale), ln(maxScale))

    fun setScale(newScale: Float) {
        logScale = clampLogScale(ln(newScale.coerceIn(minScale, maxScale)))
        syncScaleFromLog()
    }

    fun zoomIn() = zoomBy(ZOOM_BUTTON_FACTOR)

    fun zoomOut() = zoomBy(1f / ZOOM_BUTTON_FACTOR)

    fun zoomBy(factor: Float) {
        val center = viewportCenter()
        zoomCentroid(center, factor)
    }

    fun setOffset(newOffset: Offset) {
        _offset.value = newOffset
    }

    fun panBy(delta: Offset) {
        _offset.value += delta
    }

    /**
     * Pinch / pan gesture from [detectTransformGestures].
     * Zoom is applied in log-space; pan is applied in screen space.
     */
    fun transform(centroid: Offset, zoomFactor: Float, panDelta: Offset) {
        if (zoomFactor == 1f && panDelta == Offset.Zero) return

        val smoothedZoom = 1f + (zoomFactor - 1f) * PINCH_SENSITIVITY
        val oldScale = scale
        val newLog = clampLogScale(logScale + ln(smoothedZoom))
        val newScale = exp(newLog)

        if (newScale != oldScale) {
            val scaleRatio = newScale / oldScale
            _offset.value = centroid - (centroid - _offset.value) * scaleRatio + panDelta
            logScale = newLog
            syncScaleFromLog()
        } else {
            _offset.value += panDelta
        }
    }

    fun zoomCentroid(centroid: Offset, zoomFactor: Float) {
        transform(centroid, zoomFactor, Offset.Zero)
    }

    fun screenToCanvas(screenPoint: Offset): Offset =
        (screenPoint - _offset.value) / scale

    fun canvasToScreen(canvasPoint: Offset): Offset =
        canvasPoint * scale + _offset.value

    fun reset() {
        logScale = 0f
        syncScaleFromLog()
        _offset.value = Offset.Zero
    }

    /** Double-tap toggles between 100% and 2.5× centered on screen. */
    fun toggleDoubleTapZoom() {
        val center = viewportCenter()
        if (scale < 1.5f) {
            zoomCentroid(center, 2.5f)
        } else {
            reset()
        }
    }

    fun toggleMode() {
        _isTransformMode.value = !_isTransformMode.value
    }

    /**
     * Keeps the world point at the viewport center fixed when the screen size changes
     * (rotation, fold, split-screen).
     */
    fun updateViewportSize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        if (viewportWidth > 0f && viewportHeight > 0f) {
            val oldCenter = Offset(viewportWidth / 2f, viewportHeight / 2f)
            val newCenter = Offset(width / 2f, height / 2f)
            val worldAtCenter = screenToCanvas(oldCenter)
            _offset.value = newCenter - worldAtCenter * scale
        }

        viewportWidth = width
        viewportHeight = height
    }

    private fun viewportCenter(): Offset =
        if (viewportWidth > 0f && viewportHeight > 0f) {
            Offset(viewportWidth / 2f, viewportHeight / 2f)
        } else {
            Offset.Zero
        }

    override fun toString(): String =
        "ZoomState(scale=$scale, offset=$offset, isTransformMode=$isTransformMode)"

    companion object {
        const val MIN_SCALE = 0.001f
        const val MAX_SCALE = 4096f
        private const val ZOOM_BUTTON_FACTOR = 1.18f
        private const val PINCH_SENSITIVITY = 1f
    }
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }
