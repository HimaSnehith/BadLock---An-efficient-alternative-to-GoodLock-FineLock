package com.dark.badlock.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OneUiDarkColorScheme = darkColorScheme(
    primary = OneUiBlue,
    onPrimary = Color.White,
    primaryContainer = OneUiBlueContainer,
    onPrimaryContainer = OneUiBlueLight,
    secondary = OneUiGreen,
    onSecondary = Color.Black,
    secondaryContainer = OneUiGreenContainer,
    onSecondaryContainer = OneUiGreen,
    tertiary = OneUiAmber,
    onTertiary = Color.Black,
    tertiaryContainer = OneUiAmberContainer,
    onTertiaryContainer = OneUiAmber,
    background = OneUiDarkBg,
    onBackground = OneUiTextPrimary,
    surface = OneUiDarkBg,
    onSurface = OneUiTextPrimary,
    surfaceVariant = OneUiSlateLow,
    onSurfaceVariant = OneUiTextSecondary,
    surfaceContainerLowest = OneUiDarkBg,
    surfaceContainerLow = OneUiSlateLow,
    surfaceContainer = OneUiSlateMedium,
    surfaceContainerHigh = OneUiSlateHigh,
    surfaceContainerHighest = OneUiSlateHighest,
    outline = OneUiBorder,
    outlineVariant = OneUiBorderSubtle
)

private val OneUiLightColorScheme = lightColorScheme(
    primary = OneUiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = OneUiGreen,
    onSecondary = Color.White,
    tertiary = OneUiAmber,
    onTertiary = Color.White,
    background = OneUiLightBg,
    onBackground = Color(0xFF0F172A),
    surface = OneUiLightCard,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = OneUiLightCard,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = OneUiLightBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun BadlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OneUiDarkColorScheme
        else -> OneUiLightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
