package com.yagay.yui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Stable Material3 navigation semantics; YTheme is the sole style source. */
@Composable
fun YUiNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(modifier = modifier, content = content)
}

@Composable
fun RowScope.YUiNavigationBarItem(
    selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null, alwaysShowLabel: Boolean = true,
) {
    NavigationBarItem(selected = selected, onClick = onClick, icon = icon, modifier = modifier,
        enabled = enabled, label = label, alwaysShowLabel = alwaysShowLabel)
}

@Composable
fun YUiNavigationRail(
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(modifier = modifier, header = header, content = content)
}

@Composable
fun ColumnScope.YUiNavigationRailItem(
    selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    label: (@Composable () -> Unit)? = null, alwaysShowLabel: Boolean = true,
) {
    NavigationRailItem(selected = selected, onClick = onClick, icon = icon, modifier = modifier,
        enabled = enabled, label = label, alwaysShowLabel = alwaysShowLabel)
}
