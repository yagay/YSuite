package com.yagay.YEntryCleaner.ui

import com.yagay.yui.LocalYAppearance
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.yagay.yui.YUiSurface as Surface
import com.yagay.yui.YUiText as Text
import com.yagay.yui.YUiTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.AppTypeFilter
import com.yagay.YEntryCleaner.domain.DisplayMode
import com.yagay.YEntryCleaner.domain.IntentKind
import com.yagay.YEntryCleaner.domain.OpenPreset
import com.yagay.YEntryCleaner.domain.matchesBrowserHost
import com.yagay.YEntryCleaner.domain.matchesOpenPreset
import com.yagay.yui.YSection
import com.yagay.yui.YEmptyMessage
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RulesTab(state: MainState, vm: MainViewModel) {
    val bulkLockRevision by vm.bulkLockRevision.collectAsState()
    var editingTitle by remember { mutableStateOf<com.yagay.YEntryCleaner.domain.ComponentCandidate?>(null) }
    var showCustomTypes by rememberSaveable { mutableStateOf(false) }
    var showBrowserHosts by rememberSaveable { mutableStateOf(false) }
    var appTypeFilter by rememberSaveable { mutableStateOf(AppTypeFilter.ALL) }

    editingTitle?.let { item ->
        ComponentTitleDialog(
            item,
            state.priorities.titles[item.rule.id],
            onSave = { vm.setComponentTitle(item.rule.id, it) },
            onDismiss = { editingTitle = null }
        )
    }
    if (showCustomTypes) {
        CustomOpenTypeDialog(
            config = state.openTypesExplicit,
            onSave = vm::setCustomOpenDefinition,
            onDismiss = { showCustomTypes = false }
        )
    }
    if (showBrowserHosts) {
        BrowserHostDialog(
            config = state.browserLinks,
            onSave = vm::setBrowserHosts,
            onDismiss = { showBrowserHosts = false }
        )
    }

    var openPreset by rememberSaveable { mutableStateOf<OpenPreset?>(null) }
    var browserHost by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.filter) {
        if (state.filter != IntentKind.OPEN) openPreset = null
        if (state.filter != IntentKind.DEEP_LINK) browserHost = null
    }
    LaunchedEffect(state.openTypesExplicit.customDefinitions, openPreset) {
        if (openPreset?.isCustom == true && openPreset !in state.openTypesExplicit.customDefinitions) openPreset = null
    }
    LaunchedEffect(state.browserAvailableHosts, browserHost) {
        if (browserHost != null && browserHost !in state.browserAvailableHosts) browserHost = null
    }

    val typedOpenPreset = openPreset.takeIf { state.filter == IntentKind.OPEN }
    val deepLinkHost = browserHost.takeIf { state.filter == IntentKind.DEEP_LINK }
    val typedSelected = typedOpenPreset?.let { state.openTypes.selectedRules(it) }.orEmpty()
    val explicitTypedSelected = typedOpenPreset?.let { state.openTypesExplicit.selectedRules(it) }.orEmpty()
    val explicitDeepLinkSelected = deepLinkHost?.let { state.browserLinks.selectedRules(it) }.orEmpty()
    val genericDeepLinkSelected = state.selected.filterTo(linkedSetOf()) { it.kind == IntentKind.DEEP_LINK }
    val deepLinkSelected = genericDeepLinkSelected + explicitDeepLinkSelected
    val deepLinkScoped = deepLinkHost != null
    val lockScope = deepLinkHost?.let(::browserRuleBulkLockScope) ?: ruleBulkLockScope(state.filter, openPreset)
    val lockScopeCandidates = when {
        typedOpenPreset != null -> state.candidates.filter {
            it.rule.kind == IntentKind.OPEN &&
                it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions)
        }
        deepLinkHost != null -> state.candidates.filter {
            it.rule.kind == IntentKind.DEEP_LINK &&
                (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
        }
        else -> state.candidates.filter { state.filter == null || it.rule.kind == state.filter }
    }
    val lockRulesByPackage = remember(lockScopeCandidates, bulkLockRevision) {
        lockScopeCandidates.groupBy { it.rule.packageName }
            .mapValues { (_, items) -> items.map { it.rule } }
    }
    val baseShownGroups = when {
        typedOpenPreset != null -> groupCandidates(
            state.candidates.filter {
                it.matchesOpenPreset(typedOpenPreset, state.openTypesExplicit.customDefinitions)
            },
            typedSelected,
            IntentKind.OPEN,
            state.query,
            state.uiFilter
        )
        deepLinkHost != null -> groupCandidates(
            state.candidates.filter {
                it.rule.kind == IntentKind.DEEP_LINK &&
                    (it.matchesBrowserHost(deepLinkHost) || it.rule in deepLinkSelected)
            },
            deepLinkSelected,
            IntentKind.DEEP_LINK,
            state.query,
            state.uiFilter
        )
        else -> state.groups
    }
    val lockFilteredGroups = if (state.uiFilter == UiFilter.LOCKED) {
        bulkLockRevision
        baseShownGroups.filter { group ->
            if (deepLinkScoped) {
                vm.bulkLockState(
                    lockScope,
                    group.packageName,
                    lockRulesByPackage[group.packageName].orEmpty().map { it.id }
                ) != BulkLockState.NONE
            } else {
                vm.ruleBulkLockState(
                    state.filter,
                    openPreset,
                    group.packageName,
                    lockRulesByPackage[group.packageName].orEmpty()
                ) != BulkLockState.NONE
            }
        }
    } else {
        baseShownGroups
    }
    val shownGroups = lockFilteredGroups.filter { appTypeFilter.matches(it.appType) }
    val activeSelected = when {
        typedOpenPreset != null -> typedSelected
        deepLinkHost != null -> deepLinkSelected
        else -> state.selected
    }
    val visibleRules = shownGroups.flatMap { it.components }.map { it.rule }.distinct()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalYAppearance.current.sectionSpacingDp.dp)) {
        item(key = "module-indicator") {
            ModuleStatusRow(state, compact = true) { vm.setDestination(Destination.DASHBOARD) }
            if (state.runtime.needsDecision) RuntimePanel(state, vm, showUpdateTools = false)
        }
        stickyHeader(key = "list-controls") {
            Surface(tonalElevation = 2.dp) {
                Column {
                    ListControls(
                        state,
                        vm::setFilter,
                        vm::setUiFilter,
                        appTypeFilter = appTypeFilter,
                        onAppTypeFilter = { appTypeFilter = it },
                        extraFilter = {
                            when (state.filter) {
                                IntentKind.OPEN -> OpenPresetFilterMenu(
                                    selected = openPreset,
                                    config = state.openTypesExplicit,
                                    onSelected = { openPreset = it },
                                    onManageCustom = { showCustomTypes = true }
                                )
                                IntentKind.DEEP_LINK -> BrowserHostFilterMenu(
                                    selected = browserHost,
                                    config = state.browserLinks,
                                    availableHosts = state.browserAvailableHosts,
                                    onSelected = { browserHost = it },
                                    onManage = { showBrowserHosts = true }
                                )
                                else -> Unit
                            }
                        },
                        onSelectAll = {
                            when {
                                typedOpenPreset != null ->
                                    vm.selectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.selectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.selectRules(visibleRules, state.filter, openPreset)
                            }
                        },
                        onSelectNone = {
                            when {
                                typedOpenPreset != null ->
                                    vm.deselectOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.deselectBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.deselectRules(visibleRules, state.filter, openPreset)
                            }
                        },
                        onInvert = {
                            when {
                                typedOpenPreset != null ->
                                    vm.invertOpenTypeRules(typedOpenPreset, visibleRules, lockScope)
                                deepLinkHost != null ->
                                    vm.invertBrowserHostRules(deepLinkHost, visibleRules, lockScope)
                                else -> vm.invertRules(visibleRules, state.filter, openPreset)
                            }
                        }
                    )
                }
            }
        }
        item(key = "list-summary") {
            SummaryRow(state, shownGroups.size, openPreset, browserHost)
            if (openPreset == null &&
                state.uiFilter != UiFilter.SHOW_SELECTED &&
                state.candidates.any { it.rule in state.selected && (it.unavailable || it.restricted) }
            ) {
                TextButton(
                    onClick = {
                        vm.setFilter(null)
                        vm.setQuery("")
                        vm.setUiFilter(UiFilter.SHOW_SELECTED)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.rules_view_unmatched))
                }
            }
        }
        shownGroups.forEach { group ->
            val key = "${state.filter?.name ?: "ALL"}|${openPreset?.name ?: browserHost ?: "ALL"}|${group.packageName}"
            val expanded = state.expandedAppKey == key
            item(key = "app|${group.packageName}", contentType = "app") {
                AppRow(
                    group,
                    activeSelected,
                    expanded,
                    if (deepLinkScoped) {
                        vm.bulkLockState(
                            lockScope,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty().map { it.id }
                        )
                    } else {
                        vm.ruleBulkLockState(
                            state.filter,
                            openPreset,
                            group.packageName,
                            lockRulesByPackage[group.packageName].orEmpty()
                        )
                    },
                    { vm.toggleExpandedApp(key) },
                    { selected ->
                        when {
                            typedOpenPreset != null -> vm.setOpenTypeGroupSelected(typedOpenPreset, group, selected)
                            deepLinkHost != null -> vm.setBrowserHostGroupSelected(deepLinkHost, group, selected)
                            else -> vm.setGroupSelected(group, selected)
                        }
                    },
                    {
                        if (deepLinkScoped) {
                            vm.setBulkAppLocked(
                                lockScope,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                                true
                            )
                        } else {
                            vm.setRuleAppLocked(
                                state.filter,
                                openPreset,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty(),
                                true
                            )
                        }
                    },
                    {
                        if (deepLinkScoped) {
                            vm.setBulkAppLocked(
                                lockScope,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty().map { it.id },
                                false
                            )
                        } else {
                            vm.setRuleAppLocked(
                                state.filter,
                                openPreset,
                                group.packageName,
                                lockRulesByPackage[group.packageName].orEmpty(),
                                false
                            )
                        }
                    }
                )
            }
            if (expanded) {
                items(group.components, key = { "component|${it.rule.id}" }, contentType = { "component" }) { component ->
                    val sourceNote = when {
                        typedOpenPreset != null && component.rule in activeSelected -> when {
                            component.rule in state.selected -> stringResource(R.string.rules_inherited_from_all)
                            component.rule in explicitTypedSelected -> stringResource(
                                R.string.rules_dedicated_rule,
                                state.openTypes.localizedTitle(typedOpenPreset)
                            )
                            else -> null
                        }
                        deepLinkHost != null && component.rule in activeSelected -> when {
                            component.rule in genericDeepLinkSelected -> stringResource(R.string.rules_browser_inherited)
                            component.rule in explicitDeepLinkSelected -> stringResource(
                                R.string.rules_browser_dedicated,
                                deepLinkHost
                            )
                            else -> null
                        }
                        else -> null
                    }
                    ComponentRow(
                        component,
                        component.rule in activeSelected,
                        state.priorities.titles[component.rule.id],
                        selectionNote = sourceNote,
                        locked = if (deepLinkScoped) {
                            vm.isBulkProtected(lockScope, component.rule.packageName, component.rule.id)
                        } else {
                            vm.isRuleBulkProtected(state.filter, openPreset, component.rule)
                        },
                        lockToggleEnabled = if (deepLinkScoped) {
                            !vm.isBulkAppLocked(lockScope, component.rule.packageName)
                        } else {
                            !vm.isRuleAppLockedForRule(state.filter, openPreset, component.rule)
                        },
                        onToggle = {
                            when {
                                typedOpenPreset != null -> vm.toggleOpenType(typedOpenPreset, component.rule)
                                deepLinkHost != null -> vm.toggleBrowserHost(deepLinkHost, component.rule)
                                else -> vm.toggle(component.rule)
                            }
                        },
                        onLock = {
                            if (deepLinkScoped) vm.setBulkItemLocked(lockScope, component.rule.id, true)
                            else vm.setRuleItemLocked(state.filter, openPreset, component.rule, true)
                        },
                        onUnlock = {
                            if (deepLinkScoped) vm.setBulkItemLocked(lockScope, component.rule.id, false)
                            else vm.setRuleItemLocked(state.filter, openPreset, component.rule, false)
                        },
                        onEditTitle = { editingTitle = component }
                    )
                }
            }
        }
        if (!state.loading && shownGroups.isEmpty()) {
            item {
                YEmptyMessage(
                    message = stringResource(R.string.no_matching_components),
                    modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    state: MainState,
    groupCount: Int = state.groups.size,
    openPreset: OpenPreset? = null,
    browserHost: String? = null
) {
    val presetTitle = when {
        openPreset != null -> state.openTypes.localizedTitle(openPreset)
        browserHost != null -> browserHost
        else -> null
    }
    val modeText = stringResource(
        when (state.displayMode) {
            DisplayMode.HIDE_SELECTED -> R.string.rules_mode_hide_selected
            DisplayMode.SHOW_SELECTED -> R.string.rules_mode_show_selected
            DisplayMode.SHOW_ALL -> R.string.rules_mode_show_all
        }
    )
    val usageText = if (presetTitle == null) {
        stringResource(R.string.rules_usage_help)
    } else {
        stringResource(R.string.rules_typed_help, presetTitle)
    }
    YSection(
        title = if (presetTitle == null) {
            stringResource(R.string.app_list_count, groupCount)
        } else {
            stringResource(R.string.app_list_count_type, groupCount, presetTitle)
        },
        subtitle = stringResource(R.string.rules_page_intro),
        detail = usageText,
        modifier = Modifier.padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp)
    ) {
        YStatusLine(
            label = stringResource(R.string.rules_summary_mode),
            value = modeText
        )
        if (state.displayMode == DisplayMode.SHOW_ALL) {
            YStatusLine(
                label = stringResource(R.string.rules_summary_status),
                value = stringResource(
                    if (state.runtime.ready) R.string.rules_pause_confirmed else R.string.rules_pause_pending
                ),
                tone = if (state.runtime.ready) YStatusTone.Good else YStatusTone.Warning
            )
        } else if (state.displayMode == DisplayMode.SHOW_SELECTED) {
            YStatusLine(
                label = stringResource(R.string.rules_summary_status),
                value = stringResource(R.string.rules_show_selected_empty)
            )
        }
        if (state.error != null) {
            YStatusLine(
                label = stringResource(R.string.rules_summary_status),
                value = stringResource(R.string.rules_refresh_incomplete),
                tone = YStatusTone.Error
            )
        }
    }
}

@Composable
fun PriorityTab(state: MainState, vm: MainViewModel) {
    PriorityDialogContent(state, vm)
}
