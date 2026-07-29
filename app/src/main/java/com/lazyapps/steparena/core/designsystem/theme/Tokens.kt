package com.lazyapps.steparena.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StepArenaColors {
    val Navy950 = Color(0xFF050A18)
    val Navy900 = Color(0xFF091224)
    val BlueBlack = Color(0xFF0C172B)
    val Gray800 = Color(0xFF1A2638)
    val Cyan = Color(0xFF54E7FF)
    val CyanSoft = Color(0xFF9DF2FF)
    val Violet = Color(0xFFA78BFA)
    val Emerald = Color(0xFF44E3B5)
    val Amber = Color(0xFFFFCE6A)
    val Error = Color(0xFFFF8295)
    val White = Color(0xFFF5F8FF)
    val TextSecondary = Color(0xFFB5C0D4)
    val Outline = Color(0xFF31415D)
    val Glass = Color(0xD9162238)
}

object StepArenaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val minimumTouchTarget = 48.dp
}

object StepArenaShapes {
    val values = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
    )
}

object StepArenaElevation {
    val resting = 2.dp
    val floating = 8.dp
    val prominent = 14.dp
}

object StepArenaGlow {
    val subtle = StepArenaColors.Cyan.copy(alpha = 0.16f)
    val active = StepArenaColors.Cyan.copy(alpha = 0.34f)
    val victory = StepArenaColors.Emerald.copy(alpha = 0.32f)
}

object StepArenaMotion {
    const val quick = 140
    const val standard = 300
    const val expressive = 520
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

val StepArenaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
)
