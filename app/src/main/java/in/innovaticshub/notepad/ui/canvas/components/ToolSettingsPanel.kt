package `in`.innovaticshub.notepad.ui.canvas.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.canvas.model.ToolConfig
import `in`.innovaticshub.notepad.ui.canvas.model.ToolType
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.glassDark
import `in`.innovaticshub.notepad.ui.theme.glassLight

@Composable
fun ToolSettingsPanel(
    visible: Boolean,
    tool: ToolConfig,
    recentColors: List<Color>,
    isDark: Boolean,
    onColorSelected: (Color) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tool.type == ToolType.ERASER) {
        EraserSettingsPanel(
            visible = visible,
            tool = tool,
            isDark = isDark,
            onWidthChanged = onWidthChanged,
            onDismiss = onDismiss,
            modifier = modifier
        )
        return
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = DesignTokens.spacing16)
                .then(if (isDark) Modifier.glassDark() else Modifier.glassLight()),
            shape = RoundedCornerShape(DesignTokens.radiusXLarge),
            color = Color.Transparent,
            tonalElevation = DesignTokens.elevationLarge
        ) {
            Column(
                modifier = Modifier.padding(DesignTokens.spacing16),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.spacing12)
            ) {
                Text(
                    text = tool.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text("Color", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentColors + presetPalette()) { color ->
                        ColorSwatch(
                            color = color,
                            selected = color == tool.color,
                            onClick = { onColorSelected(color) }
                        )
                    }
                }

                Text("Thickness", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = tool.baseWidth,
                    onValueChange = onWidthChanged,
                    valueRange = 1f..48f
                )

                if (tool.type == ToolType.MARKER || tool.type == ToolType.HIGHLIGHTER) {
                    Text("Opacity", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = tool.opacity,
                        onValueChange = onOpacityChanged,
                        valueRange = 0.1f..1f
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun EraserSettingsPanel(
    visible: Boolean,
    tool: ToolConfig,
    isDark: Boolean,
    onWidthChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = DesignTokens.spacing16)
                .then(if (isDark) Modifier.glassDark() else Modifier.glassLight()),
            shape = RoundedCornerShape(DesignTokens.radiusXLarge),
            color = Color.Transparent
        ) {
            Column(Modifier.padding(DesignTokens.spacing16)) {
                Text("Eraser size", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = tool.baseWidth,
                    onValueChange = onWidthChanged,
                    valueRange = 8f..80f
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(0.3f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

private fun presetPalette(): List<Color> = listOf(
    Color(0xFF1C1C1E),
    Color(0xFF007AFF),
    Color(0xFFFF3B30),
    Color(0xFF34C759),
    Color(0xFFFF9500),
    Color(0xFFAF52DE),
    Color(0xFFFFD60A),
    Color(0xFF8E8E93)
)
