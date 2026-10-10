package com.yagay.YEntryCleaner.ui

import com.yagay.yui.LocalYAppearance
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.yagay.yui.YUiTextButton as TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.data.CleanupKind
import com.yagay.YEntryCleaner.data.RootComponent
import com.yagay.YEntryCleaner.domain.AppTypeFilter
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YCheckboxControl
import com.yagay.yui.YFormDialog
import com.yagay.yui.YSection
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone

private fun componentAppSelectionRank(items: List<RootComponent>): Int {
    val editable = items.filter { it.blocked == null && it.enabled != null }
    val disabledCount = editable.count { it.enabled == false }
    return when {
        editable.isNotEmpty() && disabledCount == editable.size -> 0
        disabledCount > 0 -> 1
        else -> 2
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RootComponentsScreen(state: MainState, vm: MainViewModel) {
    val scan by vm.componentScan.collectAsState()
    val busy by vm.componentBusy.collectAsState()
    val message by vm.componentMessage.collectAsState()
    val rootNotice by vm.componentRootNotice.collectAsState()
    rootNotice?.let { notice ->
        YFormDialog(
            title = stringResource(R.string.root_permission_required),
            onDismissRequest = vm::dismissComponentRootNotice,
            actions = listOf(
                YActionSpec(
                    label = stringResource(R.string.root_permission_ack),
                    style = YActionStyle.PRIMARY,
                    onClick = vm::dismissComponentRootNotice,
                ),
            ),
        ) {
            Text(notice)
        }
    }

    var kind by remember { mutableStateOf<CleanupKind?>(null) }
    val bulkLockRevision by vm.bulkLockRevision.collectAsState()
    var viewFilter by rememberSaveable { mutableStateOf(UiFilter.ALL) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }
    var filterMenu by remember { mutableStateOf(false) }
    var appTypeMenu by remember { mutableStateOf(false) }
    var expandedAppKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshComponents() }

    val kindItems = scan.items.filter { kind == null || it.kind == kind }
    val lockItemsByApp = remember(kindItems, bulkLockRevision) {
        kindItems.groupBy(::componentBulkLockAppId)
    }
    val scopeItems = kindItems.filter { appTypeFilter.matches(it.appType) }
    val baseVisible = scopeItems.filter {
        (when (viewFilter) {
                UiFilter.ALL, UiFilter.LOCKED -> true
                UiFilter.SHOW_SELECTED -> it.enabled == false
                UiFilter.HIDE_SELECTED -> it.enabled == true
            }) &&
            (state.query.isBlank() || listOf(it.label, it.owner, it.component.flattenToString()).any { text -> text.contains(state.query, true) })
    }
    val baseGroups = baseVisible.groupBy { "${it.user}|${it.component.packageName}" }.entries
        .sortedWith(
            compareBy<Map.Entry<String, List<RootComponent>>> { componentAppSelectionRank(it.value) }
                .thenBy { it.value.first().owner.lowercase() }
                .thenBy { it.key }
        )
    val groups = if (viewFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseGroups.filter { (appKey, _) ->
            vm.componentBulkLockState(kind, appKey, lockItemsByApp[appKey].orEmpty()) != BulkLockState.NONE
        }
    } else {
        baseGroups
    }
    val visible = groups.flatMap { it.value }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalYAppearance.current.sectionSpacingDp.dp)) {
        item(key = "title") {
            YSectionHeader(
                title = stringResource(R.string.root_screen_title),
                subtitle = stringResource(R.string.root_filter_help),
                modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp),
            )
        }
        stickyHeader(key = "controls") {
            Surface(tonalElevation = (LocalYAppearance.current.rowVerticalPaddingDp / 3f).dp) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = LocalYAppearance.current.rowHorizontalPaddingDp.dp)) {
                        items(listOf<CleanupKind?>(null) + CleanupKind.entries) { entry ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(onClick = { kind = entry }) {
                                    Text(
                                        entry?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all),
                                        fontWeight = if (kind == entry) FontWeight.Bold else FontWeight.Normal,
                                        color = if (kind == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    Modifier.height((LocalYAppearance.current.rowVerticalPaddingDp / 3f).dp)
                                        .width(LocalYAppearance.current.iconVisualSizeDp.dp).background(
                                        if (kind == entry) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            TextButton(
                                onClick = { appTypeMenu = true },
                                contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.compact_filter_format,
                                        stringResource(R.string.app_type_filter),
                                        stringResource(appTypeFilter.titleRes())
                                    )
                                )
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 0.75f).dp))
                            }
                            DropdownMenu(expanded = appTypeMenu, onDismissRequest = { appTypeMenu = false }) {
                                AppTypeFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(filter.titleRes())) },
                                        leadingIcon = { if (appTypeFilter == filter) Icon(Icons.Rounded.Check, null) },
                                        onClick = { appTypeFilter = filter; appTypeMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            TextButton(
                                onClick = { filterMenu = true },
                                contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                            ) {
                                Text(
                                    stringResource(
                                        R.string.compact_filter_format,
                                        stringResource(R.string.view_filter),
                                        stringResource(viewFilter.titleRes())
                                    )
                                )
                                Icon(Icons.Rounded.ExpandMore, null, Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 0.75f).dp))
                            }
                            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                                UiFilter.entries.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(filter.titleRes())) },
                                        leadingIcon = { if (viewFilter == filter) Icon(Icons.Rounded.Check, null) },
                                        onClick = { viewFilter = filter; filterMenu = false }
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = { vm.changeComponentsBulk(kind, visible, enable = false) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled == true },
                            contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                        ) { Text(stringResource(R.string.select_all)) }
                        TextButton(
                            onClick = { vm.invertComponentsBulk(kind, visible) },
                            enabled = !busy && visible.any { it.blocked == null && it.enabled != null },
                            contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                        ) { Text(stringResource(R.string.invert_selection)) }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    HorizontalDivider()
                }
            }
        }
        item(key = "summary") {
            YSection(
                title = stringResource(R.string.root_summary, groups.size, visible.size),
                subtitle = stringResource(R.string.root_selection_semantics),
                detail = stringResource(R.string.root_change_semantics),
                modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = (LocalYAppearance.current.effectiveGapDp / 2f).dp),
            ) {
                YStatusLine(
                    label = stringResource(R.string.view_filter),
                    value = stringResource(
                        when (kind) {
                            null -> R.string.root_kind_all_help
                            CleanupKind.TILE -> R.string.root_kind_tile_help
                            CleanupKind.SHORTCUT -> R.string.root_kind_shortcut_help
                            CleanupKind.WIDGET -> R.string.root_kind_widget_help
                        }
                    ),
                    tone = YStatusTone.Neutral,
                )
                if (scan.warning.isNotBlank()) {
                    YStatusLine(stringResource(R.string.yentry_status_warning), scan.warning, YStatusTone.Error)
                }
                message?.let { YStatusLine(stringResource(R.string.yentry_status_status), it, YStatusTone.Neutral) }
            }
        }

        groups.forEach { (appKey, unsorted) ->
            val components = unsorted.sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }, { it.id }))
            val editableComponents = components.filter { it.blocked == null && it.enabled != null }
            val disabledCount = editableComponents.count { it.enabled == false }
            val selectionState = when {
                disabledCount == 0 -> ToggleableState.Off
                disabledCount == editableComponents.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            val first = components.first()
            val expansionKey = "${kind?.name ?: "ALL"}|$appKey"
            val expanded = expandedAppKey == expansionKey
            val onExpand = { expandedAppKey = if (expandedAppKey == expansionKey) null else expansionKey }

            item(key = "app|$expansionKey") {
                val expandLabel = stringResource(if (expanded) R.string.common_collapse else R.string.common_expand)
                val kindTitles = CleanupKind.entries.filter { entry -> components.any { it.kind == entry } }
                    .map { stringResource(it.titleRes()) }
                Row(
                    Modifier.fillMaxWidth()
                        .bulkLockSwipe(
                            onLock = {
                                vm.setComponentAppLocked(
                                    kind,
                                    appKey,
                                    lockItemsByApp[appKey].orEmpty(),
                                    true
                                )
                            },
                            onUnlock = {
                                vm.setComponentAppLocked(
                                    kind,
                                    appKey,
                                    lockItemsByApp[appKey].orEmpty(),
                                    false
                                )
                            }
                        )
                        .clickable(onClickLabel = expandLabel, onClick = onExpand)
                        .heightIn(min = LocalYAppearance.current.rowHeightDp.dp)
                        .padding(horizontal = LocalYAppearance.current.effectiveGapDp.dp,
                            vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(
                        state = selectionState,
                        enabled = !busy && editableComponents.isNotEmpty(),
                        onClick = { vm.changeComponents(editableComponents, selectionState == ToggleableState.On) }
                    )
                    AppIcon(first.icon, first.owner)
                    Column(Modifier.weight(1f).padding(start = LocalYAppearance.current.effectiveGapDp.dp)) {
                        Text(first.owner, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(
                                R.string.root_disabled_summary,
                                components.count { it.enabled == false },
                                components.size,
                                kindTitles.joinToString(stringResource(R.string.yentry_list_separator))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (editableComponents.size < components.size) {
                            Text(
                                stringResource(R.string.root_display_only_count, components.size - editableComponents.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val lockState = vm.componentBulkLockState(
                        kind,
                        appKey,
                        lockItemsByApp[appKey].orEmpty()
                    )
                    if (lockState != BulkLockState.NONE) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = stringResource(
                                if (lockState == BulkLockState.PARTIAL) R.string.bulk_lock_partial
                                else R.string.bulk_lock_full
                            ),
                            modifier = Modifier.padding(horizontal = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp)
                                .size(LocalYAppearance.current.iconVisualSizeDp.dp),
                            tint = if (lockState == BulkLockState.PARTIAL) MaterialTheme.colorScheme.tertiary
                            else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = onExpand) {
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, expandLabel)
                    }
                }
                HorizontalDivider()
            }

            if (expanded) items(components, key = { it.id }) { item ->
                val editable = !busy && item.blocked == null && item.enabled != null
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .bulkLockSwipe(
                            enabled = !vm.isComponentAppLockedForItem(kind, item),
                            onLock = { vm.setComponentItemLocked(kind, item, true) },
                            onUnlock = { vm.setComponentItemLocked(kind, item, false) }
                        )
                        .toggleable(
                            value = item.enabled == false,
                            enabled = editable,
                            role = Role.Checkbox,
                            onValueChange = { checked -> vm.changeComponent(item, !checked) }
                        )
                        .heightIn(min = LocalYAppearance.current.rowHeightDp.dp)
                        .padding(start = LocalYAppearance.current.rowHorizontalPaddingDp.dp,
                            end = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp,
                            top = LocalYAppearance.current.rowVerticalPaddingDp.dp,
                            bottom = LocalYAppearance.current.rowVerticalPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YCheckboxControl(
                        checked = item.enabled == false,
                        enabled = editable,
                        onCheckedChange = null,
                        modifier = Modifier.padding(LocalYAppearance.current.effectiveGapDp.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(item.kind.titleRes()), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            item.component.flattenToShortString(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            item.blocked ?: stringResource(
                                when (item.overrideState) {
                                    0 -> if (item.enabled == true) R.string.root_default_enabled else R.string.root_default_disabled
                                    1 -> R.string.root_explicit_enabled
                                    2, 3, 4 -> R.string.root_disabled_unknown_source
                                    else -> R.string.root_unknown_state
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    val itemLocked = vm.isComponentBulkProtected(kind, item)
                    if (itemLocked) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = stringResource(R.string.bulk_lock_full),
                            modifier = Modifier.padding(horizontal = (LocalYAppearance.current.rowHorizontalPaddingDp / 2f).dp)
                                .size((LocalYAppearance.current.iconVisualSizeDp * 0.75f).dp)
                        )
                    }
                }
            }
        }
        if (visible.isEmpty() && !busy) {
            item { YEmptyMessage(stringResource(R.string.root_no_components)) }
        }
    }
}
