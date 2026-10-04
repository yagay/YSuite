package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

enum class YSuitePageRole {
    Dashboard,
    List,
    Detail,
    Settings,
}

@Composable
fun YSuitePage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    role: YSuitePageRole = YSuitePageRole.List,
    state: YSuitePageState = YSuitePageState.Content,
    onRetry: (() -> Unit)? = null,
    onPermissionAction: (() -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) {
    YSuiteAdaptiveContainer(modifier = modifier.fillMaxSize()) { widthClass ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (role == YSuitePageRole.Detail) {
                        Modifier
                    } else {
                        Modifier
                    },
                )
                .verticalScroll(rememberScrollState())
                .padding(YSuiteSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Large),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.XSmall)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            header?.invoke(this)

            YSuiteStateHost(
                state = state,
                onRetry = onRetry,
                onPermissionAction = onPermissionAction,
            ) {
                content(widthClass)
            }
        }
    }
}

@Composable
fun YSuiteDashboardPage(
    title: String,
    subtitle: String? = null,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) = YSuitePage(
    title = title,
    subtitle = subtitle,
    role = YSuitePageRole.Dashboard,
    state = state,
    content = content,
)

@Composable
fun YSuiteListPage(
    title: String,
    subtitle: String? = null,
    state: YSuitePageState = YSuitePageState.Content,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) = YSuitePage(
    title = title,
    subtitle = subtitle,
    role = YSuitePageRole.List,
    state = state,
    header = header,
    content = content,
)

@Composable
fun YSuiteDetailPage(
    title: String,
    subtitle: String? = null,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) = YSuitePage(
    title = title,
    subtitle = subtitle,
    role = YSuitePageRole.Detail,
    state = state,
    content = content,
)

@Composable
fun YSuiteSettingsPage(
    title: String,
    subtitle: String? = null,
    state: YSuitePageState = YSuitePageState.Content,
    content: @Composable ColumnScope.(YSuiteWidthClass) -> Unit,
) = YSuitePage(
    title = title,
    subtitle = subtitle,
    role = YSuitePageRole.Settings,
    state = state,
    content = content,
)
