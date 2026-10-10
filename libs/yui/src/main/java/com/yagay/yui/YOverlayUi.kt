package com.yagay.yui

import android.content.Context
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.widget.TextView
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
    private val scopedContexts = WeakHashMap<Context, MutableMap<String, WeakReference<Context>>>()

    /** Shared module-aware theme context, cache weakly to avoid retaining Activity or Service. */
    @JvmStatic fun scopedContext(context: Context, moduleId: String): Context =
        synchronized(scopedContexts) {
            val scopes = scopedContexts.getOrPut(context) { mutableMapOf() }
            scopes[moduleId]?.get() ?: YAppearanceScopeContext(context, moduleId).also {
                scopes[moduleId] = WeakReference(it)
            }
        }

    @JvmStatic fun appearance(context: Context, moduleId: String): YAppearance =
        YAppearanceStore(context).appearance(moduleId)

    @JvmStatic fun cornerRadiusPx(context: Context, moduleId: String): Float =
        YOverlayTokens.dp(context, appearance(context, moduleId).overlayPanelRadiusDp.toFloat()).toFloat()

    @JvmStatic fun contentPaddingPx(context: Context, moduleId: String): Int =
        YOverlayTokens.dp(context, appearance(context, moduleId).overlayContentPaddingDp.toFloat())

    @JvmStatic fun elevationPx(context: Context, moduleId: String): Float =
        YOverlayTokens.dp(context, appearance(context, moduleId).overlayShadowElevationDp.toFloat()).toFloat()

    @JvmStatic fun actionHeightPx(context: Context, moduleId: String): Int {
        val config = appearance(context, moduleId)
        val density = context.resources.displayMetrics.density
        return maxOf(48, config.iconTouchTargetDp, config.buttonHeightDp).times(density).toInt()
    }

    /** Shared native overlay typography and padding, preserving Android text selection. */
    @JvmStatic fun styleSelectionText(view: TextView, moduleId: String) {
        YView.styleBody(view)
        view.setTextColor(YOverlayTokens.textPrimary(scopedContext(view.context, moduleId)))
        val inset = contentPaddingPx(view.context, moduleId)
        view.setPadding(inset, maxOf(1, inset / 2), inset, maxOf(1, inset / 2))
    }

    @JvmStatic fun styleHintText(view: TextView, moduleId: String) {
        val context = view.context
        YView.styleCaption(view)
        val themed = scopedContext(context, moduleId)
        view.setTextColor(YOverlayTokens.textPrimary(themed))
        view.background = menuBackground(context, moduleId, YOverlayTokens.surface(themed))
        val inset = contentPaddingPx(context, moduleId)
        view.setPadding(inset, 0, inset, 0)
    }

    @JvmStatic fun styleAction(view: TextView, moduleId: String, surface: Int, outline: Int) {
        val context = view.context
        YView.styleStrongBody(view)
        view.setTextColor(YOverlayTokens.textPrimary(scopedContext(context, moduleId)))
        view.minHeight = actionHeightPx(context, moduleId)
        view.background = panelBackground(context, moduleId, surface, outline)
    }

    @JvmStatic fun actionRipple(context: Context, moduleId: String): RippleDrawable {
        val normal = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = cornerRadiusPx(context, moduleId)
        }
        return RippleDrawable(ColorStateList.valueOf(YOverlayTokens.menuRipple(scopedContext(context, moduleId))), normal, null)
    }

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
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = config.overlaySurfaceAlpha / 100f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = config.overlayShadowElevationDp.dp,
        border = if (config.overlayOutlineWidthDp > 0)
            BorderStroke(config.overlayOutlineWidthDp.dp, MaterialTheme.colorScheme.outline)
        else null,
    ) {
        Column(modifier = Modifier.padding(config.overlayContentPaddingDp.dp), content = content)
    }
}
