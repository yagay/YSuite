package com.yagay.yui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Standard-size Material 3 switch. YUI owns only the surrounding accessible slot. */
@Composable
fun YStandardSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier.size(
            width = LocalYAppearance.current.switchSlotWidthDp.dp,
            height = YDimens.SwitchSlotHeight,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** Binary compatibility alias; it no longer paints a compact switch. */
@Deprecated("Use YStandardSwitch: compact density has been removed.")
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = YStandardSwitch(checked, onCheckedChange, modifier, enabled)
