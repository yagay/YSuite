package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Standard page states shared by every YSuite plugin.
 *
 * Plugins own data and business rules; YUI owns the visual treatment of loading, empty and error
 * states so feature screens do not reimplement those patterns independently.
 */
sealed interface YPageState {
    data object Ready : YPageState
    data class Loading(val message: String? = null) : YPageState
    data class Empty(val message: String) : YPageState
    data class Error(val message: String) : YPageState
}

/**
 * Canonical plugin screen shell. Window insets, system bars, top bar and content padding remain
 * YUI responsibilities; a plugin only supplies its page content and optional actions.
 */
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
    YScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        actions = actions,
        bottomBar = bottomBar,
    ) { padding ->
        when (state) {
            YPageState.Ready -> content(padding)
            is YPageState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = YDimens.ScreenHorizontal),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                YLoadingState(state.message)
            }
            is YPageState.Empty -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = YDimens.ScreenHorizontal),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                YEmptyState(state.message)
            }
            is YPageState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = YDimens.ScreenHorizontal),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                YErrorState(state.message)
            }
        }
    }
}

/** Standard list/settings/detail body used inside [YPluginScaffold]. */
@Composable
fun YPluginList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(
            horizontal = YDimens.ScreenHorizontal,
            vertical = YDimens.ScreenVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(YDimens.SectionGap),
        content = content,
    )
}

/** Standard compact action row for cards, dialogs and list items. */
@Composable
fun YActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Consistent feature/plugin identity block used by the host and plugin landing pages. */
@Composable
fun YPluginHeader(
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
