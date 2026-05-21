package `in`.innovaticshub.notepad.ui.canvas.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.drawing.ZoomableCanvas
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.components.ZoomIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MarkupPanel {
    Colors,
    Tools,
    Collaboration
}

private enum class ColorMode {
    Grid,
    Spectrum,
    Sliders
}

@Composable
fun ModernDrawingScreen(
    viewModel: DrawingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val zoomState = viewModel.zoomState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chromeVisible by remember { mutableStateOf(true) }
    var showZoomHud by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<MarkupPanel?>(null) }
    var lastAutoSharedRoom by remember { mutableStateOf<String?>(null) }

    fun flashZoom() {
        showZoomHud = true
        scope.launch {
            delay(1600)
            showZoomHud = false
        }
    }

    fun roomLink(roomCode: String): String {
        return "https://notepad-collab.vinayyadav010010001.workers.dev/r/$roomCode"
    }

    fun shareRoom(roomCode: String) {
        val link = roomLink(roomCode)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Join my Notepad canvas:\n$link\n\nRoom code: $roomCode"
            )
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share canvas room"))
    }

    LaunchedEffect(uiState.collaborationRoomCode, uiState.collaborationStatus) {
        val roomCode = uiState.collaborationRoomCode
        val status = uiState.collaborationStatus.orEmpty()
        if (
            roomCode != null &&
            lastAutoSharedRoom != roomCode &&
            status.contains("ready", ignoreCase = true)
        ) {
            lastAutoSharedRoom = roomCode
            shareRoom(roomCode)
        }
    }

    Box(modifier.fillMaxSize()) {
        ZoomableCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
            backgroundColor = uiState.canvasBackgroundColor,
            zoomState = zoomState,
            onZoomChanged = { flashZoom() },
            onDoubleTap = { chromeVisible = !chromeVisible }
        )

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 10.dp, end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MarkupDock(
                    selected = uiState.currentTool.type,
                    currentColor = uiState.currentTool.color,
                    selectedCount = uiState.selectedStrokeIds.size,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    colorPanelOpen = activePanel == MarkupPanel.Colors,
                    toolsPanelOpen = activePanel == MarkupPanel.Tools,
                    collaborationPanelOpen = activePanel == MarkupPanel.Collaboration,
                    isCollaborating = uiState.collaborationRoomCode != null,
                    isCollaborationBusy = uiState.isCollaborationBusy,
                    onBack = onBackClick,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onClear = { viewModel.clearCanvas() },
                    onSelectionSmaller = { viewModel.scaleSelectionFromCenter(0.88f) },
                    onSelectionLarger = { viewModel.scaleSelectionFromCenter(1.12f) },
                    onSelectionNudgeLeft = { viewModel.nudgeSelection(-12f / zoomState.scale, 0f) },
                    onSelectionNudgeRight = { viewModel.nudgeSelection(12f / zoomState.scale, 0f) },
                    onSelectionDismiss = { viewModel.clearSelection() },
                    onToolSelected = {
                        viewModel.setTool(it)
                        activePanel = null
                    },
                    onColorClick = {
                        activePanel = if (activePanel == MarkupPanel.Colors) null else MarkupPanel.Colors
                    },
                    onAddClick = {
                        activePanel = if (activePanel == MarkupPanel.Tools) null else MarkupPanel.Tools
                    },
                    onCollaborationClick = {
                        activePanel = MarkupPanel.Collaboration
                        val roomCode = uiState.collaborationRoomCode
                        if (roomCode == null && !uiState.isCollaborationBusy) {
                            viewModel.createCollaborationRoom()
                        } else if (roomCode != null) {
                            shareRoom(roomCode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                MarkupColorPanel(
                    visible = activePanel == MarkupPanel.Colors,
                    selectedColor = uiState.currentTool.color,
                    recentColors = uiState.recentColors,
                    onColorSelected = { viewModel.setColor(it) },
                    onDismiss = { activePanel = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, end = 10.dp)
                )

                MarkupToolPanel(
                    visible = activePanel == MarkupPanel.Tools,
                    tool = uiState.currentTool,
                    onWidthChanged = { viewModel.setStrokeWidth(it) },
                    onOpacityChanged = { viewModel.setOpacity(it) },
                    onDismiss = { activePanel = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, end = 10.dp)
                )

                MarkupCollaborationPanel(
                    visible = activePanel == MarkupPanel.Collaboration,
                    roomCode = uiState.collaborationRoomCode,
                    status = uiState.collaborationStatus,
                    busy = uiState.isCollaborationBusy,
                    onCreate = { viewModel.createCollaborationRoom() },
                    onShare = { shareRoom(it) },
                    onLeave = { viewModel.leaveCollaborationRoom() },
                    onDismiss = { activePanel = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, end = 10.dp)
                )
            }
        }

        ZoomIndicator(
            zoomLevel = zoomState.scale,
            isVisible = showZoomHud,
            isDark = isDark,
            onZoomIn = { zoomState.zoomIn(); flashZoom() },
            onZoomOut = { zoomState.zoomOut(); flashZoom() },
            onReset = { zoomState.reset(); flashZoom() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        )
    }
}

@Composable
private fun MarkupDock(
    selected: ToolType,
    currentColor: Color,
    selectedCount: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    colorPanelOpen: Boolean,
    toolsPanelOpen: Boolean,
    collaborationPanelOpen: Boolean,
    isCollaborating: Boolean,
    isCollaborationBusy: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSelectionSmaller: () -> Unit,
    onSelectionLarger: () -> Unit,
    onSelectionNudgeLeft: () -> Unit,
    onSelectionNudgeRight: () -> Unit,
    onSelectionDismiss: () -> Unit,
    onToolSelected: (ToolType) -> Unit,
    onColorClick: () -> Unit,
    onAddClick: () -> Unit,
    onCollaborationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        ToolType.PEN,
        ToolType.PENCIL,
        ToolType.MARKER,
        ToolType.HIGHLIGHTER,
        ToolType.ERASER,
        ToolType.LASSO,
        ToolType.BRUSH
    )

    Surface(
        modifier = modifier.padding(horizontal = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFBFBFD).copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x18000000)),
        shadowElevation = 12.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopRoundButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            TopRoundButton(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo)
            TopRoundButton(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, enabled = canRedo)
            TopRoundButton(Icons.Default.Delete, "Delete selected", onClear)

            VerticalHairline()

            tools.forEach { tool ->
                MarkupToolButton(
                    tool = tool,
                    color = currentColor,
                    selected = selected == tool,
                    onClick = { onToolSelected(tool) }
                )
            }

            if (selectedCount > 0) {
                VerticalHairline()
                LassoTransformControls(
                    selectedCount = selectedCount,
                    onSmaller = onSelectionSmaller,
                    onLarger = onSelectionLarger,
                    onNudgeLeft = onSelectionNudgeLeft,
                    onNudgeRight = onSelectionNudgeRight,
                    onDismiss = onSelectionDismiss
                )
            }

            VerticalHairline()

            ColorWheelButton(
                selectedColor = currentColor,
                selected = colorPanelOpen,
                onClick = onColorClick
            )
            PlusButton(
                selected = toolsPanelOpen,
                onClick = onAddClick
            )
            DockIcon(
                icon = Icons.Default.Share,
                description = "Collaborate",
                onClick = onCollaborationClick,
                enabled = !isCollaborationBusy,
                selected = collaborationPanelOpen || isCollaborating
            )
        }
    }
}

