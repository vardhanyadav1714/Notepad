package `in`.innovaticshub.notepad.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium glassmorphism effect.
 * Creates translucent, blurred surfaces with subtle borders.
 *
 * Inspired by:
 * - iOS frosted glass (UIBlurEffect)
 * - macOS Big Sur panels
 * - Windows 11 Acrylic material
 */

/**
 * Applies glassmorphism effect to a modifier.
 *
 * @param backgroundColor The translucent background color
 * @param borderColor The subtle border color
 * @param borderWidth The border width (default: hairline)
 * @param cornerRadius The corner radius for the surface
 * @param elevation The shadow elevation
 */
fun Modifier.glassmorphism(
    backgroundColor: Color,
    borderColor: Color,
    borderWidth: Dp = DesignTokens.borderHairline,
    cornerRadius: Dp = DesignTokens.radiusLarge,
    elevation: Dp = DesignTokens.elevationMedium
): Modifier = composed {
    this
        .shadow(
            elevation = elevation,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
            ambientColor = Color.Black.copy(alpha = 0.1f),
            spotColor = Color.Black.copy(alpha = 0.1f)
        )
        .background(
            color = backgroundColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
        )
        .border(
            width = borderWidth,
            color = borderColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
        )
}

/**
 * Light mode glassmorphism preset.
 * Use for floating panels in light theme.
 */
fun Modifier.glassLight(): Modifier = glassmorphism(
    backgroundColor = AppColors.SurfaceLight,
    borderColor = AppColors.SurfaceLightBorder,
    cornerRadius = DesignTokens.radiusLarge,
    elevation = DesignTokens.elevationMedium
)

/**
 * Dark mode glassmorphism preset.
 * Use for floating panels in dark theme.
 */
fun Modifier.glassDark(): Modifier = glassmorphism(
    backgroundColor = AppColors.SurfaceDark,
    borderColor = AppColors.SurfaceDarkBorder,
    cornerRadius = DesignTokens.radiusLarge,
    elevation = DesignTokens.elevationMedium
)

/**
 * Pill-shaped glassmorphism.
 * Perfect for floating toolbars.
 */
fun Modifier.glassPill(
    backgroundColor: Color,
    isDark: Boolean = false
): Modifier = glassmorphism(
    backgroundColor = backgroundColor,
    borderColor = if (isDark) AppColors.SurfaceDarkBorder else AppColors.SurfaceLightBorder,
    cornerRadius = DesignTokens.radiusFull,
    elevation = DesignTokens.elevationSmall
)

/**
 * Heavy glass effect for prominent panels.
 * More opaque with stronger blur simulation.
 */
fun Modifier.glassHeavy(
    isDark: Boolean = false
): Modifier = glassmorphism(
    backgroundColor = if (isDark) Color(0xE62C2C2E) else Color(0xFAFFFFFF),
    borderColor = if (isDark) Color(0x1AFFFFFF) else Color(0x1A000000),
    cornerRadius = DesignTokens.radiusXLarge,
    elevation = DesignTokens.elevationLarge
)
