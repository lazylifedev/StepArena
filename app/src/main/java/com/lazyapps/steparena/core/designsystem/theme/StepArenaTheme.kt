package com.lazyapps.steparena.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = StepArenaColors.Cyan,
    onPrimary = StepArenaColors.Navy950,
    primaryContainer = StepArenaColors.Gray800,
    onPrimaryContainer = StepArenaColors.CyanSoft,
    secondary = StepArenaColors.Violet,
    tertiary = StepArenaColors.Emerald,
    background = StepArenaColors.Navy950,
    onBackground = StepArenaColors.White,
    surface = StepArenaColors.BlueBlack,
    onSurface = StepArenaColors.White,
    surfaceVariant = StepArenaColors.Gray800,
    onSurfaceVariant = StepArenaColors.TextSecondary,
    outline = StepArenaColors.Outline,
    error = StepArenaColors.Error,
)

private val LightColorScheme = lightColorScheme(
    primary = StepArenaColors.BlueBlack,
    onPrimary = StepArenaColors.White,
    secondary = StepArenaColors.Violet,
    tertiary = StepArenaColors.Emerald,
    background = StepArenaColors.White,
    onBackground = StepArenaColors.Navy950,
    surface = StepArenaColors.White,
    onSurface = StepArenaColors.Navy950,
    outline = StepArenaColors.Outline,
    error = StepArenaColors.Error,
)

@Composable
fun StepArenaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = StepArenaTypography,
        shapes = StepArenaShapes.values,
        content = content,
    )
}