@Composable
private fun LassoTransformControls(
    selectedCount: Int,
    onSmaller: () -> Unit,
    onLarger: () -> Unit,
    onNudgeLeft: () -> Unit,
    onNudgeRight: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFEAF3FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33007AFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$selectedCount",
                color = Color(0xFF0057B8),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            MiniTextButton("−", onSmaller)
            MiniTextButton("+", onLarger)
            MiniTextButton("‹", onNudgeLeft)
            MiniTextButton("›", onNudgeRight)
            MiniTextButton("×", onDismiss)
        }
    }
}

@Composable
private fun MiniTextButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.9f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color(0xFF111827),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun MarkupCollaborationPanel(
    visible: Boolean,
    roomCode: String?,
    status: String?,
    busy: Boolean,
    onCreate: () -> Unit,
    onShare: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F8FA),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Collaborate",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF111827),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                if (roomCode == null) {
                    Button(
                        onClick = onCreate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Create and share link")
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x16000000))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Share link ready", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                                Text(roomCode, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF111827))
                            }
                            TextButton(onClick = { onShare(roomCode) }, enabled = !busy) {
                                Text("Share")
                            }
                            TextButton(onClick = onLeave, enabled = !busy) {
                                Text("Leave")
                            }
                        }
                    }
                }

                if (!status.isNullOrBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.contains("failed", ignoreCase = true) ||
                            status.contains("could not", ignoreCase = true) ||
                            status.contains("not found", ignoreCase = true)
                        ) {
                            Color(0xFFB42318)
                        } else {
                            Color(0xFF4B5563)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkupColorPanel(
    visible: Boolean,
    selectedColor: Color,
    recentColors: List<Color>,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(ColorMode.Grid) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F8FA),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Colors",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF111827),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                SegmentedTabs(selectedMode = mode, onModeSelected = { mode = it })
                Spacer(Modifier.height(10.dp))

                when (mode) {
                    ColorMode.Grid -> ColorGrid(
                        selectedColor = selectedColor,
                        onColorSelected = onColorSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ColorMode.Spectrum -> SpectrumGrid(
                        selectedColor = selectedColor,
                        onColorSelected = onColorSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ColorMode.Sliders -> ColorSliders(
                        selectedColor = selectedColor,
                        onColorSelected = onColorSelected,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("OPACITY", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6B7280))
                OpacityStrip()

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(selectedColor)
                            .border(1.dp, Color(0x22000000), RoundedCornerShape(5.dp))
                    )
                    (recentColors + quickColors()).distinct().take(9).forEach { color ->
                        ColorDot(color, color == selectedColor) { onColorSelected(color) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkupToolPanel(
    visible: Boolean,
    tool: ToolConfig,
    onWidthChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF8F8FA),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.type.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF111827),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B7280))
                    Slider(
                        value = tool.baseWidth.coerceIn(tool.widthRange()),
                        onValueChange = onWidthChanged,
                        valueRange = tool.widthRange(),
                        steps = 0,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${tool.baseWidth.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.width(24.dp)
                    )
                    StrokePreview(tool)
                }

                if (tool.type == ToolType.MARKER || tool.type == ToolType.HIGHLIGHTER) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Opacity", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6B7280))
                        Slider(
                            value = tool.opacity,
                            onValueChange = onOpacityChanged,
                            valueRange = 0.1f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(
    selectedMode: ColorMode,
    onModeSelected: (ColorMode) -> Unit
) {
    val modes = listOf(ColorMode.Grid, ColorMode.Spectrum, ColorMode.Sliders)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, Color(0x16000000), RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        modes.forEach { mode ->
            Surface(
                onClick = { onModeSelected(mode) },
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                color = if (mode == selectedMode) Color(0xFFE9EAEE) else Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(mode.name, style = MaterialTheme.typography.labelSmall, color = Color(0xFF111827))
                }
            }
        }
    }
}

@Composable
private fun ColorGrid(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = 12
    val rows = 10
    val selectedCell = nearestGridCell(selectedColor, columns, rows)
    Box(modifier = modifier.height(190.dp)) {
        Column(Modifier.fillMaxSize()) {
            repeat(rows) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(columns) { column ->
                        val color = gridColor(column, row, columns, rows)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(color)
                                .border(0.25.dp, Color.White.copy(alpha = 0.22f))
                                .clip(RoundedCornerShape(0.dp))
                                .background(color)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Transparent,
                                onClick = { onColorSelected(color) }
                            ) {}
                        }
                    }
                }
            }
        }

        ColorSelectionRing(
            selectedColor = selectedColor,
            column = selectedCell.first,
            row = selectedCell.second,
            columns = columns,
            rows = rows
        )
    }
}

