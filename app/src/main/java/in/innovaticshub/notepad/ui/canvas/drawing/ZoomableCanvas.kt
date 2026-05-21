package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import `in`.innovaticshub.notepad.ui.canvas.model.RectF
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import kotlin.math.ceil
import kotlin.math.floor

private enum class SelectionDragMode {
    Move,
    Scale
}

private data class SelectionHandle(
    val center: Offset,
    val pivot: Offset
)

/**
 * Infinite canvas: 1 finger / stylus = draw, 2+ fingers = pinch-zoom + pan.
 */
@Composable
fun ZoomableCanvas(
    viewModel: DrawingViewModel,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    zoomState: ZoomState,
    onZoomChanged: () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }
    var eraserPreview by remember { mutableStateOf<Offset?>(null) }
    val selectedBounds = remember(uiState.strokes, uiState.selectedStrokeIds) {
        computeSelectedBounds(uiState.strokes, uiState.selectedStrokeIds)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                zoomState.updateViewportSize(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(zoomState, uiState.currentTool, uiState.selectedStrokeIds) {
                awaitEachGesture {
                    val gestureSelectedBounds = computeSelectedBounds(
                        uiState.strokes,
                        uiState.selectedStrokeIds
                    )
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isStylus = firstDown.type == PointerType.Stylus ||
                        firstDown.type == PointerType.Eraser
                    val firstCanvasPoint = zoomState.screenToCanvas(firstDown.position)
                    var isMultiTouch = false
                    var dragStarted = false
                    var selectionDragMode: SelectionDragMode? = null
                    var lastSelectionPoint = firstCanvasPoint
                    var selectionPivot = Offset.Zero
                    var initialPinchDistance = 0f
                    var lastPinchCenter = Offset.Zero

                    if (uiState.currentTool.type == ToolType.LASSO && gestureSelectedBounds != null) {
                        val handleRadius = (34f / zoomState.scale).coerceIn(16f, 70f)
                        val resizeHandle = gestureSelectedBounds.selectionHandles(zoomState.scale)
                            .minByOrNull { (it.center - firstCanvasPoint).getDistance() }
                            ?.takeIf { (it.center - firstCanvasPoint).getDistance() <= handleRadius }
                        val insideSelection = gestureSelectedBounds
                            .expanded((34f / zoomState.scale).coerceIn(14f, 80f))
                            .contains(firstCanvasPoint)

                        if (resizeHandle != null || insideSelection) {
                            selectionDragMode = if (resizeHandle != null) SelectionDragMode.Scale else SelectionDragMode.Move
                            selectionPivot = resizeHandle?.pivot ?: gestureSelectedBounds.center
                            lastSelectionPoint = firstCanvasPoint
                            viewModel.beginSelectionTransform()
                            firstDown.consume()
                        }
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val pointerCount = pressed.size

                        if (pointerCount >= 2) {
                            if (dragStarted) {
                                viewModel.cancelStroke()
                                currentStroke = null
                                eraserPreview = null
                                dragStarted = false
                            }
                            if (selectionDragMode != null) {
                                viewModel.endSelectionTransform()
                                selectionDragMode = null
                            }
                            isMultiTouch = true
                            val p1 = pressed[0]
                            val p2 = pressed[1]
                            val newDistance = (p1.position - p2.position).getDistance()
                            val newCenter = (p1.position + p2.position) / 2f
                            if (initialPinchDistance > 0f) {
                                zoomState.transform(
                                    newCenter,
                                    newDistance / initialPinchDistance,
                                    newCenter - lastPinchCenter
                                )
                                onZoomChanged()
                            }
                            initialPinchDistance = newDistance
                            lastPinchCenter = newCenter
                            pressed.forEach { it.consume() }
                        } else if (pointerCount == 1 && !isMultiTouch) {
                            val change = pressed.first()
                            val pos = change.position
                            val canvasPoint = zoomState.screenToCanvas(pos)

                            if (selectionDragMode != null) {
                                change.consume()
                                when (selectionDragMode) {
                                    SelectionDragMode.Move -> {
                                        val delta = canvasPoint - lastSelectionPoint
                                        viewModel.moveSelectedStrokesBy(delta.x, delta.y)
                                    }
                                    SelectionDragMode.Scale -> {
                                        val previousDistance = (lastSelectionPoint - selectionPivot).getDistance()
                                        val nextDistance = (canvasPoint - selectionPivot).getDistance()
                                        if (previousDistance > 0.001f && nextDistance > 0.001f) {
                                            viewModel.scaleSelectedStrokesBy(
                                                scale = nextDistance / previousDistance,
                                                pivotX = selectionPivot.x,
                                                pivotY = selectionPivot.y
                                            )
                                        }
                                    }
                                }
                                lastSelectionPoint = canvasPoint
                                continue
                            }

                            if (!dragStarted) {
                                if (!isStylus && uiState.currentTool.type == ToolType.ERASER) {
                                    // finger on eraser: skip until transform mode
                                }
                                viewModel.startStroke(
                                    canvasPoint.x,
                                    canvasPoint.y,
                                    change.pressure.coerceIn(0.2f, 1f),
                                    minPointDistance = (0.7f / zoomState.scale).coerceIn(0.04f, 2f)
                                )
                                dragStarted = true
                            }
                            change.consume()
                            currentStroke = viewModel.addStrokePoint(
                                canvasPoint.x,
                                canvasPoint.y,
                                change.pressure.coerceIn(0.2f, 1f)
                            )
                            if (uiState.currentTool.isEraser) {
                                eraserPreview = pos
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dragStarted) {
                        viewModel.endStroke()
                        currentStroke = null
                        eraserPreview = null
                    }
                    if (selectionDragMode != null) {
                        viewModel.endSelectionTransform()
                    }
                }
            }
            .pointerInput(zoomState, onDoubleTap) {
                detectTapGestures(
                    onDoubleTap = {
                        if (onDoubleTap != null) {
                            onDoubleTap()
                        } else {
                            zoomState.toggleDoubleTapZoom()
                            onZoomChanged()
                        }
                    }
                )
            }
    ) {
        drawRect(color = backgroundColor)

        withTransform({
            translate(zoomState.offset.x, zoomState.offset.y)
            scale(zoomState.scale, zoomState.scale, pivot = Offset.Zero)
        }) {
            if (uiState.showGrid) {
                drawAdaptiveGrid(
                    scale = zoomState.scale,
                    offset = zoomState.offset,
                    isDark = backgroundColor.luminance() < 0.35f
                )
            }

            uiState.strokes.forEach { stroke ->
                drawStroke(stroke, selected = stroke.id in uiState.selectedStrokeIds)
            }
            selectedBounds?.let { drawSelectionOverlay(it, zoomState.scale) }
            currentStroke?.let { drawStroke(it) }
        }

        eraserPreview?.let { center ->
            val radius = uiState.currentTool.strokeWidth() / 2f * zoomState.scale
            drawCircle(
                color = Color(0xFF007AFF).copy(alpha = 0.14f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.92f),
                radius = radius,
                center = center,
                style = ComposeStroke(width = 3.5f)
            )
            drawCircle(
                color = Color(0xFF007AFF).copy(alpha = 0.82f),
                radius = radius,
                center = center,
                style = ComposeStroke(width = 2.2f)
            )
            drawCircle(
                color = Color(0xFF007AFF),
                radius = 3.5f,
                center = center
            )
        }
    }
}

private fun computeSelectedBounds(strokes: List<Stroke>, selectedIds: Set<String>): RectF? {
    val selected = strokes.filter { it.id in selectedIds }
    if (selected.isEmpty()) return null
    return RectF(
        left = selected.minOf { it.bounds.left },
        top = selected.minOf { it.bounds.top },
        right = selected.maxOf { it.bounds.right },
        bottom = selected.maxOf { it.bounds.bottom }
    )
}

private val RectF.topLeft: Offset
    get() = Offset(left, top)

private val RectF.topRight: Offset
    get() = Offset(right, top)

private val RectF.bottomLeft: Offset
    get() = Offset(left, bottom)

private val RectF.bottomRight: Offset
    get() = Offset(right, bottom)

private val RectF.center: Offset
    get() = Offset((left + right) / 2f, (top + bottom) / 2f)

private fun RectF.expanded(amount: Float): RectF {
    return RectF(left - amount, top - amount, right + amount, bottom + amount)
}

private fun RectF.contains(point: Offset): Boolean {
    return point.x in left..right && point.y in top..bottom
}

private fun RectF.selectionHandles(scale: Float): List<SelectionHandle> {
    val padding = selectionPadding(scale)
    val padded = expanded(padding)
    return listOf(
        SelectionHandle(center = padded.topLeft, pivot = padded.bottomRight),
        SelectionHandle(center = padded.topRight, pivot = padded.bottomLeft),
        SelectionHandle(center = padded.bottomLeft, pivot = padded.topRight),
        SelectionHandle(center = padded.bottomRight, pivot = padded.topLeft)
    )
}

private fun selectionPadding(scale: Float): Float {
    return (12f / scale).coerceIn(4f, 18f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAdaptiveGrid(
    scale: Float,
    offset: Offset,
    isDark: Boolean
) {
    var spacing = 40f
    while (spacing * scale < 24f) spacing *= 2f
    while (spacing * scale > 96f) spacing /= 2f

    val left = -offset.x / scale - spacing * 2f
    val top = -offset.y / scale - spacing * 2f
    val right = left + size.width / scale + spacing * 4f
    val bottom = top + size.height / scale + spacing * 4f
    val lineWidth = (1f / scale).coerceIn(0.2f, 1.5f)
    val color = if (isDark) {
        Color.White.copy(alpha = 0.09f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    var x = floor(left / spacing) * spacing
    val lastX = ceil(right / spacing) * spacing
    while (x <= lastX) {
        drawLine(color, Offset(x, top), Offset(x, bottom), lineWidth)
        x += spacing
    }
    var y = floor(top / spacing) * spacing
    val lastY = ceil(bottom / spacing) * spacing
    while (y <= lastY) {
        drawLine(color, Offset(left, y), Offset(right, y), lineWidth)
        y += spacing
    }
}

private fun Color.luminance(): Float {
    return red * 0.299f + green * 0.587f + blue * 0.114f
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionOverlay(
    bounds: RectF,
    scale: Float
) {
    val padding = selectionPadding(scale)
    val left = bounds.left - padding
    val top = bounds.top - padding
    val right = bounds.right + padding
    val bottom = bounds.bottom + padding
    val handleRadius = (10f / scale).coerceIn(4f, 10f)
    val strokeWidth = (2f / scale).coerceIn(1.2f, 2.6f)

    drawRect(
        color = Color(0xFF007AFF).copy(alpha = 0.09f),
        topLeft = Offset(left, top),
        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f))
    )
    drawRect(
        color = Color(0xFF007AFF),
        topLeft = Offset(left, top),
        size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
        style = ComposeStroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / scale, 8f / scale))
        )
    )

    listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom)
    ).forEachIndexed { index, center ->
        drawCircle(Color.White, radius = handleRadius * 1.25f, center = center)
        drawCircle(Color(0xFF007AFF), radius = handleRadius, center = center)
        if (index == 3) {
            drawCircle(
                color = Color.White,
                radius = handleRadius * 0.42f,
                center = center,
                style = ComposeStroke(width = strokeWidth)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: Stroke,
    selected: Boolean = false
) {
    val tool = stroke.toolConfig
    if (tool.isEraser) return

    if (tool.type == ToolType.LASSO) {
        drawPath(
            path = stroke.path,
            color = Color(0xFF007AFF),
            style = ComposeStroke(
                width = (2f / 1f).coerceAtLeast(1.5f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
            )
        )
        return
    }

    val style = ComposeStroke(
        width = tool.strokeWidth(),
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
        join = androidx.compose.ui.graphics.StrokeJoin.Round
    )

    when (tool.type) {
        ToolType.MARKER, ToolType.HIGHLIGHTER -> {
            drawPath(
                path = stroke.path,
                color = tool.effectiveColor(),
                style = ComposeStroke(
                    width = tool.strokeWidth(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    pathEffect = PathEffect.cornerPathEffect(4f)
                )
            )
        }
        ToolType.PENCIL -> {
            drawPath(
                path = stroke.path,
                color = tool.effectiveColor(),
                style = ComposeStroke(
                    width = tool.strokeWidth() * 0.85f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
        else -> {
            drawPath(
                path = stroke.path,
                color = tool.effectiveColor(),
                style = style
            )
        }
    }

    if (selected) {
        val bounds = stroke.bounds
        drawRect(
            color = Color(0xFF007AFF).copy(alpha = 0.18f),
            topLeft = Offset(bounds.left - 8f, bounds.top - 8f),
            size = Size(
                width = (bounds.right - bounds.left + 16f).coerceAtLeast(12f),
                height = (bounds.bottom - bounds.top + 16f).coerceAtLeast(12f)
            ),
            style = ComposeStroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        )
    }
}
