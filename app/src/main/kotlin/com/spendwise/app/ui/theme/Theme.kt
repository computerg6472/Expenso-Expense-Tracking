package com.spendwise.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Tertiary,
    error = DueRose,
    onError = Color.White,
    background = BackgroundLight,
    onBackground = TextLightPrimary,
    surface = SurfaceLight,
    onSurface = TextLightPrimary,
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = TextLightSecondary
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFEEF2FF),
    secondary = CreditBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0369A1),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Tertiary,
    error = DueRose,
    onError = Color.White,
    background = BackgroundDark,
    onBackground = TextDarkPrimary,
    surface = SurfaceDark,
    onSurface = TextDarkPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextDarkSecondary
)

@Composable
fun SpendWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
