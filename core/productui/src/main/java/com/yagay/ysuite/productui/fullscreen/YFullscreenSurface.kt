package com.yagay.ysuite.productui.fullscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveBox
import com.yagay.ysuite.productui.ProductAdaptiveInfo

/**
 * Edge-to-edge product surface for media, preview, terminal, remote desktop and
 * other experiences where chrome is intentionally owned by the content.
 */
@Composable
fun YFullscreenSurface(
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.(ProductAdaptiveInfo) -> Unit = {},
    content: @Composable BoxScope.(ProductAdaptiveInfo) -> Unit,
) {
    ProductAdaptiveBox(modifier = modifier.fillMaxSize()) { adaptive ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(adaptive)
            overlay(adaptive)
        }
    }
}
