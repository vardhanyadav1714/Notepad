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
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.canvas.zoom.ZoomState
import kotlin.math.max

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

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                zoomState.updateViewportSize(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(zoomState, uiState.currentTool) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val isStylus = firstDown.type == PointerType.Stylus ||
                        firstDown.type == PointerType.Eraser
                    var isMultiTouch = false
                    var dragStarted = false
                    var initialPinchDistance = 0f
                    var lastPinchCenter = Offset.Zero

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
                            if (!dragStarted) {
                                if (!isStylus && uiState.currentTool.type == ToolType.ERASER) {
                                    // finger on eraser: skip until transform mode
                                }
                                val canvasPoint = zoomState.screenToCanvas(pos)
                                viewModel.startStroke(
                                    canvasPoint.x,
                                    canvasPoint.y,
                                    change.pressure.coerceIn(0.2f, 1f),
                                    minPointDistance = (0.7f / zoomState.scale).coerceIn(0.04f, 2f)
                                )
                                dragStarted = true
                            }
                            change.consume()
                            val canvasPoint = zoomState.screenToCanvas(pos)
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
        withTransform({
            translate(zoomState.offset.x, zoomState.offset.y)
            scale(zoomState.scale, zoomState.scale, pivot = Offset.Zero)
        }) {
            val worldHalf = max(size.width, size.height) / zoomState.scale + 8000f
            drawRect(
                color = backgroundColor,
                topLeft = Offset(-worldHalf, -worldHalf),
                size = Size(worldHalf * 2f, worldHalf * 2f)
            )

            if (uiState.showGrid) {
                drawAdaptiveGrid(zoomState.scale)
            }

            uiState.strokes.forEach { stroke ->
                drawStroke(stroke, selected = stroke.id in uiState.selectedStrokeIds)
            }
            currentStroke?.let { drawStroke(it) }
        }

        eraserPreview?.let { center ->
            val radius = uiState.currentTool.strokeWidth() / 2f * zoomState.scale
            drawCircle(
                color = Color.Gray.copy(alpha = 0.35f),
                radius = radius,
                center = center,
                style = ComposeStroke(width = 2f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAdaptiveGrid(scale: Float) {
    var spacing = 40f
    while (spacing * scale < 24f) spacing *= 2f
    while (spacing * scale > 96f) spacing /= 2f

    val halfW = size.width / scale / 2f + spacing * 3f
    val halfH = size.height / scale / 2f + spacing * 3f
    val lineWidth = (1f / scale).coerceIn(0.2f, 1.5f)
    val color = Color.Gray.copy(alpha = 0.1f)

    var x = -halfW
    while (x <= halfW) {
        drawLine(color, Offset(x, -halfH), Offset(x, halfH), lineWidth)
        x += spacing
    }
    var y = -halfH
    while (y <= halfH) {
        drawLine(color, Offset(-halfW, y), Offset(halfW, y), lineWidth)
        y += spacing
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
