package com.yagay.yui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.yagay.yui.YUiCheckbox as Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.yagay.yui.YUiScrollableTabRow as ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * YUI describes interaction roles, not feature names. All normal YSuite screens compose these
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
data class YFilterSpec(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Immutable
data class YStatusSpec(
    val label: String,
    val value: String,
    val tone: YStatusTone = YStatusTone.Neutral,
)

/** Canonical normal-screen shell. Role now controls shared density/content-width behavior. */
@Composable
fun YPageScaffold(
    title: String,
    role: YPageRole,
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
        role = role,
        content = content,
    )
}

/** Role-aware body for settings, managers, browsers, dashboards and ordinary lists. */
@Composable
fun YPageList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val role = LocalYPageRole.current
    val template = role.template()
    // Legacy compact argument is retained for binary/source compatibility.
    // Normal-screen lists always use the role's standard row spacing.
    val rowSpacing = template.sectionSpacing
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val horizontal = yPageHorizontalPadding(maxWidth)
        LazyColumn(
            modifier = Modifier.widthIn(max = template.maxContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontal,
                top = padding.calculateTopPadding() + YDimens.ScreenVertical,
                end = horizontal,
                bottom = padding.calculateBottomPadding() + YDimens.ScreenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            content = content,
        )
    }
}

@Composable
fun YSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val topPadding = if (LocalYPageRole.current == YPageRole.SETTINGS) 8.dp else 4.dp
    Column(
        modifier.fillMaxWidth().padding(top = topPadding, bottom = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Canonical role-aware section. This replaces the old feature-specific card API. */
@Composable
fun YSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val role = LocalYPageRole.current

    @Composable
    fun body() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
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

    if (role.template().emphasizeCards) {
        YCard(modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                YDimens.ControlGap,
            ),
        ) {
            body()
            YDivider()
        }
    }
}

@Composable
fun YEmptyMessage(message: String, modifier: Modifier = Modifier) {
    YNotice(text = message, modifier = modifier, tone = YNoticeTone.NEUTRAL)
}

