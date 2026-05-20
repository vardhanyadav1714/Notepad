package `in`.innovaticshub.notepad.ui.canvas.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Floating drawing toolbar with tool selection and actions.
 *
 * @param selectedTool Currently selected tool
 * @param onToolChange Callback when tool is changed
 * @param canUndo Whether undo is available
 * @param canRedo Whether redo is available
 * @param onUndo Undo callback
 * @param onRedo Redo callback
 * @param onClear Clear canvas callback
 * @param modifier Modifier
 */
@Composable
fun DrawingToolbar(
    selectedTool: ToolType,
    onToolChange: (ToolType) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(DesignTokens.radiusXLarge),
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = DesignTokens.spacing8, vertical = DesignTokens.spacing4)
                .background(
                    color = if (isDark) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = RoundedCornerShape(DesignTokens.radiusXLarge)
                )
                .padding(DesignTokens.spacing4),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool buttons
            ToolButton(
                icon = Icons.Default.Create,
                label = "Pen",
                isSelected = selectedTool == ToolType.PEN,
                onClick = { onToolChange(ToolType.PEN) },
                isDark = isDark
            )

            ToolButton(
                icon = Icons.Default.AutoFixHigh,
                label = "Highlighter",
                isSelected = selectedTool == ToolType.MARKER,
                onClick = { onToolChange(ToolType.MARKER) },
                isDark = isDark
            )

            EraserButton(
                isSelected = selectedTool == ToolType.ERASER,
                onClick = { onToolChange(ToolType.ERASER) },
                isDark = isDark
            )

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
            )

            // Undo button
            ActionButton(
                icon = Icons.Default.Undo,
                contentDescription = "Undo",
                enabled = canUndo,
                onClick = onUndo
            )

            // Redo button
            ActionButton(
                icon = Icons.Default.Redo,
                contentDescription = "Redo",
                enabled = canRedo,
                onClick = onRedo
            )

            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
            )

            // Clear button
            ActionButton(
                icon = Icons.Default.Delete,
                contentDescription = "Clear",
                enabled = true,
                onClick = onClear,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Tool selection button with visual feedback.
 */
@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(),
        label = "backgroundColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusMedium))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(DesignTokens.spacing8),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.scale(scale)
            )
        }
    }
}

/**
 * Eraser button with special styling.
 */
@Composable
private fun EraserButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    isDark: Boolean
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(),
        label = "backgroundColor"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⌫",
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * Generic action button for undo/redo/clear.
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                tint
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

/**
 * Color palette for selecting stroke colors.
 */
@Composable
fun ColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberDefaultColors()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing8)
    ) {
        colors.forEach { color ->
            ColorButton(
                color = color,
                isSelected = color == selectedColor,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

/**
 * Individual color button.
 */
@Composable
private fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                shape = CircleShape
            )
            .scale(scale)
            .clickable(onClick = onClick)
    )
}

/**
 * Remember the default color palette.
 */
@Composable
private fun rememberDefaultColors(): List<Color> {
    return listOf(
        Color(0xFF1C1C1E), // Black
        Color(0xFF007AFF), // Blue
        Color(0xFF34C759), // Green
        Color(0xFFFF9500), // Orange
        Color(0xFFFF3B30), // Red
        Color(0xFFAF52DE), // Purple
    )
}

/**
 * Stroke width slider for adjusting brush size.
 */
@Composable
fun StrokeWidthSlider(
    currentWidth: Float,
    onWidthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Float = 1f,
    maxWidth: Float = 20f
) {
    Slider(
        value = currentWidth,
        onValueChange = onWidthChange,
        valueRange = minWidth..maxWidth,
        modifier = modifier
    )
}