@Composable
private fun SpectrumGrid(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = 24
    val rows = 10
    val selectedCell = nearestSpectrumCell(selectedColor, columns, rows)
    Box(modifier = modifier.height(190.dp)) {
        Column(Modifier.fillMaxSize()) {
            repeat(rows) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(columns) { column ->
                        val color = spectrumColor(column, row, columns, rows)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            color = color,
                            onClick = { onColorSelected(color) }
                        ) {}
                    }
                }
            }
        }

        ColorSelectionRing(
            selectedColor = selectedColor,
            column = selectedCell.first,
            row = selectedCell.second,
            columns = columns,
            rows = rows
        )
    }
}

@Composable
private fun ColorSliders(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColorSliderRow("R", selectedColor.red, Color.Red) {
            onColorSelected(selectedColor.copy(red = it))
        }
        ColorSliderRow("G", selectedColor.green, Color(0xFF34C759)) {
            onColorSelected(selectedColor.copy(green = it))
        }
        ColorSliderRow("B", selectedColor.blue, Color(0xFF007AFF)) {
            onColorSelected(selectedColor.copy(blue = it))
        }
        ColorSliderRow("A", selectedColor.alpha, Color.Black) {
            onColorSelected(selectedColor.copy(alpha = it))
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF111827), modifier = Modifier.width(16.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = value.coerceIn(0.2f, 1f)))
        )
    }
}

