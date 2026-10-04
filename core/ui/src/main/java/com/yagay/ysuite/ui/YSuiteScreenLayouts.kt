package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

@Composable
fun YSuiteDashboardScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) {
    YSuiteDashboardShell(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Large),
        ) {
            YSuiteStateHost(state = state) {
                content(info.widthClass)
            }
        }
    }
}

@Composable
fun YSuiteSettingsScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) {
    YSuiteSettingsShell(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Large),
        ) {
            YSuiteStateHost(state = state) {
                content(info.widthClass)
            }
        }
    }
}

@Composable
fun YSuiteDetailScreen(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) {
    YSuiteDetailShell(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) { info ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Large),
        ) {
            YSuiteStateHost(state = state) {
                content(info.widthClass)
            }
        }
    }
}

@Composable
fun YSuiteManagerListScreen(
    title: String,
    subtitle: String? = null,
    state: YSuitePageState = YSuitePageState.Content,
    onRetry: (() -> Unit)? = null,
    header: LazyListScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    YSuiteManagerShell(
        title = title,
        subtitle = subtitle,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                vertical = YSuiteSpacing.Small,
            ),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
        ) {
            header()

            when (state) {
                YSuitePageState.Content -> content()
                is YSuitePageState.Loading,
                is YSuitePageState.Empty,
                is YSuitePageState.Error,
                is YSuitePageState.PermissionRequired ->
                    item {
                        YSuiteStateHost(
                            state = state,
                            onRetry = onRetry,
                        )
                    }
            }
        }
    }
}
