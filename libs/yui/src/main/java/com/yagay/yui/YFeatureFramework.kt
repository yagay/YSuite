package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Preferred YSuite feature UI surface.
 *
 * New feature screens should use the YFeature* APIs instead of building their own Scaffold/Card/
 * spacing conventions. The older YPlugin* APIs stay source-compatible while existing screens are
 * migrated incrementally.
 */
enum class YStatusTone {
    Neutral,
    Good,
    Warning,
    Error,
}

@Composable
fun YFeatureScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    YPluginScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        state = state,
        actions = actions,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun YFeatureList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    YPluginList(padding = padding, modifier = modifier, content = content)
}

@Composable
fun YFeatureCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    YCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing()
        }
        content()
    }
}

@Composable
fun YStatusPill(
    label: String,
    value: String,
    tone: YStatusTone = YStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val foreground = when (tone) {
        YStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        YStatusTone.Good -> MaterialTheme.colorScheme.primary
        YStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        YStatusTone.Error -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
    ) {
        Text(
            text = "$label · $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
    val valueColor = when (tone) {
        YStatusTone.Neutral -> MaterialTheme.colorScheme.onSurface
        YStatusTone.Good -> MaterialTheme.colorScheme.primary
        YStatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        YStatusTone.Error -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
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
) {
    YSettingRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun YSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "搜索",
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(hint) },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun YNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String = "打开",
    enabled: Boolean = true,
) {
    YSettingRow(title = title, subtitle = subtitle, modifier = modifier) {
        YSecondaryButton(
            text = actionLabel,
            onClick = onClick,
            enabled = enabled,
        )
    }
}
