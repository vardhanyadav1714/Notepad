package `in`.innovaticshub.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import `in`.innovaticshub.notepad.ui.theme.DesignTokens
import `in`.innovaticshub.notepad.ui.theme.glassDark
import `in`.innovaticshub.notepad.ui.theme.glassLight

/**
 * Floating zoom indicator with animated appearance.
 *
 * Design philosophy:
 * - Appears only when zoom changes
 * - Auto-hides after 2 seconds of inactivity
 * - Shows only the current percentage, keeping the canvas uncluttered
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
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier,
            shape = RoundedCornerShape(999.dp),
            color = if (isDark) Color(0xE61C1C1E) else Color(0xF7FFFFFF),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) Color(0x24FFFFFF) else Color(0x14000000)
            ),
            shadowElevation = if (isDark) 0.dp else 8.dp,
            tonalElevation = 0.dp
        ) {
            Text(
                text = "${(zoomLevel * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = if (isDark) Color.White else Color(0xFF111827),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
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
