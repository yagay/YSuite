package com.yagay.yui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Unified overlay *appearance* shared by Java/Canvas/WindowManager and Compose.
 * Window ownership, gesture capture, input hit testing and business actions stay
 * inside feature modules. All configurable dimensions come from YAppearanceStore.
 */
object YOverlayUi {
    @JvmStatic fun appearance(context: Context, moduleId: String): YAppearance =
        YAppearanceStore(context).appearance(moduleId)

    @JvmStatic fun cornerRadiusPx(context: Context, moduleId: String): Float =
        YOverlayTokens.dp(context, appearance(context, moduleId).overlayPanelRadiusDp.toFloat()).toFloat()

    @JvmStatic fun contentPaddingPx(context: Context, moduleId: String): Int =
        YOverlayTokens.dp(context, appearance(context, moduleId).overlayContentPaddingDp.toFloat())

    @JvmStatic fun surfaceColor(context: Context, moduleId: String, color: Int): Int {
        val alpha = Color.alpha(color) * appearance(context, moduleId).overlaySurfaceAlpha / 100
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    @JvmStatic fun scaleScrimAlpha(context: Context, moduleId: String, alpha: Int): Int =
        ((alpha.coerceIn(0, 255) * appearance(context, moduleId).overlayScrimScale) / 100)
            .coerceIn(0, 255)

    @JvmStatic fun menuBackground(context: Context, moduleId: String, surface: Int): GradientDrawable =
        panelBackground(context, moduleId, surface, null)

    @JvmStatic fun panelBackground(
        context: Context, moduleId: String, surface: Int, outline: Int?,
    ): GradientDrawable {
        val config = appearance(context, moduleId)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(surfaceColor(context, moduleId, surface))
            cornerRadius = YOverlayTokens.dp(context, config.overlayPanelRadiusDp.toFloat()).toFloat()
            if (outline != null && config.overlayOutlineWidthDp > 0) {
                setStroke(YOverlayTokens.dp(context, config.overlayOutlineWidthDp.toFloat()), outline)
            }
        }
    }
}

/** Compose counterpart to the native window/overlay panel facade. */
@Composable
fun YUiOverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalYAppearance.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(config.overlayPanelRadiusDp.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = config.overlaySurfaceAlpha / 100f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (config.overlayOutlineWidthDp > 0)
            BorderStroke(config.overlayOutlineWidthDp.dp, MaterialTheme.colorScheme.outline)
        else null,
    ) {
        Column(modifier = Modifier.padding(config.overlayContentPaddingDp.dp), content = content)
    }
}
