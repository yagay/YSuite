package com.yagay.yui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Feature chips, alternate buttons and standard rows delegate to upstream Material3.
 * Only the live appearance geometry is owned by YUI; feature state and callbacks
 * remain in the calling module.
 */
@Composable
fun YUiElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val appearance = LocalYAppearance.current
    ElevatedButton(onClick = onClick, modifier = modifier.heightIn(min = appearance.buttonHeightDp.dp),
        enabled = enabled, shape = RoundedCornerShape(appearance.buttonRadiusDp.dp),
        contentPadding = PaddingValues(horizontal = appearance.buttonPaddingHorizontalDp.dp,
            vertical = appearance.buttonVerticalPaddingDp.dp), content = content)
}

@Composable
fun YUiFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val appearance = LocalYAppearance.current
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = appearance.buttonHeightDp.dp),
        enabled = enabled, shape = RoundedCornerShape(appearance.buttonRadiusDp.dp),
        contentPadding = PaddingValues(horizontal = appearance.buttonPaddingHorizontalDp.dp,
            vertical = appearance.buttonVerticalPaddingDp.dp), content = content)
}

@Composable
fun YUiAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        shape = RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp))
}

@Composable
fun YUiSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    SuggestionChip(onClick = onClick, label = label, modifier = modifier, enabled = enabled,
        icon = icon, shape = RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp))
}

@Composable
fun YUiInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    avatar: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    InputChip(selected = selected, onClick = onClick, label = label, modifier = modifier,
        enabled = enabled, avatar = avatar, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, shape = RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp))
}

@Composable
fun YUiListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(headlineContent = headlineContent,
        modifier = modifier.heightIn(min = LocalYAppearance.current.rowHeightDp.dp),
        overlineContent = overlineContent, supportingContent = supportingContent,
        leadingContent = leadingContent, trailingContent = trailingContent)
}
