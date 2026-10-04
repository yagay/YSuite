package com.yagay.ysuite.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    secondary = Color(0xFF526273),
    background = Color(0xFFF9F9FC),
    surface = Color(0xFFF9F9FC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    secondary = Color(0xFFBAC8DA),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
)

@Composable
fun YSuiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
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
    val colorScheme =
        if (
            dynamicColorEnabled &&
            Build.VERSION.SDK_INT >= 31
        ) {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        } else if (darkTheme) {
            DarkColors
        } else {
            LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YSuiteTypography,
        shapes = YSuiteShapes,
        content = content,
    )
}
