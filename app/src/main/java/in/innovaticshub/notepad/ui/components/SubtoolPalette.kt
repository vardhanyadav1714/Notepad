package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens

/**
 * Simple circle icon for slider controls
 */
@Composable
private fun CircleIcon(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(tint)
    )
}

/**
 * Expandable subtool palette.
 */
@Composable
fun SubtoolPalette(
    isExpanded: Boolean,
    selectedSubtool: String,
    isDark: Boolean = false,
    onSubtoolSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPosition: AnchorPosition = AnchorPosition.BottomCenter,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = when (anchorPosition) {
            AnchorPosition.BottomCenter -> Alignment.BottomCenter
            AnchorPosition.TopCenter -> Alignment.TopCenter
        }
    ) {
        // Expanded palette
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            SubtoolPanel(
                selectedSubtool = selectedSubtool,
                isDark = isDark,
                onSubtoolSelected = onSubtoolSelected,
                onDismiss = onDismiss,
                modifier = Modifier
                    .padding(bottom = DesignTokens.spacing8)
                    .clip(RoundedCornerShape(DesignTokens.radiusXLarge))
                    .background(
                        if (isDark) AppColors.SurfaceDark else AppColors.SurfaceLight
                    )
            )
        }

        // Anchor content (the tool button that was pressed)
        content()
    }
}

/**
 * The actual panel content when expanded.
 */
@Composable
private fun SubtoolPanel(
    selectedSubtool: String,
    isDark: Boolean,
    onSubtoolSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(DesignTokens.spacing8),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Subtool options
        SubtoolGrid(
            subtools = listOf(
                Subtool("pencil", Icons.Default.Create, "Pencil"),
                Subtool("pen", Icons.Default.Edit, "Pen"),
                Subtool("marker", Icons.Default.Star, "Marker"),
                Subtool("highlighter", Icons.Default.Create, "Highlighter")
            ),
            selectedSubtool = selectedSubtool,
            isDark = isDark,
            onSubtoolSelected = onSubtoolSelected
        )

        Spacer(modifier = Modifier.height(DesignTokens.spacing8))

        // Size slider for the selected tool
        ToolSizeSlider(
            currentValue = 5f,
            onValueChange = { /* Handle size change */ },
            isDark = isDark
        )
    }
}

/**
 * Horizontal scrollable grid of subtools.
 */
@Composable
private fun SubtoolGrid(
    subtools: List<Subtool>,
    selectedSubtool: String,
    isDark: Boolean,
    onSubtoolSelected: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing8)
    ) {
        subtools.forEach { subtool ->
            SubtoolItem(
                subtool = subtool,
                isSelected = subtool.id == selectedSubtool,
                isDark = isDark,
                onClick = { onSubtoolSelected(subtool.id) }
            )
        }
    }
}

/**
 * Individual subtool button.
 */
@Composable
private fun SubtoolItem(
    subtool: Subtool,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(DesignTokens.radiusMedium))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    if (isDark) AppColors.SelectedDark else AppColors.SelectedLight
                } else {
                    Color.Transparent
                }
            )
            .padding(
                horizontal = DesignTokens.spacing12,
                vertical = DesignTokens.spacing8
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) {
                        AppColors.SurfaceDark
                    } else {
                        AppColors.SurfaceLight
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = subtool.icon,
                contentDescription = subtool.label,
                tint = if (isSelected) {
                    AppColors.Accent
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = DesignTokens.opacityHigh
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(DesignTokens.spacing4))

        Text(
            text = subtool.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (isSelected) {
                    DesignTokens.opacityHigh
                } else {
                    DesignTokens.opacityMedium
                }
            ),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Size slider for tool adjustment.
 */
@Composable
private fun ToolSizeSlider(
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.spacing8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small circle indicator
        CircleIcon(
            tint = MaterialTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.opacityMedium
            )
        )

        // Slider track
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .padding(horizontal = DesignTokens.spacing8)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = DesignTokens.opacityVeryLow
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentValue / 10f)
                    .height(4.dp)
                    .background(AppColors.Accent)
            )
        }

        // Large circle indicator
        CircleIcon(
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.opacityMedium
            )
        )
    }
}

/**
 * Data class for subtool information.
 */
data class Subtool(
    val id: String,
    val icon: ImageVector,
    val label: String
)

/**
 * Anchor position for the palette.
 */
enum class AnchorPosition {
    BottomCenter,
    TopCenter
}
