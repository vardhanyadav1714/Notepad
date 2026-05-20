package `in`.innovaticshub.notepad.ui.canvas.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.components.ColorPalette
import `in`.innovaticshub.notepad.ui.canvas.components.DrawingToolbar
import `in`.innovaticshub.notepad.ui.canvas.components.StrokeWidthSlider
import `in`.innovaticshub.notepad.ui.canvas.drawing.SimpleDrawingCanvas
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.canvas.viewmodel.DrawingViewModel
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import kotlinx.coroutines.launch

/**
 * Main drawing screen with full UI integration.
 *
 * Features:
 * - Full-screen drawing canvas
 * - Floating toolbar with tool selection
 * - Color picker panel
 * - Stroke width adjustment
 * - Undo/Redo functionality
 * - Smooth, pressure-sensitive drawing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    viewModel: DrawingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // UI state
    var showColorPalette by remember { mutableStateOf(false) }
    var showStrokeSlider by remember { mutableStateOf(false) }

    // Scroll behavior for top bar
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DrawingTopBar(
                onBackClick = onBackClick,
                onToggleDarkMode = { viewModel.toggleDarkCanvas() },
                isDark = uiState.canvasBackgroundColor != Color(0xFFFAFAFA),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (showColorPalette || showStrokeSlider) {
                FloatingActionButton(
                    onClick = {
                        showColorPalette = false
                        showStrokeSlider = false
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Drawing Canvas
            SimpleDrawingCanvas(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Color Palette Panel (shown when activated)
            AnimatedVisibility(
                visible = showColorPalette,
                enter = slideInVertically(
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                ColorPalettePanel(
                    selectedColor = uiState.currentTool.color,
                    onColorSelected = { color ->
                        viewModel.setColor(color)
                        showColorPalette = false
                    }
                )
            }

            // Stroke Width Panel (shown when activated)
            AnimatedVisibility(
                visible = showStrokeSlider,
                enter = slideInVertically(
                    initialOffsetY = { it }
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it }
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp)
            ) {
                StrokeWidthPanel(
                    currentWidth = uiState.currentTool.baseWidth,
                    onWidthChange = { width ->
                        viewModel.setStrokeWidth(width)
                    },
                    onDismiss = { showStrokeSlider = false }
                )
            }

            // Bottom Toolbar
            DrawingToolbar(
                selectedTool = uiState.currentTool.type,
                onToolChange = { tool -> viewModel.setTool(tool) },
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onClear = { viewModel.clearCanvas() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = DesignTokens.spacing16,
                        start = DesignTokens.spacing16,
                        end = DesignTokens.spacing16
                    ),
                isDark = uiState.canvasBackgroundColor != Color(0xFFFAFAFA)
            )

            // Quick Action Buttons (Color & Width)
            QuickActions(
                onShowColors = { showColorPalette = !showColorPalette },
                onShowWidth = { showStrokeSlider = !showStrokeSlider },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = DesignTokens.spacing16 + 60.dp,
                        end = DesignTokens.spacing16
                    )
            )
        }
    }
}

/**
 * Top app bar for the drawing screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingTopBar(
    onBackClick: () -> Unit,
    onToggleDarkMode: () -> Unit,
    isDark: Boolean,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = {
            Text(
                text = "Drawing",
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go back"
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleDarkMode) {
                Icon(
                    imageVector = if (isDark) {
                        Icons.Default.Close
                    } else {
                        Icons.Default.Close
                    },
                    contentDescription = "Toggle theme"
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

/**
 * Quick action buttons for color and stroke width.
 */
@Composable
private fun QuickActions(
    onShowColors: () -> Unit,
    onShowWidth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.spacing8)
    ) {
        QuickActionButton(
            icon = Icons.Default.Palette,
            contentDescription = "Colors",
            onClick = onShowColors
        )
        QuickActionButton(
            icon = Icons.Default.Create,
            contentDescription = "Stroke width",
            onClick = onShowWidth
        )
    }
}

/**
 * Quick action button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Color palette panel.
 */
@Composable
private fun ColorPalettePanel(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = DesignTokens.spacing16)
            .fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.radiusXLarge),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(DesignTokens.spacing16)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Color",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(DesignTokens.spacing12))
            ColorPalette(
                selectedColor = selectedColor,
                onColorSelected = onColorSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Stroke width panel.
 */
@Composable
private fun StrokeWidthPanel(
    currentWidth: Float,
    onWidthChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(horizontal = DesignTokens.spacing16)
            .fillMaxWidth(),
        shape = RoundedCornerShape(DesignTokens.radiusXLarge),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(DesignTokens.spacing16)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stroke Width",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(modifier = Modifier.height(DesignTokens.spacing12))
            StrokeWidthSlider(
                currentWidth = currentWidth,
                onWidthChange = onWidthChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Alternative bottom sheet version of the drawing screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreenWithBottomSheet(
    viewModel: DrawingViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = { Text("Drawing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        sheetContent = {
            DrawingToolsSheet(
                selectedTool = uiState.currentTool.type,
                selectedColor = uiState.currentTool.color,
                strokeWidth = uiState.currentTool.baseWidth,
                onToolChange = { viewModel.setTool(it) },
                onColorChange = { viewModel.setColor(it) },
                onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onClear = { viewModel.clearCanvas() }
            )
        },
        sheetPeekHeight = 80.dp
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SimpleDrawingCanvas(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Drawing tools bottom sheet content.
 */
@Composable
private fun DrawingToolsSheet(
    selectedTool: ToolType,
    selectedColor: Color,
    strokeWidth: Float,
    onToolChange: (ToolType) -> Unit,
    onColorChange: (Color) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DesignTokens.spacing16)
    ) {
        // Tool selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolType.entries.forEach { tool ->
                ToolSheetButton(
                    tool = tool,
                    isSelected = selectedTool == tool,
                    onClick = { onToolChange(tool) }
                )
            }
        }

        Spacer(modifier = Modifier.height(DesignTokens.spacing16))

        // Color palette
        ColorPalette(
            selectedColor = selectedColor,
            onColorSelected = onColorChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DesignTokens.spacing16))

        // Stroke width
        StrokeWidthSlider(
            currentWidth = strokeWidth,
            onWidthChange = onStrokeWidthChange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(DesignTokens.spacing16))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Icon(Icons.Default.Undo, contentDescription = null)
                Spacer(modifier = Modifier.size(DesignTokens.spacing8))
                Text("Undo")
            }
            Button(
                onClick = onRedo,
                enabled = canRedo
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
                Spacer(modifier = Modifier.size(DesignTokens.spacing8))
                Text("Redo")
            }
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.size(DesignTokens.spacing8))
                Text("Clear")
            }
        }
    }
}

/**
 * Tool button for bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolSheetButton(
    tool: ToolType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = when (tool) {
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
            )
        },
        leadingIcon = {
            Icon(
                imageVector = when (tool) {
                    ToolType.PEN -> Icons.Default.Create
                    ToolType.PENCIL -> Icons.Default.Create
                    ToolType.MARKER -> Icons.Default.Palette
                    ToolType.HIGHLIGHTER -> Icons.Default.Palette
                    ToolType.BRUSH -> Icons.Default.Edit
                    ToolType.CALLIGRAPHY -> Icons.Default.Edit
                    ToolType.ERASER -> Icons.Default.Close
                    ToolType.LASSO -> Icons.Default.Brush
                    ToolType.SHAPE -> Icons.Default.Create
                    ToolType.TEXT -> Icons.Default.Edit
                },
                contentDescription = null
            )
        }
    )
}