@Composable
private fun ColorSelectionRing(
    selectedColor: Color,
    column: Int,
    row: Int,
    columns: Int,
    rows: Int
) {
    Canvas(Modifier.fillMaxSize()) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val center = Offset(
            cellWidth * (column + 0.5f),
            cellHeight * (row + 0.5f)
        )
        drawCircle(Color.White, radius = 23.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = 25.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
        drawCircle(selectedColor, radius = 9.dp.toPx(), center = center)
    }
}

@Composable
private fun OpacityStrip() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .height(24.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0f), Color.Black)
                    )
                )
                .border(1.dp, Color(0x18000000), RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("100%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF111827))
        }
    }
}

@Composable
private fun MarkupToolButton(
    tool: ToolType,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 29.dp, height = 46.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFFEAF3FF) else Color.Transparent,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0x33007AFF))
        } else {
            null
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppleMarkupGlyph(
                tool = tool,
                color = color,
                selected = selected,
                modifier = Modifier.size(width = 23.dp, height = 40.dp)
            )
        }
    }
}

@Composable
private fun AppleMarkupGlyph(
    tool: ToolType,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val body = Color(0xFFF5F6F8)
        val outline = Color(0xFF8E949C)
        val shadow = Color(0x24000000)
        val metal = Color(0xFFD8DCE1)
        val ink = if (tool == ToolType.ERASER) Color(0xFFE77683) else color

        if (selected) {
            drawCircle(Color(0xFF007AFF), radius = w * 0.10f, center = Offset(cx, h * 0.96f))
        }

        when (tool) {
            ToolType.PEN -> {
                drawRoundRect(shadow, Offset(cx - w * 0.13f, h * 0.10f), Size(w * 0.26f, h * 0.60f), CornerRadius(w * 0.08f, w * 0.08f))
                drawRoundRect(metal, Offset(cx - w * 0.12f, h * 0.08f), Size(w * 0.24f, h * 0.62f), CornerRadius(w * 0.08f, w * 0.08f))
                drawRoundRect(Color.White, Offset(cx - w * 0.065f, h * 0.16f), Size(w * 0.05f, h * 0.42f), CornerRadius(w * 0.03f, w * 0.03f))
                val nib = Path().apply {
                    moveTo(cx - w * 0.16f, h * 0.69f)
                    lineTo(cx + w * 0.16f, h * 0.69f)
                    lineTo(cx, h * 0.92f)
                    close()
                }
                drawPath(nib, ink)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.035f, center = Offset(cx, h * 0.78f))
            }
            ToolType.PENCIL -> {
                val wood = Color(0xFFE4B35B)
                drawRoundRect(ink, Offset(cx - w * 0.13f, h * 0.12f), Size(w * 0.26f, h * 0.56f), CornerRadius(w * 0.04f, w * 0.04f))
                drawLine(Color.White.copy(alpha = 0.65f), Offset(cx - w * 0.05f, h * 0.18f), Offset(cx - w * 0.05f, h * 0.62f), strokeWidth = w * 0.035f)
                val woodTip = Path().apply {
                    moveTo(cx - w * 0.13f, h * 0.68f)
                    lineTo(cx + w * 0.13f, h * 0.68f)
                    lineTo(cx, h * 0.86f)
                    close()
                }
                drawPath(woodTip, wood)
                val graphite = Path().apply {
                    moveTo(cx - w * 0.055f, h * 0.83f)
                    lineTo(cx + w * 0.055f, h * 0.83f)
                    lineTo(cx, h * 0.94f)
                    close()
                }
                drawPath(graphite, Color(0xFF3A3A3C))
            }
            ToolType.MARKER -> {
                drawRoundRect(body, Offset(cx - w * 0.20f, h * 0.09f), Size(w * 0.40f, h * 0.58f), CornerRadius(w * 0.09f, w * 0.09f))
                drawRoundRect(ink, Offset(cx - w * 0.18f, h * 0.38f), Size(w * 0.36f, h * 0.30f), CornerRadius(w * 0.03f, w * 0.03f))
                drawRoundRect(outline, Offset(cx - w * 0.12f, h * 0.67f), Size(w * 0.24f, h * 0.21f), CornerRadius(w * 0.02f, w * 0.02f))
                drawRoundRect(Color.White.copy(alpha = 0.8f), Offset(cx - w * 0.12f, h * 0.17f), Size(w * 0.06f, h * 0.17f), CornerRadius(w * 0.03f, w * 0.03f))
            }
            ToolType.HIGHLIGHTER -> {
                drawRoundRect(body, Offset(cx - w * 0.19f, h * 0.08f), Size(w * 0.38f, h * 0.56f), CornerRadius(w * 0.08f, w * 0.08f))
                drawRoundRect(ink.copy(alpha = 0.72f), Offset(cx - w * 0.21f, h * 0.38f), Size(w * 0.42f, h * 0.30f), CornerRadius(w * 0.02f, w * 0.02f))
                val chisel = Path().apply {
                    moveTo(cx - w * 0.21f, h * 0.68f)
                    lineTo(cx + w * 0.21f, h * 0.68f)
                    lineTo(cx + w * 0.13f, h * 0.88f)
                    lineTo(cx - w * 0.13f, h * 0.88f)
                    close()
                }
                drawPath(chisel, outline)
            }
            ToolType.ERASER -> {
                drawRoundRect(ink, Offset(cx - w * 0.21f, h * 0.15f), Size(w * 0.42f, h * 0.36f), CornerRadius(w * 0.10f, w * 0.10f))
                drawRoundRect(Color.White, Offset(cx - w * 0.21f, h * 0.46f), Size(w * 0.42f, h * 0.25f), CornerRadius(w * 0.07f, w * 0.07f))
                drawRoundRect(outline, Offset(cx - w * 0.18f, h * 0.72f), Size(w * 0.36f, h * 0.12f), CornerRadius(w * 0.04f, w * 0.04f))
            }
            ToolType.LASSO -> {
                val loop = Path().apply {
                    moveTo(cx - w * 0.28f, h * 0.45f)
                    cubicTo(cx - w * 0.28f, h * 0.22f, cx + w * 0.28f, h * 0.22f, cx + w * 0.28f, h * 0.46f)
                    cubicTo(cx + w * 0.28f, h * 0.70f, cx - w * 0.28f, h * 0.70f, cx - w * 0.28f, h * 0.45f)
                }
                drawPath(loop, Color(0xFF007AFF), style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
                drawLine(Color(0xFF007AFF), Offset(cx + w * 0.18f, h * 0.66f), Offset(cx + w * 0.34f, h * 0.88f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            else -> {
                drawLine(outline, Offset(cx, h * 0.10f), Offset(cx, h * 0.55f), strokeWidth = w * 0.16f, cap = StrokeCap.Round)
                drawLine(ink, Offset(cx, h * 0.52f), Offset(cx - w * 0.16f, h * 0.90f), strokeWidth = w * 0.12f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun ColorWheelButton(
    selectedColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = if (selected) Color(0xFFEAF3FF) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0x33007AFF)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            ColorWheelGlyph(selectedColor, Modifier.size(27.dp))
        }
    }
}

@Composable
private fun ColorWheelGlyph(
    selectedColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val strokeWidth = radius * 0.32f
        val colors = listOf(
            Color(0xFFFF3B30),
            Color(0xFFFF9500),
            Color(0xFFFFD60A),
            Color(0xFF34C759),
            Color(0xFF007AFF),
            Color(0xFF5856D6),
            Color(0xFFFF2D55)
        )

        colors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = index * (360f / colors.size) - 90f,
                sweepAngle = 360f / colors.size + 2f,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        drawCircle(Color.White, radius = radius * 0.42f, center = Offset(radius, radius))
        drawCircle(selectedColor, radius = radius * 0.25f, center = Offset(radius, radius))
    }
}

@Composable
private fun PlusButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        shape = CircleShape,
        color = if (selected) Color(0xFFE5E7EB) else Color(0xFFF1F2F4)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF111827), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DockIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFFE8F1FF) else Color.Transparent)
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(17.dp),
            tint = if (!enabled) Color(0x55111827) else if (selected) Color(0xFF007AFF) else Color(0xFF111827)
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Color(0x18000000))
    )
}

