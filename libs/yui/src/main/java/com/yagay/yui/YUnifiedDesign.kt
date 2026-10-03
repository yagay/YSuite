package com.yagay.yui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * YUI 2.0 describes interaction roles, not feature names. All normal YSuite screens compose these
 * primitives instead of inventing feature-local cards, spacing, themes or navigation chrome.
 */
enum class YPageRole {
    SETTINGS,
    LIST,
    MANAGER,
    BROWSER,
    DASHBOARD,
    DETAIL,
    TIMELINE,
    LOG,
    EDITOR,
    WIZARD,
}

enum class YActionStyle { PRIMARY, SECONDARY, DANGER }
enum class YNoticeTone { NEUTRAL, POSITIVE, WARNING, ERROR }

@Immutable
data class YActionSpec(
    val label: String,
    val enabled: Boolean = true,
    val style: YActionStyle = YActionStyle.SECONDARY,
    val onClick: () -> Unit,
)

@Immutable
data class YTabSpec(val key: String, val label: String)

@Immutable
data class YStatusSpec(
    val label: String,
    val value: String,
    val tone: YStatusTone = YStatusTone.Neutral,
)

/** Canonical normal-screen shell for current and future YSuite features. */
@Composable
fun YPageScaffold(
    title: String,
    @Suppress("UNUSED_PARAMETER") role: YPageRole,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    YFeatureScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        state = state,
        actions = actions,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** Standard body for settings, managers, browsers, dashboards and ordinary lists. */
@Composable
fun YPageList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = YDimens.ScreenHorizontal,
            top = padding.calculateTopPadding() + YDimens.ScreenVertical,
            end = YDimens.ScreenHorizontal,
            bottom = padding.calculateBottomPadding() + YDimens.ScreenVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else YDimens.SectionGap),
        content = content,
    )
}

@Composable
fun YSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Canonical row for navigation, state, settings and manager/browser entries. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick != null || onLongClick != null) {
        modifier.combinedClickable(enabled = enabled, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else modifier
    ListItem(
        modifier = interactionModifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface,
            disabledHeadlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle.isNullOrBlank() && detail.isNullOrBlank()) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!detail.isNullOrBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        leadingContent = leading,
        trailingContent = trailing,
    )
}

@Composable
fun YNavigationItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = YListItem(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    enabled = enabled,
    onClick = onClick,
    trailing = {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
)

@Composable
fun YSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = YListItem(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    enabled = enabled,
    onClick = { if (enabled) onCheckedChange(!checked) },
    trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
)

@Composable
fun YCheckboxItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = YListItem(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    selected = checked,
    enabled = enabled,
    onClick = { if (enabled) onCheckedChange(!checked) },
    trailing = { Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
)

@Composable
fun YStatusItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: YStatusTone = YStatusTone.Neutral,
) = YListItem(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    trailing = { YStatusPill(label = "", value = value, tone = tone) },
)

@Composable
fun YStatusStrip(statuses: List<YStatusSpec>, modifier: Modifier = Modifier) {
    YActionGroup(
        modifier = modifier,
        actions = statuses.map { status ->
            YActionSpec(
                label = if (status.label.isBlank()) status.value else "${status.label} · ${status.value}",
                enabled = false,
                style = when (status.tone) {
                    YStatusTone.Error -> YActionStyle.DANGER
                    else -> YActionStyle.SECONDARY
                },
                onClick = {},
            )
        },
    )
}

@Composable
fun YProgressItem(
    title: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        YListItem(title = title, subtitle = subtitle, detail = detail, leading = leading, trailing = trailing)
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun YNotice(text: String, modifier: Modifier = Modifier, tone: YNoticeTone = YNoticeTone.NEUTRAL) {
    val container = when (tone) {
        YNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        YNoticeTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
        YNoticeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        YNoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val foreground = when (tone) {
        YNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        YNoticeTone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        YNoticeTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        YNoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = foreground)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YActionGroup(actions: List<YActionSpec>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action ->
            when (action.style) {
                YActionStyle.PRIMARY -> Button(onClick = action.onClick, enabled = action.enabled) {
                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                YActionStyle.SECONDARY -> OutlinedButton(onClick = action.onClick, enabled = action.enabled) {
                    Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                YActionStyle.DANGER -> OutlinedButton(onClick = action.onClick, enabled = action.enabled) {
                    Text(
                        action.label,
                        color = if (action.enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun YFilterBar(options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(selected = selectedIndex == index, onClick = { onSelected(index) }, label = { Text(label) })
        }
    }
}

@Composable
fun YTabBar(tabs: List<YTabSpec>, selectedKey: String, onSelected: (YTabSpec) -> Unit, modifier: Modifier = Modifier) {
    YFilterBar(
        options = tabs.map { it.label },
        selectedIndex = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0),
        onSelected = { index -> tabs.getOrNull(index)?.let(onSelected) },
        modifier = modifier,
    )
}

@Composable
fun YSelectionBar(
    count: Int,
    actions: List<YActionSpec>,
    modifier: Modifier = Modifier,
    label: String = count.toString(),
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            YActionGroup(actions)
        }
    }
}

@Composable
fun YLogPanel(lines: List<String>, modifier: Modifier = Modifier, maxHeightDp: Int = 360) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeightDp.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
fun YDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun YSettingsScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.SETTINGS, subtitle = subtitle, state = state, actions = actions, content = content)

@Composable
fun YListScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.LIST, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, content = content)

@Composable
fun YManagerScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, floatingActionButton: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.MANAGER, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, floatingActionButton = floatingActionButton, content = content)

@Composable
fun YBrowserScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, floatingActionButton: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.BROWSER, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, floatingActionButton = floatingActionButton, content = content)

@Composable
fun YDashboardScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.DASHBOARD, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, content = content)

@Composable
fun YDetailScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.DETAIL, subtitle = subtitle, state = state, actions = actions, content = content)

@Composable
fun YTimelineScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.TIMELINE, subtitle = subtitle, state = state, actions = actions, content = content)

@Composable
fun YLogScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.LOG, subtitle = subtitle, state = state, actions = actions, content = content)

@Composable
fun YEditorScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.EDITOR, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, content = content)

@Composable
fun YWizardScaffold(title: String, subtitle: String? = null, state: YPageState = YPageState.Ready, actions: @Composable RowScope.() -> Unit = {}, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) =
    YPageScaffold(title, YPageRole.WIZARD, subtitle = subtitle, state = state, actions = actions, bottomBar = bottomBar, content = content)
