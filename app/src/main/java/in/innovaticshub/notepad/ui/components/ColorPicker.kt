package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.glassDark
import `in`.innovaticshub.notepad.ui.theme.glassLight

/**
 * Premium floating color picker.
 *
 * Design philosophy:
 * - Shows recent colors first
 * - Quick access to preset palette
 * - Custom color picker on expansion
 * - Animated transitions
 *
 * Inspired by: Procreate, GoodNotes, Concepts
 */
@Composable
fun ColorPickerPanel(
    isVisible: Boolean,
    selectedColor: Color,
    recentColors: List<Color>,
    isDark: Boolean = false,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(DesignTokens.spacing16)
                .then(
                    if (isDark) {
                        Modifier.glassDark()
                    } else {
                        Modifier.glassLight()
                    }
                ),
            shape = RoundedCornerShape(DesignTokens.radiusXLarge),
            color = Color.Transparent,
            tonalElevation = DesignTokens.elevationXLarge
        ) {
            Column(
                modifier = Modifier.padding(DesignTokens.spacing16),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Recent colors section
                if (recentColors.isNotEmpty()) {
                    ColorSectionHeader("Recent", isDark)

                    Spacer(modifier = Modifier.height(DesignTokens.spacing8))

                    ColorRow(
                        colors = recentColors,
                        selectedColor = selectedColor,
                        isDark = isDark,
                        onColorSelected = onColorSelected
                    )

                    Spacer(modifier = Modifier.height(DesignTokens.spacing12))
                }

                // Preset colors section
                ColorSectionHeader("Colors", isDark)

                Spacer(modifier = Modifier.height(DesignTokens.spacing8))

                ColorRow(
                    colors = getPresetColors(),
                    selectedColor = selectedColor,
                    isDark = isDark,
                    onColorSelected = onColorSelected
                )

                Spacer(modifier = Modifier.height(DesignTokens.spacing12))

                // Quick shades of selected color
                ColorSectionHeader("Shades", isDark)

                Spacer(modifier = Modifier.height(DesignTokens.spacing8))

                ShadeRow(
                    baseColor = selectedColor,
                    isDark = isDark,
                    onColorSelected = onColorSelected
                )
            }
        }
    }
}

/**
 * Horizontal scrolling row of color circles.
 */
@Composable
private fun ColorRow(
    colors: List<Color>,
    selectedColor: Color,
    isDark: Boolean,
    onColorSelected: (Color) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing12)
    ) {
        items(colors) { color ->
            ColorCircle(
                color = color,
                isSelected = color == selectedColor,
                isDark = isDark,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

/**
 * Individual color circle with selection indicator.
 */
@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val size = if (isSelected) 36.dp else 32.dp

    Box(
        modifier = Modifier
            .size(DesignTokens.touchTargetComfortable)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Selection ring
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .clip(CircleShape)
                    .border(
                        width = DesignTokens.borderThin,
                        color = AppColors.Accent,
                        shape = CircleShape
                    )
            )
        }

        // Color circle
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = DesignTokens.borderThin,
                            color = if (isDark) {
                                AppColors.SurfaceDarkBorder
                            } else {
                                AppColors.SurfaceLightBorder
                            },
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
        )
    }
}

/**
 * Row of shades based on the selected color.
 * Generates lighter and darker variants.
 */
@Composable
private fun ShadeRow(
    baseColor: Color,
    isDark: Boolean,
    onColorSelected: (Color) -> Unit
) {
    val shades = rememberShades(baseColor)

    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing12)
    ) {
        shades.forEach { shade ->
            ColorCircle(
                color = shade,
                isSelected = shade == baseColor,
                isDark = isDark,
                onClick = { onColorSelected(shade) }
            )
        }
    }
}

/**
 * Generates shades of a color (darker to lighter).
 */
private fun rememberShades(color: Color): List<Color> {
    // Generate 5 shades: darker, slightly darker, original, slightly lighter, lighter
    val factor = when {
        color.red > 0.8f && color.green > 0.8f && color.blue > 0.8f -> 0.15f // For light colors
        else -> 0.25f
    }

    return listOf(
        color.copy(red = (color.red - factor * 2).coerceIn(0f, 1f),
                   green = (color.green - factor * 2).coerceIn(0f, 1f),
                   blue = (color.blue - factor * 2).coerceIn(0f, 1f)),
        color.copy(red = (color.red - factor).coerceIn(0f, 1f),
                   green = (color.green - factor).coerceIn(0f, 1f),
                   blue = (color.blue - factor).coerceIn(0f, 1f)),
        color,
        color.copy(red = (color.red + factor).coerceIn(0f, 1f),
                   green = (color.green + factor).coerceIn(0f, 1f),
                   blue = (color.blue + factor).coerceIn(0f, 1f)),
        color.copy(red = (color.red + factor * 2).coerceIn(0f, 1f),
                   green = (color.green + factor * 2).coerceIn(0f, 1f),
                   blue = (color.blue + factor * 2).coerceIn(0f, 1f))
    )
}

/**
 * Section header for color categories.
 */
@Composable
private fun ColorSectionHeader(
    title: String,
    isDark: Boolean
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = DesignTokens.opacityMedium
        ),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Default preset colors for quick selection.
 * Curated for drawing and note-taking.
 */
private fun getPresetColors() = listOf(
    AppColors.InkBlack,      // Classic black
    AppColors.InkNavy,       // Rich blue
    Color(0xFF2D5A27),       // Forest green
    AppColors.InkRed,        // Warm red
    AppColors.InkPurple,     // Creative purple
    AppColors.InkOrange,     // Energetic orange
    Color(0xFF008080),       // Teal
    Color(0xFF8B4513),       // Brown
    Color(0xFF808080),       // Gray
    Color(0xFFFFD700),       // Gold
    Color(0xFFFF69B4),       // Pink
    Color(0xFF00CED1)        // Turquoise
)

/**
 * Compact color palette row for toolbar integration.
 *
 * Use this when you want color selection directly in the toolbar
 * without opening the full picker.
 */
@Composable
fun QuickColorPalette(
    colors: List<Color>,
    selectedColor: Color,
    isDark: Boolean = false,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = DesignTokens.spacing4),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing4)
    ) {
        colors.forEach { color ->
            QuickColorButton(
                color = color,
                isSelected = color == selectedColor,
                isDark = isDark,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

/**
 * Minimal color button for quick palette.
 */
@Composable
private fun QuickColorButton(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val size = if (isSelected) 24.dp else 20.dp

    Box(
        modifier = Modifier
            .size(DesignTokens.touchTargetMinimum - 8.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = DesignTokens.borderMedium,
                            color = if (isDark) Color.White else Color.Black,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
        )
    }
}
