package com.yagay.yui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Material 3's upstream Switch is now the only renderer for YSuite's Compose toggles.
 *
 * The graphics layer shrinks its *painted* geometry to match the existing compact setting
 * rows without rewriting the track/thumb colors or animations. Switch still owns state,
 * semantics, colors and animation; the surrounding row retains its own touch target.
 */
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.size(
            width = YDimens.SwitchSlotWidth,
            height = YDimens.SwitchSlotHeight,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.graphicsLayer(scaleX = 0.75f, scaleY = 0.75f),
        )
    }
}
