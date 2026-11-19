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

/**
 * Upstream navigation behavior is preserved; only the drawer sheet geometry
 * is supplied by the same appearance source used by all other YUI surfaces.
 */
@Composable
fun YUiModalDrawerSheet(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalDrawerSheet(
        modifier = modifier,
        drawerShape = RoundedCornerShape(LocalYAppearance.current.navRadiusDp.dp),
        content = content,
    )
}

@Composable
fun YUiModalNavigationDrawer(
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: androidx.compose.material3.DrawerState =
        androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    scrimColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.DrawerDefaults.scrimColor,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.ModalNavigationDrawer(
        drawerContent = drawerContent,
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        scrimColor = scrimColor,
        content = content,
    )
}

/** Upstream overload supports predictive-back and shares the same drawer state. */
@Composable
fun YUiModalDrawerSheet(
    drawerState: androidx.compose.material3.DrawerState,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.ModalDrawerSheet(
        drawerState = drawerState,
        modifier = modifier,
        drawerShape = RoundedCornerShape(LocalYAppearance.current.navRadiusDp.dp),
        content = content,
    )
}
