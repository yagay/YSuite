package com.yagay.yui

import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** A single upstream Material3 divider entry point. */
@Composable
fun YUiHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}
