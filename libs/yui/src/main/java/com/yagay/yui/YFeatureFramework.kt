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
import androidx.compose.material3.OutlinedTextField
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
fun YFeatureCustomScaffold(
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

@Composable
fun YFeatureList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) = YPageList(padding = padding, modifier = modifier, content = content)

@Composable
fun YFeatureSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) = YSectionHeader(title = title, modifier = modifier, subtitle = subtitle)

/**
 * Legacy card API kept for existing modules, but the visual surface is role-aware:
 * dashboards keep strong cards; ordinary settings/lists use flatter sections so dense tools
 * do not become a wall of nested cards.
 */
@Composable
fun YFeatureCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val role = LocalYPageRole.current

    @Composable
    fun sectionBody() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) {
            Column(Modifier.weight(1f)) { YFeatureCardCopy(title, subtitle, detail) }
            trailing()
        }
        content()
    }

    if (role == YPageRole.DASHBOARD) {
        YCard(modifier = modifier.fillMaxWidth()) {
            sectionBody()
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                if (role.prefersCompactRows()) 8.dp else YDimens.ControlGap,
            ),
        ) {
            sectionBody()
            YDivider()
        }
    }
}

@Composable
private fun YFeatureCardCopy(title: String, subtitle: String?, detail: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!detail.isNullOrBlank()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun YFeatureEmpty(message: String, modifier: Modifier = Modifier) {
    YNotice(text = message, modifier = modifier, tone = YNoticeTone.NEUTRAL)
}

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

@Composable
fun YFeatureStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: YStatusTone = YStatusTone.Neutral,
) {
    val color = yStatusForeground(tone)
    YCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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

@Composable
fun YStatusRow(
    label: String,
    value: String,
    tone: YStatusTone = YStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val valueColor = yStatusForeground(tone)
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

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
        shape = MaterialTheme.shapes.extraLarge,
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
