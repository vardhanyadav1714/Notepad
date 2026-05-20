package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.theme.AppColors
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.MotionTokens
import `in`.innovaticshub.notepad.ui.theme.glassPill

/**
 * Premium floating toolbar with glassmorphism effect.
 * Designed for thumb-reachable bottom placement.
 *
 * Design rationale:
 * - Pill shape = modern, approachable
 * - Glass effect = depth without heaviness
 * - Animated selection = clear feedback
 * - Proper touch targets = accessibility
 */
@Composable
fun FloatingToolbar(
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.glassPill(
            backgroundColor = if (isDark) AppColors.SurfaceDark else AppColors.SurfaceLight,
            isDark = isDark
        ),
        color = Color.Transparent,
        tonalElevation = DesignTokens.elevationNone
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.spacing16,
                vertical = DesignTokens.spacing8
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing4),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Premium toolbar button with animated states.
 *
 * Features:
 * - Scale animation on press
 * - Color transition on selection
 * - Haptic-style visual feedback
 * - Proper semantic descriptions
 */
@Composable
fun ToolbarButton(
    icon: ImageVector,
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> Color.Transparent
            isSelected -> AppColors.SelectedLight
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = MotionTokens.DurationFast),
        label = "backgroundColor"
    )

    val iconTint = tint ?: if (isSelected) {
        AppColors.Accent
    } else {
        LocalContentColor.current.copy(
            alpha = if (isEnabled) DesignTokens.opacityHigh else DesignTokens.opacityLow
        )
    }

    var isPressed by remember { mutableStateOf(false) }
    val scale: Dp by animateDpAsState(
        targetValue = when {
            isPressed -> 40.dp
            isSelected -> 48.dp
            else -> 44.dp
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(DesignTokens.touchTargetComfortable)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                this.role = Role.Button
            }
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        awaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        onLongClick?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(DesignTokens.iconMedium)
        )
    }
}

/**
 * Color picker button for quick access.
 * Shows a colored circle that can be tapped to open full picker.
 */
@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val size: Dp by animateDpAsState(
        targetValue = if (isSelected) 32.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "colorSize"
    )

    Box(
        modifier = modifier
            .size(DesignTokens.touchTargetComfortable)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                role = Role.Button
            }
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}
