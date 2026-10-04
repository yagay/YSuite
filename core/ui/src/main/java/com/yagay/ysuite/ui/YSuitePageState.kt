package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.component.YSuitePrimaryAction
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

sealed interface YSuitePageState {
    data object Content : YSuitePageState
    data class Loading(val message: String? = null) : YSuitePageState
    data class Empty(val title: String, val message: String? = null) : YSuitePageState
    data class Error(
        val title: String,
        val message: String? = null,
        val retryText: String? = null,
    ) : YSuitePageState
    data class PermissionRequired(
        val title: String,
        val message: String,
        val actionText: String,
    ) : YSuitePageState
}

@Composable
fun YSuiteStateHost(
    state: YSuitePageState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onPermissionAction: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    when (state) {
        YSuitePageState.Content -> content()
        is YSuitePageState.Loading ->
            YSuiteCenteredState(modifier) {
                CircularProgressIndicator()
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        is YSuitePageState.Empty ->
            YSuiteCenteredState(modifier) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        is YSuitePageState.Error ->
            YSuiteCenteredState(modifier) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                state.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.retryText != null && onRetry != null) {
                    YSuitePrimaryAction(
                        text = state.retryText,
                        onClick = onRetry,
                    )
                }
            }
        is YSuitePageState.PermissionRequired ->
            YSuiteCenteredState(modifier) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (onPermissionAction != null) {
                    YSuitePrimaryAction(
                        text = state.actionText,
                        onClick = onPermissionAction,
                    )
                }
            }
    }
}

@Composable
private fun YSuiteCenteredState(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(YSuiteSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        content()
    }
}
