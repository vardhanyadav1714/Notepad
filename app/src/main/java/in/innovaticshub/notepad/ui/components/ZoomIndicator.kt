package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.glassDark
import `in`.innovaticshub.notepad.ui.theme.glassLight

/**
 * Floating zoom indicator with animated appearance.
 *
 * Design philosophy:
 * - Appears only when zoom changes
 * - Auto-hides after 2 seconds of inactivity
 * - Shows percentage with + and - controls
 * - Positioned away from thumb zones
 *
 * Inspired by: Procreate, Concepts, Figma
 */
@Composable
fun ZoomIndicator(
    zoomLevel: Float,
    isVisible: Boolean,
    isDark: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "scale"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isDark) {
                        Modifier.glassDark()
                    } else {
                        Modifier.glassLight()
                    }
                )
                .clip(RoundedCornerShape(DesignTokens.radiusLarge)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = DesignTokens.spacing12,
                    vertical = DesignTokens.spacing8
                ),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom out button
                ZoomControlButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    onClick = onZoomOut,
                    contentDescription = "Zoom out"
                )

                // Percentage display
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DesignTokens.radiusSmall))
                        .background(
                            if (isDark) {
                                AppColors.SelectedDark
                            } else {
                                AppColors.SelectedLight
                            }
                        )
                        .padding(horizontal = DesignTokens.spacing12, vertical = DesignTokens.spacing4)
                ) {
                    Text(
                        text = "${(zoomLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.Accent
                    )
                }

                // Zoom in button
                ZoomControlButton(
                    icon = Icons.Default.Add,
                    onClick = onZoomIn,
                    contentDescription = "Zoom in"
                )

                // Reset button (appears when not at 100%)
                if (zoomLevel != 1f) {
                    ZoomControlButton(
                        icon = Icons.Default.Refresh,
                        onClick = onReset,
                        contentDescription = "Reset zoom"
                    )
                }
            }
        }
    }
}

/**
 * Compact button for zoom controls.
 */
@Composable
private fun ZoomControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String?
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(DesignTokens.touchTargetMinimum)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.opacityHigh
            ),
            modifier = Modifier.size(DesignTokens.iconSmall)
        )
    }
}

/**
 * Minimal zoom badge for permanent display (optional).
 *
 * Use this when you want a subtle, always-visible zoom indicator
 * that doesn't obstruct the canvas.
 */
@Composable
fun ZoomBadge(
    zoomLevel: Float,
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (isDark) {
                    Modifier.glassDark()
                } else {
                    Modifier.glassLight()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${(zoomLevel * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = DesignTokens.opacityMedium
            ),
            modifier = Modifier.padding(
                horizontal = DesignTokens.spacing8,
                vertical = DesignTokens.spacing4
            )
        )
    }
}
