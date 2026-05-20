package `in`.innovaticshub.notepad.core.engine.renderer

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.domain.geometry.RectF
import `in`.innovaticshub.notepad.core.domain.model.CanvasElement
import `in`.innovaticshub.notepad.core.domain.model.Layer
import `in`.innovaticshub.notepad.core.domain.model.ShapeElement
import `in`.innovaticshub.notepad.core.domain.model.Stroke
import `in`.innovaticshub.notepad.core.domain.model.StrokeStyle
import `in`.innovaticshub.notepad.core.domain.model.TextBlock
import `in`.innovaticshub.notepad.core.domain.model.ImageElement
import `in`.innovaticshub.notepad.core.engine.viewport.Viewport
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as DrawScopeStroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * High-performance canvas renderer using Jetpack Compose Canvas.
 */
@Composable
fun InfiniteCanvasRenderer(
    viewport: Viewport,
    elements: Flow<List<CanvasElement>>,
    layers: StateFlow<List<Layer>>,
    modifier: Modifier = Modifier,
    showGrid: Boolean = false,
    gridColor: Color = Color(0xFFE0E0E0),
    backgroundColor: Color = Color.White
) {
    val elementsList by elements.collectAsState(initial = emptyList())
    val layersList by layers.collectAsState()
    val viewportState by viewport.state.collectAsState()

    val strokeRenderer = remember { CanvasStrokeRenderer() }
    val shapeRenderer = remember { CanvasShapeRenderer() }

    val visibleBounds = remember(viewportState) {
        viewport.getWorldBounds()
    }

    val visibleElements = remember(elementsList, layersList, visibleBounds) {
        val layerOrder = layersList.associate { it.id to it }
            .entries.sortedBy { it.value.zIndex }
            .map { it.key }

        elementsList
            .filter { it.isVisible && visibleBounds.intersects(it.bounds) }
            .sortedBy { layerOrder.indexOf(it.layerId) }
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
        onDraw = {
            // Draw background
            drawRect(color = backgroundColor)

            // Draw grid if enabled
            if (showGrid) {
                drawGrid(viewport, gridColor)
            }

            // Draw elements by layer
            visibleElements.forEach { element ->
                when (element) {
                    is Stroke -> {
                        strokeRenderer.render(element, this, viewport)
                    }
                    is ShapeElement -> {
                        shapeRenderer.render(element, this, viewport)
                    }
                    is TextBlock -> {
                        // Text rendering would be implemented here
                    }
                    is ImageElement -> {
                        // Image rendering would be implemented here
                    }
                }
            }
        }
    )
}

/**
 * Draw coordinate grid.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    viewport: Viewport,
    gridColor: Color
) {
    val state = viewport.state.value
    val worldBounds = viewport.getWorldBounds()

    val baseSpacing = 50f
    val zoomFactor = state.zoomLevel
    val spacing = when {
        zoomFactor < 0.5f -> baseSpacing * 4
        zoomFactor < 1f -> baseSpacing * 2
        zoomFactor < 2f -> baseSpacing
        zoomFactor < 4f -> baseSpacing / 2
        else -> baseSpacing / 4
    }

    val startX = (worldBounds.left / spacing).toInt() * spacing
    val endX = (worldBounds.right / spacing).toInt() * spacing + spacing
    val startY = (worldBounds.top / spacing).toInt() * spacing
    val endY = (worldBounds.bottom / spacing).toInt() * spacing + spacing

    val path = Path()
    val strokeWidth = 1f / zoomFactor

    // Vertical lines
    var x = startX
    while (x <= endX) {
        val screenX = viewport.worldToScreen(PointF(x, 0f)).x
        path.moveTo(screenX, 0f)
        path.lineTo(screenX, size.height)
        x += spacing.toInt()
    }

    // Horizontal lines
    var y = startY
    while (y <= endY) {
        val screenY = viewport.worldToScreen(PointF(0f, y)).y
        path.moveTo(0f, screenY)
        path.lineTo(size.width, screenY)
        y += spacing.toInt()
    }

    drawPath(
        path = path,
        color = gridColor,
        style = DrawScopeStroke(width = strokeWidth.coerceIn(0.5f, 2f))
    )
}

/**
 * Optimized stroke renderer for use in draw scope.
 */
private class CanvasStrokeRenderer {
    private val pathCache = mutableMapOf<String, Path>()

