package com.yagay.yui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose adapter for YUiMaterialSwitch, the same View instance used by Java
 * modules. This file does not draw or implement a second switch widget.
 */
@Composable
fun YStandardSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val appearance = LocalYAppearance.current
    val geometry = appearance.switchGeometry()
    val colors = MaterialTheme.colorScheme
    val paintColors = YSwitchPaintColors(
        checkedTrack = colors.primary.toArgb(),
        uncheckedTrack = colors.surfaceContainerHighest.toArgb(),
        checkedThumb = colors.onPrimary.toArgb(),
        uncheckedThumb = colors.onSurfaceVariant.toArgb(),
        outline = colors.outline.toArgb(),
    )

    AndroidView(
        modifier = modifier
            .padding(start = appearance.switchLabelGapDp.dp)
            .defaultMinSize(
                minWidth = geometry.touchWidthDp.dp,
                minHeight = geometry.touchHeightDp.dp,
            ),
        factory = { context -> YUiMaterialSwitch(context) },
        update = { toggle ->
            toggle.bindPresentation(appearance, paintColors)
            // Keep Compose as the single source of checked state; never notify
            // the feature when the host recomposes an externally changed value.
            toggle.setOnCheckedChangeListener(null)
            if (toggle.isChecked != checked) toggle.isChecked = checked
            toggle.isEnabled = enabled
            toggle.setOnCheckedChangeListener { _, value -> onCheckedChange(value) }
        },
    )
}

@Deprecated("Use YStandardSwitch.")
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = YStandardSwitch(checked, onCheckedChange, modifier, enabled)
