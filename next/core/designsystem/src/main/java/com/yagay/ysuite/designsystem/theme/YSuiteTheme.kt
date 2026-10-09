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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF315DA8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001A42),
        secondary = Color(0xFF526273),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6E4F7),
        onSecondaryContainer = Color(0xFF0E1D2A),
        tertiary = Color(0xFF6B5778),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF2DAFF),
        onTertiaryContainer = Color(0xFF251431),
        background = Color(0xFFF9F9FC),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFF9F9FC),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE1E2E8),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA9C7FF),
        onPrimary = Color(0xFF003063),
        primaryContainer = Color(0xFF16477E),
        onPrimaryContainer = Color(0xFFD9E2FF),
        secondary = Color(0xFFBAC8DA),
        onSecondary = Color(0xFF253140),
        secondaryContainer = Color(0xFF3B4857),
        onSecondaryContainer = Color(0xFFD6E4F7),
        tertiary = Color(0xFFD6BEE4),
        onTertiary = Color(0xFF3B2947),
        tertiaryContainer = Color(0xFF523F5F),
        onTertiaryContainer = Color(0xFFF2DAFF),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474F),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )


@Composable
fun YSuiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current

    SideEffect {
        if (!view.isInEditMode) {
            val activity =
                view.context as? Activity
            activity?.window?.let { window ->
                WindowCompat
                    .getInsetsController(
                        window,
                        view,
                    )
                    .apply {
                        isAppearanceLightStatusBars =
                            !darkTheme
                        isAppearanceLightNavigationBars =
                            !darkTheme
                    }
                if (Build.VERSION.SDK_INT >= 29) {
                    window.isStatusBarContrastEnforced =
                        false
                    window.isNavigationBarContrastEnforced =
                        false
                }
            }
        }
    }

    val colorScheme =
        remember(
            context,
            darkTheme,
            dynamicColorEnabled,
        ) {
            if (
                dynamicColorEnabled &&
                Build.VERSION.SDK_INT >= 31
            ) {
                if (darkTheme) {
                    dynamicDarkColorScheme(
                        context,
                    )
                } else {
                    dynamicLightColorScheme(
                        context,
                    )
                }
            } else if (darkTheme) {
                DarkColors
            } else {
                LightColors
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YSuiteTypography,
        shapes = YSuiteShapes,
        content = content,
    )
}
