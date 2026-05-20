package `in`.innovaticshub.notepad.core.engine.renderer

import `in`.innovaticshub.notepad.core.domain.geometry.RectF
import `in`.innovaticshub.notepad.core.domain.model.CanvasElement
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import `in`.innovaticshub.notepad.core.engine.spatial.QuadTree
import `in`.innovaticshub.notepad.core.engine.viewport.Viewport
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-performance rendering pipeline for infinite canvas.
 */
class RenderingPipeline(
    private val viewport: Viewport
) {
    private val mutableRenderState: MutableStateFlow<RenderState> = MutableStateFlow(
        RenderState(
            elements = emptyList(),
            dirtyRegions = emptySet(),
            renderCache = emptyMap(),
            frameCount = 0
        )
    )

    val renderState: StateFlow<RenderState> = mutableRenderState.asStateFlow()

    private val spatialIndex: QuadTree<CanvasElement> = QuadTree(
        boundary = RectF(-Float.MAX_VALUE / 2, -Float.MAX_VALUE / 2, Float.MAX_VALUE / 2, Float.MAX_VALUE / 2)
    )

    private var lastFrameElements: Set<String> = emptySet()

    /**
     * Update the list of elements to render.
     */
    fun updateElements(elements: List<CanvasElement>) {
        spatialIndex.clear()
        elements.forEach { spatialIndex.insert(it) }

        val currentElementIds = elements.map { it.id }.toSet()
        val dirtyRegions = mutableSetOf<RectF>()

        lastFrameElements.forEach { id ->
            if (id !in currentElementIds) {
                dirtyRegions.add(RectF.Zero)
            }
        }

        elements.forEach { element ->
            if (element.id !in lastFrameElements) {
                dirtyRegions.add(element.bounds)
            }
        }

        lastFrameElements = currentElementIds

        mutableRenderState.value = RenderState(
            elements = elements,
            dirtyRegions = dirtyRegions,
            renderCache = emptyMap(),
            frameCount = renderState.value.frameCount + 1
        )
    }

    /**
     * Get elements that are currently visible.
     */
    fun getVisibleElements(): List<CanvasElement> {
        val worldBounds = viewport.getWorldBounds()
        val results = spatialIndex.query(worldBounds)

        return results
            .filter { it.isVisible }
            .sortedBy { it.layerId }
    }

    /**
     * Mark a region as dirty.
     */
    fun invalidate(region: RectF) {
        val current = mutableRenderState.value
        mutableRenderState.value = current.copy(
            dirtyRegions = current.dirtyRegions + region
        )
    }

    /**
     * Mark entire canvas as dirty.
     */
    fun invalidateAll() {
        val current = mutableRenderState.value
        mutableRenderState.value = current.copy(
            dirtyRegions = setOf(RectF(
                -Float.MAX_VALUE / 2, -Float.MAX_VALUE / 2,
                Float.MAX_VALUE / 2, Float.MAX_VALUE / 2
            ))
        )
    }

    /**
     * Get elements at a specific point.
     */
    fun hitTest(point: androidx.compose.ui.geometry.Offset): List<CanvasElement> {
        val worldPoint = viewport.screenToWorld(
            `in`.innovaticshub.notepad.core.domain.geometry.PointF(
                point.x,
                point.y
            )
        )
        return spatialIndex.query(worldPoint, 10f)
            .filter { it.hitTest(worldPoint) }
            .sortedByDescending { it.layerId }
    }

    /**
     * Clear the rendering cache.
     */
    fun clearCache() {
        val current = mutableRenderState.value
        mutableRenderState.value = current.copy(
            renderCache = emptyMap()
        )
    }
}

/**
 * Immutable render state.
 */
data class RenderState(
    val elements: List<CanvasElement>,
    val dirtyRegions: Set<RectF>,
    val renderCache: Map<String, CachedRender>,
    val frameCount: Int
)

/**
 * Cached render data for an element.
 */
data class CachedRender(
    val elementId: String,
    val bitmap: android.graphics.Bitmap,
    val bounds: RectF,
    val zoomLevel: Float
)

/**
 * Element renderer interface.
 */
interface ElementRenderer<T : CanvasElement> {
    val tool: String
        get() = "renderer"
    fun render(element: T, drawScope: DrawScope, viewport: Viewport)

    fun getRenderBounds(element: T): RectF = element.bounds

    fun shouldRender(element: T, viewport: Viewport): Boolean {
        return element.isVisible && viewport.isVisible(element.bounds)
    }
}

/**
 * Stroke renderer with optimization.
 */
class StrokeRenderer : ElementRenderer<Stroke> {
    override fun render(
        element: Stroke,
        drawScope: DrawScope,
        viewport: Viewport
    ) {
        if (element.points.size < 2) return

        val screenPoints = element.points.map { viewport.worldToScreen(it) }

        val path = androidx.compose.ui.graphics.Path().apply {
            if (screenPoints.isNotEmpty()) {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) {
                    if (i < screenPoints.size - 1) {
                        val p0 = screenPoints[i - 1]
                        val p1 = screenPoints[i]
                        val p2 = screenPoints[i + 1]
                        val midX = (p1.x + p2.x) / 2
                        val midY = (p1.y + p2.y) / 2
                        quadraticBezierTo(p1.x, p1.y, midX, midY)
                    } else {
                        lineTo(screenPoints[i].x, screenPoints[i].y)
                    }
                }
            }
        }

        val scaledWidth = element.style.width * viewport.state.value.zoomLevel
        drawScope.drawPath(
            path = path,
            color = element.style.color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = scaledWidth,
                cap = element.style.cap,
                join = element.style.join
            ),
            alpha = element.opacity
        )
    }

    override fun shouldRender(
        element: Stroke,
        viewport: Viewport
    ): Boolean {
        if (!element.isVisible) return false
        return viewport.isVisible(element.bounds)
    }
}

/**
 * Shape renderer.
 */
class ShapeRenderer : ElementRenderer<`in`.innovaticshub.notepad.core.domain.model.ShapeElement> {
    override fun render(
        element: `in`.innovaticshub.notepad.core.domain.model.ShapeElement,
        drawScope: DrawScope,
        viewport: Viewport
    ) {
        val screenBounds = viewport.getTransform().transform(element.bounds)

        when (element.type) {
            is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Rectangle -> {
                if (element.style.fillColor != null) {
                    drawScope.drawRect(
                        color = element.style.fillColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            screenBounds.left,
                            screenBounds.top
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            screenBounds.width,
                            screenBounds.height
                        ),
                        alpha = element.opacity
                    )
                }
                drawScope.drawRect(
                    color = element.style.strokeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        screenBounds.left,
                        screenBounds.top
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        screenBounds.width,
                        screenBounds.height
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = element.style.strokeWidth * viewport.state.value.zoomLevel
                    ),
                    alpha = element.opacity
                )
            }
            is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Circle,
            is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Ellipse -> {
                if (element.style.fillColor != null) {
                    drawScope.drawOval(
                        color = element.style.fillColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            screenBounds.left,
                            screenBounds.top
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            screenBounds.width,
                            screenBounds.height
                        ),
                        alpha = element.opacity
                    )
                }
                drawScope.drawOval(
                    color = element.style.strokeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        screenBounds.left,
                        screenBounds.top
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        screenBounds.width,
                        screenBounds.height
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = element.style.strokeWidth * viewport.state.value.zoomLevel
                    ),
                    alpha = element.opacity
                )
            }
            else -> {
                // Handle other shapes
            }
        }
    }
}
