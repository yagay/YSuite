package com.yagay.YEntryCleaner.ui

import com.yagay.yui.YDimens
import com.yagay.yui.YFilterBar
import com.yagay.yui.YUiOutlinedTextField as OutlinedTextField
import com.yagay.yui.LocalYAppearance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import com.yagay.yui.YUiDropdownMenu as DropdownMenu
import com.yagay.yui.YUiDropdownMenuItem as DropdownMenuItem
import com.yagay.yui.YUiHorizontalDivider as HorizontalDivider
import com.yagay.yui.YUiIcon as Icon
import com.yagay.yui.YUiLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiText as Text
import com.yagay.yui.YUiTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.IntentKind
import com.yagay.YEntryCleaner.domain.AppTypeFilter
import com.yagay.yui.YActionSpec
import com.yagay.yui.YCustomTopBar
import com.yagay.yui.YIconAction
import com.yagay.yui.YIcons
import com.yagay.yui.YOverflowMenu

@Composable
internal fun CompactSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        singleLine = true,
        placeholder = {
            Text(
                stringResource(R.string.search_apps_components_packages),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
    )
}
@Composable
internal fun MainToolbar(
    query: String,
    expanded: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRestore: () -> Unit,
    onBackup: () -> Unit
) {
    YCustomTopBar(
        title = {
            if (expanded) CompactSearchField(query, onQuery)
            else Text(stringResource(R.string.yentrycleaner_app_name), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            if (expanded) {
                YIconAction(
                    icon = YIcons.Back,
                    contentDescription = stringResource(R.string.close_search),
                    onClick = onClose,
                )
            }
        },
        actions = {
            if (expanded) {
                if (query.isNotEmpty()) {
                    YIconAction(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        onClick = { onQuery("") },
                    )
                }
            } else {
                YIconAction(
                    icon = YIcons.Search,
                    contentDescription = stringResource(R.string.common_search),
                    onClick = onSearch,
                )
                YIconAction(
                    icon = YIcons.Refresh,
                    contentDescription = stringResource(R.string.common_refresh),
                    onClick = onRefresh,
                )
                YOverflowMenu(
                    actions = listOf(
                        YActionSpec(
                            label = stringResource(R.string.restore_backup),
                            onClick = onRestore,
                        ),
                        YActionSpec(
                            label = stringResource(R.string.export_backup),
                            onClick = onBackup,
                        ),
                    )
                )
            }
        }
    )
}

@Composable
internal fun ListControls(
    state: MainState,
    onFilter: (IntentKind?) -> Unit,
    onUiFilter: (UiFilter) -> Unit,
    appTypeFilter: AppTypeFilter = AppTypeFilter.ALL,
    onAppTypeFilter: (AppTypeFilter) -> Unit = {},
    includeAllKinds: Boolean = true,
    viewTitle: @Composable (UiFilter) -> String = { stringResource(it.titleRes()) },
    extraFilter: (@Composable () -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onSelectNone: (() -> Unit)? = null,
    onInvert: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    var appTypeMenu by remember { mutableStateOf(false) }
    var selectionMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        val kinds: List<IntentKind?> =
            (if (includeAllKinds) listOf<IntentKind?>(null) else emptyList()) + IntentKind.entries
        val kindLabels = kinds.map { kind ->
            kind?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.common_all)
        }
        YFilterBar(
            options = kindLabels,
            selectedIndex = kinds.indexOf(state.filter),
            onSelected = { index -> onFilter(kinds[index]) },
            modifier = Modifier.padding(horizontal = LocalYAppearance.current.rowHorizontalPaddingDp.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = LocalYAppearance.current.rowHorizontalPaddingDp.dp),
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
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 2f / 3f).dp))
                }
                DropdownMenu(expanded = appTypeMenu, onDismissRequest = { appTypeMenu = false }) {
                    AppTypeFilter.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.titleRes())) },
                            leadingIcon = { if (mode == appTypeFilter) Icon(Icons.Rounded.Check, null) },
                            onClick = { appTypeMenu = false; onAppTypeFilter(mode) }
                        )
                    }
                }
            }
            Box {
                TextButton(
                    onClick = { menu = true },
                    contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.compact_filter_format,
                            stringResource(R.string.view_filter),
                            viewTitle(state.uiFilter)
                        )
                    )
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 2f / 3f).dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    UiFilter.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(viewTitle(mode)) },
                            leadingIcon = { if (mode == state.uiFilter) Icon(Icons.Rounded.Check, null) },
                            onClick = { menu = false; onUiFilter(mode) }
                        )
                    }
                }
            }
            extraFilter?.invoke()
            if (onSelectAll != null || onSelectNone != null || onInvert != null) {
                Box {
                    TextButton(
                        onClick = { selectionMenu = true },
                        contentPadding = PaddingValues(horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp, vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp)
                    ) {
                        Text(stringResource(R.string.selection_actions))
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 2f / 3f).dp))
                    }
                    DropdownMenu(
                        expanded = selectionMenu,
                        onDismissRequest = { selectionMenu = false }
                    ) {
                        onSelectAll?.let { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_all)) },
                                onClick = {
                                    selectionMenu = false
                                    action()
                                }
                            )
                        }
                        onSelectNone?.let { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.select_none)) },
                                onClick = {
                                    selectionMenu = false
                                    action()
                                }
                            )
                        }
                        onInvert?.let { action ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.invert_selection)) },
                                onClick = {
                                    selectionMenu = false
                                    action()
                                }
                            )
                        }
                    }
                }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        HorizontalDivider()
    }
}

@Composable
internal fun ModuleStatusRow(state: MainState, compact: Boolean = false, onClick: () -> Unit) {
    val status = state.module
    val titleRes = when {
        !status.connected -> R.string.module_lsposed_disconnected
        status.outdated -> R.string.module_old_running
        status.error != null || (status.scopeKnown && status.missingScope.isNotEmpty()) -> R.string.module_needs_attention
        status.resolverLoaded -> R.string.module_loaded
        else -> R.string.module_lsposed_connected
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = LocalYAppearance.current.rowHorizontalPaddingDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
            Text(
                if (!compact) state.syncStatus
                else if (state.runtime.ready) stringResource(R.string.module_system_confirmed)
                else stringResource(R.string.module_check_status),
                style = MaterialTheme.typography.labelSmall,
                color = if (status.outdated || !state.runtime.ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Rounded.ExpandMore, stringResource(R.string.module_view_status))
    }
}
