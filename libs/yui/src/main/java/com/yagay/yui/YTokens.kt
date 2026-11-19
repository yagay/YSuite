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
        success = YUiPalette.LightSuccess,
        onSuccess = YUiPalette.LightOnSuccess,
        successContainer = YUiPalette.LightSuccessContainer,
        onSuccessContainer = YUiPalette.LightOnSuccessContainer,
        warning = YUiPalette.LightWarning,
        onWarning = YUiPalette.LightOnWarning,
        warningContainer = YUiPalette.LightWarningContainer,
        onWarningContainer = YUiPalette.LightOnWarningContainer,
        info = YUiPalette.LightInfo,
        onInfo = YUiPalette.LightOnInfo,
        infoContainer = YUiPalette.LightInfoContainer,
        onInfoContainer = YUiPalette.LightOnInfoContainer,
    )

    val Dark = YSemanticColors(
        success = YUiPalette.DarkSuccess,
        onSuccess = YUiPalette.DarkOnSuccess,
        successContainer = YUiPalette.DarkSuccessContainer,
        onSuccessContainer = YUiPalette.DarkOnSuccessContainer,
        warning = YUiPalette.DarkWarning,
        onWarning = YUiPalette.DarkOnWarning,
        warningContainer = YUiPalette.DarkWarningContainer,
        onWarningContainer = YUiPalette.DarkOnWarningContainer,
        info = YUiPalette.DarkInfo,
        onInfo = YUiPalette.DarkOnInfo,
        infoContainer = YUiPalette.DarkInfoContainer,
        onInfoContainer = YUiPalette.DarkOnInfoContainer,
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
