package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

@Composable
fun YSuiteLazyListPage(
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
            verticalArrangement =
                Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            header()

            when (state) {
                YSuitePageState.Content ->
                    content()
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