    fun render(
        stroke: Stroke,
        drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
        viewport: Viewport
    ) {
        if (stroke.points.size < 2) return

        val path = pathCache.getOrPut(stroke.id) {
            buildPath(stroke, viewport)
        }

        val scaledWidth = stroke.style.width * viewport.state.value.zoomLevel

        drawScope.drawPath(
            path = path,
            color = stroke.style.color,
            style = DrawScopeStroke(
                width = scaledWidth,
                cap = stroke.style.cap,
                join = stroke.style.join,
                pathEffect = if (stroke.style.blendMode != `in`.innovaticshub.notepad.core.domain.model.BlendMode.SrcOver) {
                    PathEffect.dashPathEffect(floatArrayOf(1f, 1f))
                } else {
                    null
                }
            ),
            alpha = stroke.opacity
        )
    }

    private fun buildPath(stroke: Stroke, viewport: Viewport): Path {
        return Path().apply {
            val points = stroke.points
            if (points.isNotEmpty()) {
                val first = viewport.worldToScreen(points.first())
                moveTo(first.x, first.y)

                for (i in 1 until points.size) {
                    val current = viewport.worldToScreen(points[i])

                    if (i < points.size - 1) {
                        val next = viewport.worldToScreen(points[i + 1])
                        val midX = (current.x + next.x) / 2
                        val midY = (current.y + next.y) / 2
                        quadraticBezierTo(current.x, current.y, midX, midY)
                    } else {
                        lineTo(current.x, current.y)
                    }
                }
            }
        }
    }

    fun invalidate(strokeId: String) {
        pathCache.remove(strokeId)
    }

    fun clear() {
        pathCache.clear()
    }
}

/**
 * Shape renderer for rectangles, circles, etc.
 */
private class CanvasShapeRenderer {
    private val pathCache = mutableMapOf<String, Path>()

    fun render(
        shape: ShapeElement,
        drawScope: androidx.compose.ui.graphics.drawscope.DrawScope,
        viewport: Viewport
    ) {
        val path = pathCache.getOrPut(shape.id) {
            buildShapePath(shape, viewport)
        }

        // Fill if specified
        shape.style.fillColor?.let { fillColor ->
            drawScope.drawPath(
                path = path,
                color = fillColor,
                alpha = shape.opacity
            )
        }

        // Draw stroke
        drawScope.drawPath(
            path = path,
            color = shape.style.strokeColor,
            style = DrawScopeStroke(
                width = shape.style.strokeWidth * viewport.state.value.zoomLevel
            ),
            alpha = shape.opacity
        )
    }

    private fun buildShapePath(shape: ShapeElement, viewport: Viewport): Path {
        return Path().apply {
            val bounds = shape.bounds
            val topLeft = viewport.worldToScreen(PointF(bounds.left, bounds.top))
            val bottomRight = viewport.worldToScreen(PointF(bounds.right, bounds.bottom))

            when (shape.type) {
                is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Rectangle -> {
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = topLeft.x,
                            top = topLeft.y,
                            right = bottomRight.x,
                            bottom = bottomRight.y
                        )
                    )
                }
                is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Circle,
                is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Ellipse -> {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            left = topLeft.x,
                            top = topLeft.y,
                            right = bottomRight.x,
                            bottom = bottomRight.y
                        )
                    )
                }
                is `in`.innovaticshub.notepad.core.domain.model.ShapeType.Line -> {
                    moveTo(topLeft.x, topLeft.y)
                    lineTo(bottomRight.x, bottomRight.y)
                }
                else -> {
                    // Default to rectangle
                    addRect(
                        androidx.compose.ui.geometry.Rect(
                            left = topLeft.x,
                            top = topLeft.y,
                            right = bottomRight.x,
                            bottom = bottomRight.y
                        )
                    )
                }
            }
        }
    }

    fun invalidate(shapeId: String) {
        pathCache.remove(shapeId)
    }

    fun clear() {
        pathCache.clear()
    }
}

/**
 * Preview renderer for strokes being drawn.
 */
@Composable
fun StrokePreview(
    points: List<PointF>,
    style: StrokeStyle,
    viewport: Viewport,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val viewportState by viewport.state.collectAsState()

    Canvas(modifier = modifier) {
        val path = Path().apply {
            val first = viewport.worldToScreen(points.first())
            moveTo(first.x, first.y)

            for (i in 1 until points.size) {
                val current = viewport.worldToScreen(points[i])
                if (i < points.size - 1) {
                    val next = viewport.worldToScreen(points[i + 1])
                    val midX = (current.x + next.x) / 2
                    val midY = (current.y + next.y) / 2
                    quadraticBezierTo(current.x, current.y, midX, midY)
                } else {
                    lineTo(current.x, current.y)
                }
            }
        }

        drawPath(
            path = path,
            color = style.color,
            style = DrawScopeStroke(
                width = style.width * viewportState.zoomLevel,
                cap = style.cap,
                join = style.join
            )
        )
    }
}
