package `in`.innovaticshub.notepad.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system tokens for premium UI.
 * Based on 8dp base unit, inspired by Apple's HIG and Material 3.
 */
object DesignTokens {

    // ========================================================================
    // SPACING (based on 8dp grid)
    // ========================================================================
    val spacing0 = 0.dp
    val spacing2 = 2.dp
    val spacing4 = 4.dp
    val spacing8 = 8.dp
    val spacing12 = 12.dp
    val spacing16 = 16.dp
    val spacing20 = 20.dp
    val spacing24 = 24.dp
    val spacing32 = 32.dp
    val spacing40 = 40.dp
    val spacing48 = 48.dp
    val spacing64 = 64.dp
    val spacing96 = 96.dp
    val spacing128 = 128.dp

    // ========================================================================
    // BORDERS & RADIUS
    // ========================================================================
    val radiusXSmall = 4.dp
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
    val radiusXLarge = 20.dp
    val radiusXXLarge = 24.dp
    val radiusFull = 999.dp // Pill shape

    // Border widths
    val borderHairline = 0.5.dp
    val borderThin = 1.dp
    val borderMedium = 2.dp
    val borderThick = 4.dp

    // ========================================================================
    // ELEVATION (dp) - for shadow elevation
    // ========================================================================
    val elevationNone = 0.dp
    val elevationXSmall = 1.dp
    val elevationSmall = 2.dp
    val elevationMedium = 4.dp
    val elevationLarge = 8.dp
    val elevationXLarge = 12.dp
    val elevationXXLarge = 16.dp

    // ========================================================================
    // SIZES
    // ========================================================================
    // Touch targets (WCAG minimum: 44x44dp)
    val touchTargetMinimum = 44.dp
    val touchTargetComfortable = 48.dp

    // Icons
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXLarge = 48.dp

    // Toolbars
    val toolbarHeightCompact = 48.dp
    val toolbarHeightDefault = 56.dp
    val toolbarHeightComfortable = 64.dp

    // Floating elements
    val floatingMargin = 24.dp     // Margin from screen edges
    val floatingPaddingH = 16.dp   // Horizontal padding inside
    val floatingPaddingV = 8.dp    // Vertical padding inside

    // Canvas
    val canvasGridSize = 50.dp
    val canvasDotSize = 1.dp

    // ========================================================================
    // TYPOGRAPHY (scale factors relative to base)
    // ========================================================================
    // Note: Actual type defined in Theme.kt, these are sizing guides
    const val fontSizeDisplayLarge = 57f
    const val fontSizeDisplayMedium = 45f
    const val fontSizeDisplaySmall = 36f

    const val fontSizeHeadlineLarge = 32f
    const val fontSizeHeadlineMedium = 28f
    const val fontSizeHeadlineSmall = 24f

    const val fontSizeTitleLarge = 22f
    const val fontSizeTitleMedium = 16f
    const val fontSizeTitleSmall = 14f

    const val fontSizeBodyLarge = 17f
    const val fontSizeBodyMedium = 15f
    const val fontSizeBodySmall = 13f

    const val fontSizeLabelLarge = 14f
    const val fontSizeLabelMedium = 12f
    const val fontSizeLabelSmall = 11f

    // Line height ratios
    const val lineHeightTight = 1.2f
    const val lineHeightNormal = 1.5f
    const val lineHeightRelaxed = 1.7f

    // ========================================================================
    // OPACITY (for subtle overlays and disabled states)
    // ========================================================================
    const val opacityFull = 1.0f
    const val opacityHigh = 0.87f
    const val opacityMedium = 0.6f
    const val opacityLow = 0.38f
    const val opacityVeryLow = 0.12f

    // Glassmorphism opacity
    const val glassLight = 0.4f      // Light mode surface
    const val glassDark = 0.6f       // Dark mode surface
    const val glassBorder = 0.08f    // Border opacity
}
