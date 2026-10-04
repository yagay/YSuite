package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = YSuiteSpacing.Medium,
                vertical = YSuiteSpacing.Large,
            ),
            verticalArrangement =
                Arrangement.spacedBy(YSuiteSpacing.Medium),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(
                            top = YSuiteSpacing.Small,
                        ),
                    )
                }
            }

            header()

            when (state) {
                YSuitePageState.Content ->
                    content()
                is YSuitePageState.Loading ->
                    item {
                        YSuiteStateHost(
                            state = state,
                            onRetry = onRetry,
                        )
                    }
                is YSuitePageState.Empty ->
                    item {
                        YSuiteStateHost(
                            state = state,
                            onRetry = onRetry,
                        )
                    }
                is YSuitePageState.Error ->
                    item {
                        YSuiteStateHost(
                            state = state,
                            onRetry = onRetry,
                        )
                    }
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
