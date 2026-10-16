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

        uiState.strokes.forEach { stroke -> renderStroke(stroke,darkPaper=isDarkPaper(uiState.currentPage,uiState.canvasBackgroundColor)) }
        currentStroke?.let { stroke -> renderStroke(stroke,darkPaper=isDarkPaper(uiState.currentPage,uiState.canvasBackgroundColor)) }
    }
}
