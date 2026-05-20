package `in`.innovaticshub.notepad.ui.canvas

import `in`.innovaticshub.notepad.core.domain.geometry.PointF
import `in`.innovaticshub.notepad.core.engine.renderer.InfiniteCanvasRenderer
import `in`.innovaticshub.notepad.core.engine.renderer.StrokePreview
import `in`.innovaticshub.notepad.core.engine.gesture.PointerTool
import `in`.innovaticshub.notepad.core.engine.tool.BrushType
import `in`.innovaticshub.notepad.core.engine.tool.Tool
import `in`.innovaticshub.notepad.core.engine.tool.Tool.Draw
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset as ComposeOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map

/**
 * Main infinite canvas composable.
 */
@Composable
fun InfiniteCanvas(
    viewModel: InfiniteCanvasViewModel,
    modifier: Modifier = Modifier
) {
    val canvasState by viewModel.canvasState.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val toolProps by viewModel.getToolProperties().collectAsState()
    val previewStroke by viewModel.previewStroke.collectAsState()

    Box(
        modifier = modifier.onSizeChanged { size ->
            viewModel.updateScreenSize(size.width.toFloat(), size.height.toFloat())
        }
    ) {
        // Main canvas
        InfiniteCanvasRenderer(
            viewport = viewModel.getViewport(),
            elements = viewModel.canvasState.map { it.elements },
            layers = viewModel.layers,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(viewModel) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewModel.pan(pan.x, pan.y)
                        viewModel.zoom(PointF(centroid.x, centroid.y), zoom)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val position = down.position

                        val toolType = when {
                            down.type == PointerType.Stylus -> PointerTool.Stylus
                            down.type == PointerType.Eraser -> PointerTool.Eraser
                            else -> PointerTool.Finger
                        }

                        viewModel.handlePointerDown(
                            pointerId = down.id.value.toInt(),
                            position = position,
                            pressure = down.pressure,
                            tool = toolType
                        )

                        drag(down.id) { change ->
                            val eventPosition = change.position
                            viewModel.handlePointerMove(
                                pointerId = change.id.value.toInt(),
                                position = eventPosition,
                                pressure = change.pressure
                            )
                            change.consume()
                        }

                        viewModel.handlePointerUp(
                            pointerId = down.id.value.toInt(),
                            position = position
                        )
                    }
                },
            showGrid = uiState.showGrid
        )

        // Stroke preview overlay
        previewStroke?.let { stroke ->
            if (stroke.points.isNotEmpty()) {
                StrokePreview(
                    points = stroke.points,
                    style = stroke.style,
                    viewport = viewModel.getViewport(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top toolbar
        CanvasToolbar(
            currentTool = uiState.currentTool,
            onToolSelected = { viewModel.selectTool(it) },
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Bottom tool options
        ToolOptionsBar(
            currentTool = uiState.currentTool,
            currentColor = toolProps.primaryColor,
            strokeWidth = toolProps.strokeWidth,
            onColorChange = { viewModel.setColor(it) },
            onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Zoom controls
        ZoomControls(
            onZoomIn = {
                val viewportState = viewModel.getViewport().state.value
                viewModel.zoom(
                    PointF(viewportState.screenWidth / 2, viewportState.screenHeight / 2),
                    1.2f
                )
            },
            onZoomOut = {
                val viewportState = viewModel.getViewport().state.value
                viewModel.zoom(
                    PointF(viewportState.screenWidth / 2, viewportState.screenHeight / 2),
                    0.8f
                )
            },
            onReset = { viewModel.resetViewport() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

/**
 * Top toolbar with tool selection and undo/redo.
 */
@Composable
fun CanvasToolbar(
    currentTool: Tool,
    onToolSelected: (Tool) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Undo",
                    tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Redo",
                    tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxHeight()
                    .width(1.dp)
            )

            ToolButton(Tool.Select, currentTool, Icons.Default.Search, onClick = { onToolSelected(Tool.Select) })
            ToolButton(Tool.Pan, currentTool, Icons.Default.Close, onClick = { onToolSelected(Tool.Pan) })

            BrushType.entries.forEach { brushType ->
                val icon = when (brushType) {
                    BrushType.Pencil -> Icons.Default.Edit
                    BrushType.Pen -> Icons.Default.Create
                    BrushType.Highlighter -> Icons.Default.Search
                    BrushType.Marker -> Icons.Default.Create
                    else -> Icons.Default.Create
                }
                ToolButton(Tool.Draw(brushType), currentTool, icon) {
                    onToolSelected(Tool.Draw(brushType))
                }
            }

            ToolButton(Tool.Eraser, currentTool, Icons.Default.Close, onClick = { onToolSelected(Tool.Eraser) })
        }
    }
}

/**
 * Tool selection button.
 */
@Composable
fun ToolButton(
    tool: Tool,
    currentTool: Tool,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val isSelected = tool == currentTool

    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = tool.displayName,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * Bottom tool options bar.
 */
@Composable
fun ToolOptionsBar(
    currentTool: Tool,
    currentColor: androidx.compose.ui.graphics.Color,
    strokeWidth: Float,
    onColorChange: (androidx.compose.ui.graphics.Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorPicker(
                selectedColor = currentColor,
                onColorSelected = onColorChange
            )

            if (currentTool is Tool.Draw || currentTool is Tool.Eraser) {
                StrokeWidthSlider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = when (currentTool) {
                        is Tool.Draw -> when (currentTool.brushType) {
                            BrushType.Pencil -> 1f..10f
                            BrushType.Highlighter -> 10f..50f
                            else -> 1f..30f
                        }
                        is Tool.Eraser -> 10f..100f
                        else -> 1f..30f
                    }
                )
            }
        }
    }
}

/**
 * Color picker for stroke color.
 */
@Composable
fun ColorPicker(
    selectedColor: androidx.compose.ui.graphics.Color,
    onColorSelected: (androidx.compose.ui.graphics.Color) -> Unit
) {
    val colors = listOf(
        androidx.compose.ui.graphics.Color.Black,
        androidx.compose.ui.graphics.Color(0xFF2196F3),
        androidx.compose.ui.graphics.Color(0xFF4CAF50),
        androidx.compose.ui.graphics.Color(0xFFFF9800),
        androidx.compose.ui.graphics.Color(0xFFF44336),
        androidx.compose.ui.graphics.Color(0xFF9C27B0)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { color ->
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, CircleShape)
                    .clickable { onColorSelected(color) }
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

/**
 * Stroke width slider.
 */
@Composable
fun StrokeWidthSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 1f..30f
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.width(120.dp)
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.toInt().toString(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Zoom controls.
 */
@Composable
fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.Add, "Zoom In")
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, "Reset Zoom")
            }
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.Close, "Zoom Out")
            }
        }
    }
}
