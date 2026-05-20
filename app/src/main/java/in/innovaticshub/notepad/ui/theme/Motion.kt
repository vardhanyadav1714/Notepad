package `in`.innovaticshub.notepad.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Motion design tokens for premium feel.
 * Inspired by Apple's Human Interface Guidelines and Material Motion.
 */
object MotionTokens {

    // ========================================================================
    // DURATION TOKENS (ms)
    // ========================================================================
    const val DurationInstant = 50
    const val DurationFast = 150
    const val DurationMedium = 250
    const val DurationSlow = 400
    const val DurationSlower = 600

    // ========================================================================
    // EASING CURVES
    // ========================================================================

    /**
     * Apple-style ease out - quick start, smooth deceleration
     * Use for: Elements entering screen, expanding
     */
    val EaseOutCubic = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    /**
     * Smooth acceleration and deceleration
     * Use for: State transitions, color changes
     */
    val EaseInOutCubic = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)

    /**
     * Quick entry, immediate stop
     * Use for: Micro-interactions, button presses
     */
    val EaseOutQuart = CubicBezierEasing(0.18f, 0.89f, 0.32f, 1.28f)

    // ========================================================================
    // SPRING SPECIFICATIONS
    // ========================================================================

    /**
     * Bouncy spring - for playful interactions
     * Use for: Tool selection, icon press
     */
    val SpringBouncy = SpringSpec<Float>(
        dampingRatio = 0.7f,
        stiffness = 350f,
    )

    /**
     * Smooth spring - natural movement
     * Use for: Panel expansion, menu reveal
     */
    val SpringSmooth = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 400f,
    )

    /**
     * Snappy spring - quick, controlled
     * Use for: Toggle switches, checkboxes
     */
    val SpringSnappy = spring<Float>(
        dampingRatio = 0.95f,
        stiffness = 500f,
    )

    // ========================================================================
    // STAGGER TOKENS
    // ========================================================================

    /**
     * Stagger delay for sequential animations
     * Creates cascading reveal effect
     */
    const val StaggerDelay = 30L // ms between items

    /**
     * Initial stagger offset
     */
    const val StaggerInitial = 0L
}
