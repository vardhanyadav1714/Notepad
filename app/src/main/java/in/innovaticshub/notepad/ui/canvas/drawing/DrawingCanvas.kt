package `in`.innovaticshub.notepad.ui.canvas.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke as DrawScopeStroke
import androidx.compose.ui.input.pointer.pointerInput
import `in`.innovaticshub.notepad.ui.canvas.model.Stroke
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import kotlinx.coroutines.launch

/**
 * High-performance drawing canvas with smooth Bezier curves.
 *
 * Performance optimizations:
 * 1. Minimizes recomposition by keeping drawing state in ViewModel
 * 2. Direct path drawing for completed strokes
 * 3. Uses rememberCoroutineScope for coroutines
 */
@Composable
fun DrawingCanvas(
    viewModel: DrawingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Track current stroke for immediate feedback
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        viewModel.startStroke(offset.x, offset.y, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = viewModel.addStrokePoint(
                            change.position.x,
                            change.position.y,
                            1f
                        )
                    },
                    onDragEnd = {
                        viewModel.endStroke()
                        currentStroke = null
                    },
                    onDragCancel = {
                        viewModel.cancelStroke()
                        currentStroke = null
                    }
                )
            }
    ) {
        drawRect(color = uiState.canvasBackgroundColor)

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        }
        uiState.strokes.forEach { stroke -> drawStroke(stroke) }
        currentStroke?.let { stroke -> drawStroke(stroke) }
        drawIntoCanvas { canvas ->
            canvas.restore()
        }
    }
}

/**
 * Draw a single stroke with proper styling.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(
    stroke: Stroke
) {
    val toolConfig = stroke.toolConfig

    if (toolConfig.isEraser) {
        drawPath(
            path = stroke.path,
            color = Color.Transparent,
            style = DrawScopeStroke(
                width = toolConfig.strokeWidth() * 2f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            ),
            blendMode = BlendMode.Clear
        )
    } else if (toolConfig.type == ToolType.MARKER) {
        // Highlighter - semi-transparent with square cap
        drawPath(
            path = stroke.path,
            color = toolConfig.effectiveColor(),
            style = DrawScopeStroke(
                width = toolConfig.strokeWidth(),
                cap = androidx.compose.ui.graphics.StrokeCap.Square,
                join = androidx.compose.ui.graphics.StrokeJoin.Miter
            )
        )
    } else {
        // Normal pen stroke
        drawPath(
            path = stroke.path,
            color = toolConfig.effectiveColor(),
            style = DrawScopeStroke(
                width = toolConfig.strokeWidth(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

/**
 * Simplified drawing canvas without channels for better compatibility.
 */
@Composable
fun SimpleDrawingCanvas(
    viewModel: DrawingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        viewModel.startStroke(offset.x, offset.y, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentStroke = viewModel.addStrokePoint(
                            change.position.x,
                            change.position.y,
                            1f
                        )
                    },
                    onDragEnd = {
                        viewModel.endStroke()
                        currentStroke = null
                    },
                    onDragCancel = {
                        viewModel.cancelStroke()
                        currentStroke = null
                    }
                )
            }
    ) {
        drawRect(color = uiState.canvasBackgroundColor)

        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        }
        uiState.strokes.forEach { stroke -> drawStroke(stroke) }
        currentStroke?.let { stroke -> drawStroke(stroke) }
        drawIntoCanvas { canvas ->
            canvas.restore()
        }
    }
}

/**
 * Draw a grid pattern for the canvas background.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    gridSize: Float = 20f,
    color: Color = Color.Gray.copy(alpha = 0.1f)
) {
    val width = size.width
    val height = size.height

    val gridPath = Path().apply {
        // Vertical lines
        var x = gridSize
        while (x < width) {
            moveTo(x, 0f)
            lineTo(x, height)
            x += gridSize
        }
        // Horizontal lines
        var y = gridSize
        while (y < height) {
            moveTo(0f, y)
            lineTo(width, y)
            y += gridSize
        }
    }

    drawPath(
        path = gridPath,
        color = color,
        style = DrawScopeStroke(width = 1f)
    )
}