@Composable
fun YMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: YStatusTone = YStatusTone.Neutral,
) {
    val color = when (tone) {
        YStatusTone.Neutral -> MaterialTheme.colorScheme.onSurface
        YStatusTone.Good -> ySemanticColors().success
        YStatusTone.Warning -> ySemanticColors().warning
        YStatusTone.Error -> MaterialTheme.colorScheme.error
    }
    YCard(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun YStatusLine(
    label: String,
    value: String,
    tone: YStatusTone = YStatusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val valueColor = when (tone) {
        YStatusTone.Neutral -> MaterialTheme.colorScheme.onSurface
        YStatusTone.Good -> ySemanticColors().success
        YStatusTone.Warning -> ySemanticColors().warning
        YStatusTone.Error -> MaterialTheme.colorScheme.error
    }
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
fun YHorizontalActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
    titleMaxLines: Int = 2,
    subtitleMaxLines: Int = 2,
    detailMaxLines: Int = 2,
) {
    // Material3 ListItem adds substantial fixed internal padding. An explicit row keeps the
    // same actions and accessibility while sizing to its actual content.
    val activeModifier = if (onClick != null || onLongClick != null) {
        modifier.combinedClickable(
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else modifier
    val foreground = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = activeModifier
            .fillMaxWidth()
            .heightIn(min = YDimens.OptionRowHeight)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                else Color.Transparent,
            )
            .padding(horizontal = YDimens.ScreenHorizontal, vertical = YDimens.ControlGap / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                title,
                color = foreground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = titleMaxLines.coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = subtitleMaxLines.coerceAtLeast(1),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = detailMaxLines.coerceAtLeast(1),
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
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
            imageVector = YIcons.Forward,
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
    detail: String? = null,
    leading: @Composable (() -> Unit)? = null,
) = YListItem(
    title = title,
    subtitle = subtitle,
    detail = detail,
    leading = leading,
    modifier = modifier,
    enabled = enabled,
    onClick = { if (enabled) onCheckedChange(!checked) },
    trailing = { YStandardSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
)

@Composable
fun YCheckboxItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    leading: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) = YListItem(
    title = title,
    subtitle = subtitle,
    detail = detail,
    modifier = modifier,
    leading = leading,
    selected = checked,
    enabled = enabled,
    onClick = { if (enabled) onCheckedChange(!checked) },
    trailing = { Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
)

@Composable
fun YCheckboxControl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YStatusStrip(statuses: List<YStatusSpec>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statuses.forEach { status -> YStatusPill(status.label, status.value, status.tone) }
    }
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
    val semantic = ySemanticColors()
    val container = when (tone) {
        YNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        YNoticeTone.POSITIVE -> semantic.successContainer
        YNoticeTone.WARNING -> semantic.warningContainer
        YNoticeTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val foreground = when (tone) {
        YNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        YNoticeTone.POSITIVE -> semantic.onSuccessContainer
        YNoticeTone.WARNING -> semantic.onWarningContainer
        YNoticeTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = foreground)
    }
}

@Composable
fun YChoiceSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        YFilterBar(
            options = options,
            selectedIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
            onSelected = onSelected,
        )
    }
}

@Composable
fun YOverflowMenu(
    actions: List<YActionSpec>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            enabled = actions.any { it.enabled },
        ) {
            Icon(YIcons.More, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            action.label,
                            color = if (action.style == YActionStyle.DANGER && action.enabled) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun YPrimaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    YUiButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

/** Standard Material3 text-action for dialogs and inline file/download operations. */
@Composable
fun YTextActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    YUiTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(
            horizontal = YDimens.ButtonTextPaddingHorizontal,
            vertical = YDimens.ButtonPaddingVertical,
        ),
        content = content,
    )
}

@Composable
fun YSecondaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    YUiOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
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
                YActionStyle.PRIMARY -> YPrimaryButton(action.label, action.onClick, enabled = action.enabled)
                YActionStyle.SECONDARY -> YSecondaryButton(action.label, action.onClick, enabled = action.enabled)
                YActionStyle.DANGER -> OutlinedButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.heightIn(min = YDimens.ButtonVisualHeight),
                    contentPadding = PaddingValues(horizontal = YDimens.ButtonPaddingHorizontal, vertical = YDimens.ButtonPaddingVertical),
                ) {
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
fun YToggleFilterBar(filters: List<YFilterSpec>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter.selected,
                onClick = filter.onClick,
                enabled = filter.enabled,
                label = { Text(filter.label) },
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun YTabBar(tabs: List<YTabSpec>, selectedKey: String, onSelected: (YTabSpec) -> Unit, modifier: Modifier = Modifier) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }.coerceIn(0, tabs.lastIndex)
    val effectiveKey = tabs[selectedIndex].key
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab.key == effectiveKey,
                onClick = { onSelected(tab) },
                text = {
                    Text(
                        tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
fun YSelectionBar(
    count: Int,
    actions: List<YActionSpec>,
    modifier: Modifier = Modifier,
    label: String = count.toString(),
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        SelectionContainer {
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
}

@Composable
fun YDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun YSettingsScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.SETTINGS, subtitle = subtitle, state = state, actions = actions,
    snackbarHost = snackbarHost, content = content,
)

@Composable
fun YListScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.LIST, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost, content = content,
)

@Composable
fun YManagerScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.MANAGER, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost,
    floatingActionButton = floatingActionButton, content = content,
)

@Composable
fun YBrowserScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.BROWSER, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost,
    floatingActionButton = floatingActionButton, content = content,
)

@Composable
fun YDashboardScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.DASHBOARD, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost, content = content,
)

@Composable
fun YDetailScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.DETAIL, subtitle = subtitle, state = state, actions = actions,
    snackbarHost = snackbarHost, content = content,
)

@Composable
fun YTimelineScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.TIMELINE, subtitle = subtitle, state = state, actions = actions,
    snackbarHost = snackbarHost, content = content,
)

@Composable
fun YLogScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.LOG, subtitle = subtitle, state = state, actions = actions,
    snackbarHost = snackbarHost, content = content,
)

@Composable
fun YEditorScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.EDITOR, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost, content = content,
)

@Composable
fun YWizardScaffold(
    title: String,
    subtitle: String? = null,
    state: YPageState = YPageState.Ready,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) = YPageScaffold(
    title, YPageRole.WIZARD, subtitle = subtitle, state = state, actions = actions,
    bottomBar = bottomBar, snackbarHost = snackbarHost, content = content,
)
