package com.yagay.yui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

sealed interface YPageState {
    data object Ready : YPageState
    data class Loading(val message: String? = null) : YPageState
    data class Empty(val message: String) : YPageState
    data class Error(val message: String) : YPageState
}

@Composable
fun YPluginScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    YPageScaffold(
        title = title,
        role = YPageRole.LIST,
        modifier = modifier,
        subtitle = subtitle,
        state = state,
        actions = actions,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun YPluginList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    YPageList(
        padding = padding,
        modifier = modifier,
        content = content,
    )
}

/** Compatibility alias for pre-v2 feature sources. */
@Deprecated("Use YHorizontalActions")
@Composable
fun YActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) = YHorizontalActions(modifier, content)

@Composable
fun YPluginHeader(
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap)) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
