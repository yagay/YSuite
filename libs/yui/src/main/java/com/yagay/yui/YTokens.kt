package com.yagay.yui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors are intentionally separate from the product accent color. */
@Immutable
data class YSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

internal object YSemanticPalette {
    val Light = YSemanticColors(
        success = Color(0xFF146C2E),
        onSuccess = Color.White,
        successContainer = Color(0xFFB7F2C4),
        onSuccessContainer = Color(0xFF00210A),
        warning = Color(0xFF8A4D00),
        onWarning = Color.White,
        warningContainer = Color(0xFFFFDDB8),
        onWarningContainer = Color(0xFF2C1600),
        info = Color(0xFF285F9E),
        onInfo = Color.White,
        infoContainer = Color(0xFFD5E3FF),
        onInfoContainer = Color(0xFF001B3C),
    )

    val Dark = YSemanticColors(
        success = Color(0xFF9BDAA8),
        onSuccess = Color(0xFF003915),
        successContainer = Color(0xFF005321),
        onSuccessContainer = Color(0xFFB7F2C4),
        warning = Color(0xFFFFB95F),
        onWarning = Color(0xFF492900),
        warningContainer = Color(0xFF693900),
        onWarningContainer = Color(0xFFFFDDB8),
        info = Color(0xFFA7C8FF),
        onInfo = Color(0xFF003062),
        infoContainer = Color(0xFF08477C),
        onInfoContainer = Color(0xFFD5E3FF),
    )
}

internal val LocalYSemanticColors = staticCompositionLocalOf { YSemanticPalette.Light }

@Composable
@ReadOnlyComposable
fun ySemanticColors(): YSemanticColors = LocalYSemanticColors.current

/** Shared motion timing. Modules should not invent their own normal-screen durations. */
object YMotion {
    const val Fast = 120
    const val Standard = 220
    const val Emphasized = 320
}

/** Shared accessibility constants for Compose and compatibility layers. */
object YAccessibility {
    val MinimumTouchTarget get() = YDimens.TouchTarget
}
