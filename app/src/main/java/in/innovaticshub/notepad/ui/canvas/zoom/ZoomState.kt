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
 * Camera state with finite zoom limits and screen-aware pan/zoom transforms.
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

    var viewportWidth: Float by mutableFloatStateOf(0f)
        private set
    var viewportHeight: Float by mutableFloatStateOf(0f)
        private set

    var minScale: Float = MIN_SCALE; private set
    var maxScale: Float = MAX_SCALE; private set
    fun setLimits(minimum:Float,maximum:Float) {
        if(!minimum.isFinite() || !maximum.isFinite() || minimum<=0f || maximum<minimum) return
        minScale=minimum;maxScale=maximum;setScale(scale)
    }

    private fun syncScaleFromLog() {
        _scale.floatValue = exp(logScale).coerceIn(minScale, maxScale)
    }

    private fun clampLogScale(log: Float): Float =
        log.coerceIn(ln(minScale), ln(maxScale))

    fun setScale(newScale: Float) {
        if(!newScale.isFinite() || newScale<=0f) return
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
        if(!newOffset.x.isFinite() || !newOffset.y.isFinite()) return
        _offset.value = newOffset
    }

    fun panBy(delta: Offset) {
        if(!delta.x.isFinite() || !delta.y.isFinite()) return
        _offset.value += delta
    }

    /** Keep finite documents reachable; centre a document that fits inside the viewport. */
    fun constrainDocument(width:Float,height:Float,padding:Float=24f,bottomSpace:Float=180f) {
        if(viewportWidth<=0f || viewportHeight<=0f) return
        val scaledWidth=width*scale
        val x=if(scaledWidth<=viewportWidth-padding*2) (viewportWidth-scaledWidth)/2
            else offset.x.coerceIn(viewportWidth-scaledWidth-padding,padding)
        val scaledHeight=height*scale+bottomSpace
        val y=if(scaledHeight<=viewportHeight-padding*2) (viewportHeight-scaledHeight)/2
            else offset.y.coerceIn(minOf(padding,viewportHeight-scaledHeight),padding)
        setOffset(Offset(x,y))
    }

    /**
     * Pinch / pan gesture from [detectTransformGestures].
     * Zoom is applied in log-space; pan is applied in screen space.
     */
    fun transform(centroid: Offset, zoomFactor: Float, panDelta: Offset) {
        if(!centroid.x.isFinite() || !centroid.y.isFinite() || !panDelta.x.isFinite() || !panDelta.y.isFinite() || !zoomFactor.isFinite() || zoomFactor<=0f) return
        if (zoomFactor == 1f && panDelta == Offset.Zero) return

        val smoothedZoom = 1f + (zoomFactor - 1f) * PINCH_SENSITIVITY
        val oldScale = scale
        val newLog = clampLogScale(logScale + ln(smoothedZoom))
        val newScale = exp(newLog)

        if (newScale != oldScale) {
            val scaleRatio = newScale / oldScale
            _offset.value = centroid - (centroid - panDelta - _offset.value) * scaleRatio
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
        logScale = clampLogScale(0f)
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
     * Keeps the world point at the top center fixed when the screen width changes
     * (rotation, fold, split-screen).
     */
    fun updateViewportSize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        // Keyboard, tool panels and the writing pad change only the height. Keep
        // the top of the document anchored so its fields do not leave the screen.
        if (viewportWidth > 0f && viewportHeight > 0f && width != viewportWidth) {
            val oldCenter = Offset(viewportWidth / 2f, 0f)
            val newCenter = Offset(width / 2f, 0f)
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
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 8f
        private const val ZOOM_BUTTON_FACTOR = 1.18f
        private const val PINCH_SENSITIVITY = 1f
    }
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }
