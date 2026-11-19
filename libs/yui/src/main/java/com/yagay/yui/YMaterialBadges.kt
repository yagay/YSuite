package com.yagay.yui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Badge semantics and theme come from upstream Material3. */
@Composable
fun YUiBadge(modifier: Modifier = Modifier, content: (@Composable RowScope.() -> Unit)? = null) {
    Badge(modifier = modifier, content = content)
}

@Composable
fun YUiBadgedBox(
    badge: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BadgedBox(badge = badge, modifier = modifier, content = content)
}