@Composable
private fun TopRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .size(30.dp),
        shape = CircleShape,
        color = Color(0xFFF2F3F5).copy(alpha = if (enabled) 1f else 0.6f),
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (enabled) Color(0xFF111827) else Color(0x55111827),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = color,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF111827)) else null
    ) {}
}

@Composable
private fun StrokePreview(tool: ToolConfig) {
    val color = if (tool.type == ToolType.ERASER) Color(0xFF9CA3AF) else tool.effectiveColor()
    Canvas(
        modifier = Modifier
            .width(50.dp)
            .height(30.dp)
    ) {
        val previewWidth = tool.strokeWidth().coerceIn(1.5f, 22f)
        drawLine(
            color = color,
            start = Offset(size.width * 0.16f, size.height / 2f),
            end = Offset(size.width * 0.84f, size.height / 2f),
            strokeWidth = previewWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun ToolConfig.widthRange(): ClosedFloatingPointRange<Float> {
    return when (type) {
        ToolType.PEN -> 0.6f..18f
        ToolType.PENCIL -> 0.4f..12f
        ToolType.MARKER -> 2f..30f
        ToolType.HIGHLIGHTER -> 3f..36f
        ToolType.BRUSH, ToolType.CALLIGRAPHY -> 1f..42f
        ToolType.ERASER -> 5f..76f
        ToolType.LASSO, ToolType.SHAPE, ToolType.TEXT -> 1f..24f
    }
}

private fun gridColor(column: Int, row: Int, columns: Int, rows: Int): Color {
    val hue = column * 360f / columns
    val saturation = 0.18f + (column.toFloat() / (columns - 1).coerceAtLeast(1)) * 0.82f
    val value = 1f - (row.toFloat() / (rows - 1).coerceAtLeast(1)) * 0.86f
    return Color.hsv(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0.08f, 1f))
}

private fun spectrumColor(column: Int, row: Int, columns: Int, rows: Int): Color {
    val hue = column * 360f / columns
    val saturation = 1f
    val value = 1f - (row.toFloat() / (rows - 1).coerceAtLeast(1)) * 0.72f
    return Color.hsv(hue, saturation, value.coerceIn(0.16f, 1f))
}

private fun nearestGridCell(color: Color, columns: Int, rows: Int): Pair<Int, Int> =
    nearestCell(color, columns, rows, ::gridColor)

private fun nearestSpectrumCell(color: Color, columns: Int, rows: Int): Pair<Int, Int> =
    nearestCell(color, columns, rows, ::spectrumColor)

private fun nearestCell(
    color: Color,
    columns: Int,
    rows: Int,
    palette: (Int, Int, Int, Int) -> Color
): Pair<Int, Int> {
    var bestColumn = 0
    var bestRow = 0
    var bestDistance = Float.MAX_VALUE

    repeat(rows) { row ->
        repeat(columns) { column ->
            val candidate = palette(column, row, columns, rows)
            val distance = colorDistance(color, candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestColumn = column
                bestRow = row
            }
        }
    }

    return bestColumn to bestRow
}

private fun colorDistance(a: Color, b: Color): Float {
    val red = a.red - b.red
    val green = a.green - b.green
    val blue = a.blue - b.blue
    return red * red + green * green + blue * blue
}

private fun quickColors(): List<Color> = listOf(
    Color.Black,
    Color(0xFF007AFF),
    Color(0xFF34C759),
    Color(0xFFFFD60A),
    Color(0xFFFF3B30),
    Color(0xFF64D2FF),
    Color.White,
    Color(0xFFFF9F0A),
    Color(0xFFBF5AF2),
    Color(0xFFFF7F50)
)

private fun ToolType.displayName(): String = when (this) {
    ToolType.PEN -> "Pen"
    ToolType.PENCIL -> "Pencil"
    ToolType.MARKER -> "Marker"
    ToolType.HIGHLIGHTER -> "Highlighter"
    ToolType.BRUSH -> "Brush"
    ToolType.CALLIGRAPHY -> "Calligraphy"
    ToolType.ERASER -> "Eraser"
    ToolType.LASSO -> "Lasso"
    ToolType.SHAPE -> "Shape"
    ToolType.TEXT -> "Text"
}
