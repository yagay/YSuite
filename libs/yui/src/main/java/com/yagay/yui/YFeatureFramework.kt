package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class YStatusTone { Neutral, Good, Warning, Error }

@Composable
fun YFeatureScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    role: YPageRole = YPageRole.LIST,
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalYPageRole provides role) {
        YScaffold(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            actions = actions,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
        ) { padding -> YFeatureStateContent(padding, state, content) }
    }
}

@Composable
fun YCustomScaffold(
    modifier: Modifier = Modifier,
    state: YPageState = YPageState.Ready,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    role: YPageRole = YPageRole.LIST,
    content: @Composable (PaddingValues) -> Unit,
) {
    CompositionLocalProvider(LocalYPageRole provides role) {
        Scaffold(
            modifier = modifier,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding -> YFeatureStateContent(padding, state, content) }
    }
}

@Deprecated("Use YCustomScaffold")
@Composable
fun YFeatureCustomScaffold(
    modifier: Modifier = Modifier,
    state: YPageState = YPageState.Ready,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    role: YPageRole = YPageRole.LIST,
    content: @Composable (PaddingValues) -> Unit,
) = YCustomScaffold(
    modifier = modifier,
    state = state,
    topBar = topBar,
    bottomBar = bottomBar,
    snackbarHost = snackbarHost,
    floatingActionButton = floatingActionButton,
    role = role,
    content = content,
)

@Composable
private fun YFeatureStateContent(
    padding: PaddingValues,
    state: YPageState,
    content: @Composable (PaddingValues) -> Unit,
) {
    when (state) {
        YPageState.Ready -> content(padding)
        is YPageState.Loading -> Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = YDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { YLoadingState(state.message) }
        is YPageState.Empty -> Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = YDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { YEmptyState(state.message) }
        is YPageState.Error -> Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = YDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { YErrorState(state.message) }
    }
}

@Deprecated("Use YPageList")
@Composable
fun YFeatureList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) = YPageList(padding = padding, modifier = modifier, content = content)

@Deprecated("Use YSectionHeader")
@Composable
fun YFeatureSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) = YSectionHeader(title = title, modifier = modifier, subtitle = subtitle)

/** Compatibility alias. Feature modules must migrate to [YSection]. */
@Deprecated("Use YSection")
@Composable
fun YFeatureCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) = YSection(title, modifier, subtitle, detail, trailing, content)

@Deprecated("Use YEmptyMessage")
@Composable
fun YFeatureEmpty(message: String, modifier: Modifier = Modifier) =
    YEmptyMessage(message = message, modifier = modifier)

@Composable
private fun yStatusForeground(tone: YStatusTone) = when (tone) {
    YStatusTone.Neutral -> MaterialTheme.colorScheme.onSurface
    YStatusTone.Good -> ySemanticColors().success
    YStatusTone.Warning -> ySemanticColors().warning
    YStatusTone.Error -> MaterialTheme.colorScheme.error
}

@Composable
private fun yStatusContainer(tone: YStatusTone) = when (tone) {
    YStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    YStatusTone.Good -> ySemanticColors().successContainer
    YStatusTone.Warning -> ySemanticColors().warningContainer
    YStatusTone.Error -> MaterialTheme.colorScheme.errorContainer
}

@Deprecated("Use YMetricCard")
@Composable
fun YFeatureStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: YStatusTone = YStatusTone.Neutral,
) = YMetricCard(label, value, modifier, tone)

@Composable
fun YStatusPill(
    label: String,
    value: String,
    tone: YStatusTone = YStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val foreground = yStatusForeground(tone)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = yStatusContainer(tone).copy(alpha = if (tone == YStatusTone.Neutral) 0.72f else 1f),
    ) {
        val text = if (label.isBlank()) value else stringResource(R.string.yui_status_pair, label, value)
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Deprecated("Use YStatusLine")
@Composable
fun YStatusRow(
    label: String,
    value: String,
    tone: YStatusTone = YStatusTone.Neutral,
    modifier: Modifier = Modifier,
) = YStatusLine(label, value, tone, modifier)

@Deprecated("Use YSwitchItem")
@Composable
fun YSettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = YSwitchItem(title, checked, onCheckedChange, modifier, subtitle, enabled)

@Composable
fun YSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val resolvedHint = hint ?: stringResource(R.string.yui_search)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(resolvedHint) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun YNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    enabled: Boolean = true,
) {
    if (actionLabel == null) {
        YNavigationItem(title = title, subtitle = subtitle, modifier = modifier, enabled = enabled, onClick = onClick)
    } else {
        YSettingRow(title = title, subtitle = subtitle, modifier = modifier) {
            YSecondaryButton(text = actionLabel, onClick = onClick, enabled = enabled)
        }
    }
}
