package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.glassDark
import `in`.innovaticshub.notepad.ui.theme.glassLight

/**
 * Minimal floating top bar.
 *
 * Philosophy: Essential actions only, visible when needed.
 * The top bar should feel lightweight and not obstruct the canvas.
 *
 * Contains:
 * - Back/Menu (left)
 * - Title (center, fades when scrolling)
 * - Actions (right: undo, redo, search, more)
 */
@Composable
fun FloatingTopBar(
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    isDark: Boolean = false,
    onNavigationClick: () -> Unit = {},
    title: String = "",
    actions: @Composable RowScope.() -> Unit = {}
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(
                    start = DesignTokens.spacing16,
                    top = DesignTokens.spacing8,
                    end = DesignTokens.spacing16
                )
                .then(
                    if (isDark) {
                        Modifier.glassDark()
                    } else {
                        Modifier.glassLight()
                    }
                ),
            color = Color.Transparent,
            tonalElevation = DesignTokens.elevationNone
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = DesignTokens.spacing12,
                    vertical = DesignTokens.spacing8
                ),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation button
                TopBarButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onNavigationClick,
                    contentDescription = "Navigate back"
                )

                // Title (optional)
                if (title.isNotEmpty()) {
                    androidx.compose.material3.Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = LocalContentColor.current.copy(
                            alpha = DesignTokens.opacityHigh
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Actions
                actions()
            }
        }
    }
}

/**
 * Minimal top bar button for icon-only actions.
 */
@Composable
fun TopBarButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isEnabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(DesignTokens.touchTargetMinimum),
        enabled = isEnabled,
        content = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = LocalContentColor.current.copy(
                    alpha = if (isEnabled) DesignTokens.opacityHigh else DesignTokens.opacityLow
                ),
                modifier = Modifier.size(DesignTokens.iconMedium)
            )
        }
    )
}
