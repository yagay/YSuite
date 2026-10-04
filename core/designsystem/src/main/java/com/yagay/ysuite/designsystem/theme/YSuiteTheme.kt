package com.yagay.ysuite.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B63CE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF5A6070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E5F5),
    onSecondaryContainer = Color(0xFF171B26),
    tertiary = Color(0xFF6A5792),
    tertiaryContainer = Color(0xFFECDDFF),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF171A20),
    surface = Color(0xFFF5F7FB),
    surfaceVariant = Color(0xFFE3E7EF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F8),
    surfaceContainer = Color(0xFFEAEFF6),
    surfaceContainerHigh = Color(0xFFE4E9F1),
    surfaceContainerHighest = Color(0xFFDDE3EC),
    outline = Color(0xFF747983),
    outlineVariant = Color(0xFFC4C9D2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468D),
    onPrimaryContainer = Color(0xFFD9E7FF),
    secondary = Color(0xFFC3C8D8),
    onSecondary = Color(0xFF2C303B),
    secondaryContainer = Color(0xFF424754),
    onSecondaryContainer = Color(0xFFDFE3F3),
    tertiary = Color(0xFFD5BAFF),
    tertiaryContainer = Color(0xFF513E78),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE1E3E9),
    surface = Color(0xFF101318),
    surfaceVariant = Color(0xFF44474F),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF181B20),
    surfaceContainer = Color(0xFF1C2025),
    surfaceContainerHigh = Color(0xFF272A30),
    surfaceContainerHighest = Color(0xFF32353B),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

@Composable
fun YSuiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as? Activity
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= 29) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = YSuiteTypography,
        shapes = YSuiteShapes,
        content = content,
    )
}
