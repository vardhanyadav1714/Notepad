package `in`.innovaticshub.notepad.core.engine.viewport

import `in`.innovaticshub.notepad.core.domain.geometry.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Viewport manages the camera transform for an infinite canvas.
 * Handles pan, zoom, and coordinate transformations.
 *
 * Coordinate Systems:
 * - World Space: Infinite canvas coordinates (arbitrary Float values)
 * - Screen Space: Device pixel coordinates (0 to screenWidth)
 * - Content Offset: World space origin on screen
 *
 * Thread-safe. All mutations happen through atomic state updates.
 */
class Viewport(
    initialWidth: Float,
    initialHeight: Float,
    minZoom: Float = MIN_ZOOM,
    maxZoom: Float = MAX_ZOOM
) {
    private val mutableState: MutableStateFlow<ViewportState> = MutableStateFlow(
        ViewportState(
            contentOffset = PointF.Zero,
            zoomLevel = 1f,
            screenWidth = initialWidth,
            screenHeight = initialHeight,
            minZoom = minZoom,
            maxZoom = maxZoom
        )
    )

    val state: StateFlow<ViewportState> = mutableState.asStateFlow()

    // Current transform (cached for performance)
    private var cachedTransform: AffineTransform? = null
    private var cachedInverseTransform: AffineTransform? = null

    init {
        updateTransforms()
    }

    /**
     * Update screen dimensions (called on resize).
     */
    fun updateScreenSize(width: Float, height: Float) {
        updateState { state ->
            if (width <= 0f || height <= 0f) return@updateState state

            val newState = if (state.screenWidth > 0f && state.screenHeight > 0f) {
                val oldCenter = PointF(state.screenWidth / 2f, state.screenHeight / 2f)
                val newCenter = PointF(width / 2f, height / 2f)
                val worldAtCenter = screenToWorld(oldCenter, state)
                val newOffset = newCenter - (worldAtCenter * state.zoomLevel)
                state.copy(
                    screenWidth = width,
                    screenHeight = height,
                    contentOffset = newOffset
                )
            } else {
                state.copy(screenWidth = width, screenHeight = height)
            }
            newState
        }
    }

    /**
     * Pan the viewport by the given delta in screen pixels.
     */
    fun pan(deltaX: Float, deltaY: Float) {
        updateState { state ->
            state.copy(
                contentOffset = state.contentOffset + PointF(deltaX, deltaY)
            )
        }
    }

    /**
     * Pan to a specific content offset.
     */
    fun panTo(offset: PointF) {
        updateState { it.copy(contentOffset = offset) }
    }

    /**
     * Center the viewport on a world point.
     */
    fun centerOn(worldPoint: PointF) {
        updateState { state ->
            val screenCenter = PointF(state.screenWidth / 2, state.screenHeight / 2)
            val newOffset = screenCenter - (worldPoint * state.zoomLevel)
            state.copy(contentOffset = newOffset)
        }
    }

    /**
     * Zoom in/out, centered on a screen point.
     * @param screenPoint The point to zoom toward (in screen coordinates)
     * @param zoomFactor Multiplier (e.g., 1.1 for zoom in, 0.9 for zoom out)
     */
    fun zoom(screenPoint: PointF, zoomFactor: Float) {
        updateState { state ->
            val newZoom = (state.zoomLevel * zoomFactor)
                .coerceIn(state.minZoom, state.maxZoom)

            // Calculate offset to keep screenPoint at same world position
            val worldBefore = screenToWorld(screenPoint, state)
            val offsetAfterZoom = screenPoint - (worldBefore * newZoom)

            state.copy(
                contentOffset = offsetAfterZoom,
                zoomLevel = newZoom
            )
        }
    }

    /**
     * Set zoom level directly, centered on screen.
     */
    fun setZoom(zoom: Float) {
        updateState { state ->
            val newZoom = zoom.coerceIn(state.minZoom, state.maxZoom)
            val center = screenToWorld(
                PointF(state.screenWidth / 2, state.screenHeight / 2),
                state
            )
            val screenCenter = PointF(state.screenWidth / 2, state.screenHeight / 2)
            val newOffset = screenCenter - (center * newZoom)
            state.copy(
                contentOffset = newOffset,
                zoomLevel = newZoom
            )
        }
    }

    /**
     * Zoom to fit a rectangle with optional padding.
     */
    fun fitRect(rect: RectF, padding: Float = 50f) {
        updateState { state ->
            val rectWidth = rect.width
            val rectHeight = rect.height
            val availableWidth = state.screenWidth - padding * 2
            val availableHeight = state.screenHeight - padding * 2

            val zoomX = availableWidth / rectWidth
            val zoomY = availableHeight / rectHeight
            val newZoom = minOf(zoomX, zoomY).coerceIn(state.minZoom, state.maxZoom)

            val rectCenter = rect.center
            val screenCenter = PointF(state.screenWidth / 2, state.screenHeight / 2)
            val newOffset = screenCenter - (rectCenter * newZoom)

            state.copy(
                contentOffset = newOffset,
                zoomLevel = newZoom
            )
        }
    }

    /**
     * Reset viewport to initial state.
     */
    fun reset() {
        updateState { state ->
            state.copy(
                contentOffset = PointF.Zero,
                zoomLevel = 1f
            )
        }
    }

    /**
     * Transform screen point to world coordinates.
     */
    fun screenToWorld(screenPoint: PointF): PointF {
        return screenToWorld(screenPoint, state.value)
    }

    /**
     * Transform world point to screen coordinates.
     */
    fun worldToScreen(worldPoint: PointF): PointF {
        return worldToScreen(worldPoint, state.value)
    }

    /**
     * Get the current world-space viewport rectangle.
     */
    fun getWorldBounds(): RectF {
        val state = state.value
        val topLeft = screenToWorld(PointF(0f, 0f), state)
        val bottomRight = screenToWorld(
            PointF(state.screenWidth, state.screenHeight),
            state
        )
        return RectF(
            left = topLeft.x,
            top = topLeft.y,
            right = bottomRight.x,
            bottom = bottomRight.y
        )
    }

    /**
     * Check if a world-space rectangle is visible.
     */
    fun isVisible(worldRect: RectF): Boolean {
        return getWorldBounds().intersects(worldRect)
    }

    /**
     * Get the current transform matrix.
     */
    fun getTransform(): AffineTransform {
        return cachedTransform ?: computeTransform().also { cachedTransform = it }
    }

    /**
     * Get the inverse transform matrix.
     */
    fun getInverseTransform(): AffineTransform {
        return cachedInverseTransform ?: computeInverseTransform().also {
            cachedInverseTransform = it
        }
    }

    private inline fun updateState(update: (ViewportState) -> ViewportState) {
        mutableState.value = update(mutableState.value)
        updateTransforms()
    }

    private fun updateTransforms() {
        cachedTransform = computeTransform()
        cachedInverseTransform = computeInverseTransform()
    }

    private fun computeTransform(): AffineTransform {
        val state = state.value
        return AffineTransform(
            scaleX = state.zoomLevel,
            scaleY = state.zoomLevel,
            transX = state.contentOffset.x,
            transY = state.contentOffset.y
        )
    }

    private fun computeInverseTransform(): AffineTransform {
        val state = state.value
        val invZoom = 1f / state.zoomLevel
        return AffineTransform(
            scaleX = invZoom,
            scaleY = invZoom,
            transX = -state.contentOffset.x * invZoom,
            transY = -state.contentOffset.y * invZoom
        )
    }

    companion object {
        fun screenToWorld(screenPoint: PointF, state: ViewportState): PointF {
            return PointF(
                x = (screenPoint.x - state.contentOffset.x) / state.zoomLevel,
                y = (screenPoint.y - state.contentOffset.y) / state.zoomLevel
            )
        }

        fun worldToScreen(worldPoint: PointF, state: ViewportState): PointF {
            return PointF(
                x = worldPoint.x * state.zoomLevel + state.contentOffset.x,
                y = worldPoint.y * state.zoomLevel + state.contentOffset.y
            )
        }

        const val MIN_ZOOM = 0.001f
        const val MAX_ZOOM = 4096f
    }
}

/**
 * Immutable state representing the viewport configuration.
 */
data class ViewportState(
    val contentOffset: PointF,
    val zoomLevel: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    val minZoom: Float = Viewport.MIN_ZOOM,
    val maxZoom: Float = Viewport.MAX_ZOOM
) {
    val aspectRatio: Float
        get() = if (screenHeight > 0) screenWidth / screenHeight else 1f

    fun toAffineTransform(): AffineTransform = AffineTransform(
        scaleX = zoomLevel,
        scaleY = zoomLevel,
        transX = contentOffset.x,
        transY = contentOffset.y
    )
}

/**
 * Viewport constraints for different zoom levels.
 */
sealed class ZoomLevel(val value: Float) {
    data object Fit : ZoomLevel(0f)
    data object Actual : ZoomLevel(1f)
    data object Quarter : ZoomLevel(0.25f)
    data object Half : ZoomLevel(0.5f)
    data object Double : ZoomLevel(2f)
    data object Four : ZoomLevel(4f)
    data class Custom(val zoom: Float) : ZoomLevel(zoom)
}
