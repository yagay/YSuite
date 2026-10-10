package com.yagay.yui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The host chooses navigation destinations; YUI owns the normal drawer item visual contract. */
@Composable
fun YUiNavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    NavigationDrawerItem(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        badge = badge,
        shape = RoundedCornerShape(LocalYAppearance.current.navRadiusDp.dp),
    )
}
