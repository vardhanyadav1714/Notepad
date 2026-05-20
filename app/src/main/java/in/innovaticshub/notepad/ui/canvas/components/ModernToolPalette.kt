package `in`.innovaticshub.notepad.ui.canvas.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.theme.DesignTokens

/**
 * Modern floating tool palette with tabbed interface.
 */
@Composable
fun ModernToolPalette(
    selectedTool: ToolType,
    selectedColor: Color,
    strokeWidth: Float,
    opacity: Float,
    onToolSelected: (ToolType) -> Unit,
    onColorSelected: (Color) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 16.dp,
        tonalElevation = 8.dp,
        color = if (isDark) {
            Color(0xFF2C2C2E)
        } else {
            Color.White
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Tab indicator
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    )
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { },
                    text = { Text("Tools") },
                    selectedContentColor = MaterialTheme.colorScheme.primary
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { },
                    text = { Text("Color") },
                    selectedContentColor = MaterialTheme.colorScheme.primary
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { },
                    text = { Text("Size") },
                    selectedContentColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content based on selected tab
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> ToolsContent(
                        selectedTool = selectedTool,
                        onToolSelected = onToolSelected,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onClear = onClear
                    )
                    1 -> ColorContent(
                        selectedColor = selectedColor,
                        onColorSelected = onColorSelected
                    )
                    2 -> SizeContent(
                        strokeWidth = strokeWidth,
                        opacity = opacity,
                        onStrokeWidthChanged = onStrokeWidthChanged,
                        onOpacityChanged = onOpacityChanged
                    )
                }
            }
        }
    }
}

/**
 * Tools selection content.
 */
@Composable
private fun ToolsContent(
    selectedTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        // Tool grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolType.entries.forEach { tool ->
                ModernToolButton(
                    tool = tool,
                    isSelected = selectedTool == tool,
                    onClick = { onToolSelected(tool) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModernActionButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )
            ModernActionButton(
                icon = Icons.Default.Redo,
                label = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )
            ModernActionButton(
                icon = Icons.Default.Delete,
                label = "Clear",
                enabled = true,
                onClick = onClear,
                isDestructive = true
            )
        }
    }
}

/**
 * Modern tool button with visual feedback.
 */
@Composable
private fun ModernToolButton(
    tool: ToolType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(),
        label = "containerColor"
    )

    val icon: ImageVector = when (tool) {
        ToolType.PEN -> Icons.Default.Brush
        ToolType.PENCIL -> Icons.Default.Create
        ToolType.MARKER -> Icons.Default.Gesture
        ToolType.HIGHLIGHTER -> Icons.Default.Edit
        ToolType.BRUSH -> Icons.Default.Brush
        ToolType.CALLIGRAPHY -> Icons.Default.Edit
        ToolType.ERASER -> Icons.Default.Delete
        ToolType.LASSO -> Icons.Default.Gesture
        ToolType.SHAPE -> Icons.Default.Create
        ToolType.TEXT -> Icons.Default.Edit
    }

    val label: String = when (tool) {
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Modern action button.
 */
@Composable
private fun ModernActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val containerColor by animateColorAsState(
        targetValue = if (isDestructive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = spring(),
        label = "containerColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) {
                if (isDestructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                if (isDestructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

/**
 * Color picker content.
 */
@Composable
private fun ColorContent(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Column {
        Text(
            text = "Stroke Color",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Color grid
        val colors = rememberColors()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(colors) { color ->
                ColorSwatch(
                    color = color,
                    isSelected = color == selectedColor,
                    onClick = { onColorSelected(color) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Opacity slider
        Text(
            text = "Opacity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModernSlider(
            value = 1f,
            onValueChange = { },
            valueRange = 0f..1f
        )
    }
}

/**
 * Color swatch button.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
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
            .scale(scale)
            .clickable(onClick = onClick)
    )
}

/**
 * Size and opacity controls.
 */
@Composable
private fun SizeContent(
    strokeWidth: Float,
    opacity: Float,
    onStrokeWidthChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit
) {
    Column {
        // Stroke width
        Text(
            text = "Stroke Size",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        StrokeSizePreview(strokeWidth)
        Spacer(modifier = Modifier.height(12.dp))
        ModernSlider(
            value = strokeWidth,
            onValueChange = onStrokeWidthChanged,
            valueRange = 1f..50f
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Opacity
        Text(
            text = "Opacity ${(opacity * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModernSlider(
            value = opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0f..1f
        )
    }
}

/**
 * Stroke size preview.
 */
@Composable
private fun StrokeSizePreview(size: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * Modern slider component.
 */
@Composable
private fun ModernSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        colors = androidx.compose.material3.SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            thumbColor = MaterialTheme.colorScheme.primary
        )
    )
}

/**
 * Remember predefined colors.
 */
@Composable
private fun rememberColors(): List<Color> {
    return listOf(
        Color(0xFF1C1C1E),  // Black
        Color(0xFF4A4A4A),  // Gray
        Color(0xFF6B2D7B),  // Purple
        Color(0xFF007AFF),  // Blue
        Color(0xFF34C759),  // Green
        Color(0xFFFFD60A),  // Yellow
        Color(0xFFFF9500),  // Orange
        Color(0xFFFF3B30),  // Red
        Color(0xFFFF6B9D),  // Pink
    )
}

/**
 * Quick access toolbar for common actions.
 */
@Composable
fun QuickAccessToolbar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo
            ) {
                Icon(
                    Icons.Default.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

/**
 * Zoom control panel.
 */
@Composable
fun ZoomControlPanel(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onZoomOut) {
                Icon(Icons.Default.ZoomOut, "Zoom Out")
            }
            Text(
                text = "${(zoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(48.dp)
            )
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.ZoomIn, "Zoom In")
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Gesture, "Reset")
            }
        }
    }
}
