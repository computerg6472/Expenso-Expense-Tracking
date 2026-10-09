package com.spendwise.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized motion and visual specs for Expenso navigation and polished UI components.
 */
object ExpensoMotionDefaults {
    // Motion Spring Specs
    val IndicatorSpring = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow
    )

    val IconScaleSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )

    const val TabTransitionDurationMs = 300
    const val ScreenTransitionDurationMs = 300

    // Geometry & Layout
    val BarHeight = 64.dp
    val BarCornerRadius = 24.dp
    val ActiveCapsuleHeight = 32.dp
    val ActiveCapsuleWidth = 52.dp
    val ActiveCapsuleRadius = 16.dp
    val MinTouchTarget = 48.dp

    // Static Palette
    val InactiveGray = Color(0xFF8E8E93)
    val ActivePrimary = Color(0xFF2563EB)
    val CapsuleLightTint = Color(0xFF2563EB).copy(alpha = 0.12f)
    val CapsuleDarkTint = Color(0xFF3B82F6).copy(alpha = 0.22f)
    val HairlineLight = Color(0x0F000000) // ~6% black hairline
    val HairlineDark = Color(0x1FFFFFFF)  // ~12% white hairline
    val PageBackgroundNeutral = Color(0xFFF5F6F8)

    // Typography
    val LabelFontSize = 11.sp
}
