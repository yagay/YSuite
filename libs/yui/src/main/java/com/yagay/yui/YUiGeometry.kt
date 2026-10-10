package com.yagay.yui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * YUI's single geometry authority for adaptive product pages and native overlays.
 * Feature modules may own content and hit-testing, never visual sizes or spacing.
 * Structural ratios are YUI defaults; user appearance settings scale the results.
 */
object YUiGeometry {
    val dashboardContentBase: Dp get() = YDimens.ContentMaxWidth
    val toolContentBase: Dp get() = YDimens.ContentMaxWidth * 0.6875f
    val detailContentBase: Dp get() = YDimens.ContentMaxWidth * 0.703125f
    val navigationPaneBase: Dp get() = YDimens.ListDetailListWidth * (7f / 9f)
    val compactPaneBase: Dp get() = YDimens.ListDetailListWidth * (2f / 3f)
    val detailPaneBase: Dp get() = YDimens.ListDetailListWidth

    @Composable fun contentWidth(base: Dp): Dp =
        base * (LocalYAppearance.current.contentWidthPercent / 100f)

    @Composable fun paneWidth(base: Dp): Dp =
        base * (LocalYAppearance.current.paneWidthPercent / 100f)

    @Composable fun gap(): Dp = LocalYAppearance.current.effectiveGapDp.dp
    @Composable fun compactGap(): Dp = (LocalYAppearance.current.effectiveGapDp / 2f).dp
    @Composable fun tinyGap(): Dp = (LocalYAppearance.current.effectiveGapDp / 4f).dp
    @Composable fun pagePadding(): Dp = LocalYAppearance.current.screenPaddingDp.dp
    @Composable fun pageVerticalPadding(): Dp = LocalYAppearance.current.pageVerticalPaddingDp.dp
    @Composable fun sectionGap(): Dp = LocalYAppearance.current.sectionSpacingDp.dp
    @Composable fun cardElevation(): Dp =
        (LocalYAppearance.current.cardOutlineWidthDp / 2f).dp

    /** Correctly scales with font, content density and the visible action's label. */
    @JvmStatic fun overlayActionWidthPx(context: Context, moduleId: String, measuredTextPx: Float): Int =
        maxOf(
            YOverlayUi.actionHeightPx(context, moduleId),
            measuredTextPx.toInt() + 2 * YOverlayUi.contentPaddingPx(context, moduleId),
        )

    @JvmStatic fun overlayMenuInsetPx(context: Context, moduleId: String): Int =
        YOverlayUi.contentPaddingPx(context, moduleId)

    @JvmStatic fun overlayCloseSizePx(context: Context, moduleId: String): Int =
        YOverlayUi.actionHeightPx(context, moduleId)

    @JvmStatic fun overlayHandleRadiusPx(context: Context, moduleId: String): Float {
        val config = YOverlayUi.appearance(context, moduleId)
        return maxOf(1f, config.iconVisualSizeDp / 4f) *
            context.resources.displayMetrics.density
    }

    @JvmStatic fun overlayResultWidthPx(context: Context, moduleId: String, availablePx: Int): Int {
        val config = YOverlayUi.appearance(context, moduleId)
        val density = context.resources.displayMetrics.density
        val inset = overlayMenuInsetPx(context, moduleId)
        val limit = (availablePx - inset * 2).coerceAtLeast(1)
        val preferred = (config.rowHeightDp * 7f * density *
            config.contentWidthPercent / 100f).toInt()
        return preferred.coerceIn(1, limit)
    }

    @JvmStatic fun overlayResultHeightPx(context: Context, moduleId: String, availablePx: Int): Int {
        val config = YOverlayUi.appearance(context, moduleId)
        val density = context.resources.displayMetrics.density
        val inset = overlayMenuInsetPx(context, moduleId)
        val limit = (availablePx - inset * 2).coerceAtLeast(1)
        val preferred = (config.rowHeightDp * 8f * density *
            config.contentWidthPercent / 100f).toInt()
        return preferred.coerceIn(1, limit)
    }
}
