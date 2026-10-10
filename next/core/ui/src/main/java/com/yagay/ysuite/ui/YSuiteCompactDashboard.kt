package com.yagay.ysuite.ui

import com.yagay.yui.LocalYAppearance
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import com.yagay.yui.YUiDropdownMenuItem as DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.yagay.yui.YUiIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.yagay.yui.YUiOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.productui.dashboard.NiaDashboardSurface
import com.yagay.ysuite.resources.R

private const val HOME_PREFERENCES = "ysuite_compact_home"
private const val PINNED_MODULES_KEY = "pinned_modules"

private data class HomeModule(
    val id: String,
    val label: String,
)

/** Keep the registry's existing order inside each group; only pinned modules move to the top. */
internal fun compactModuleOrder(ids: List<String>, pinned: Set<String>): List<String> =
    ids.filter { it in pinned } + ids.filterNot { it in pinned }

@Composable
internal fun YSuiteCompactDashboard(
    features: List<YSuiteFeatureUiRegistration>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(HOME_PREFERENCES, Context.MODE_PRIVATE)
    }
    var pinnedIds by remember(preferences) {
        mutableStateOf(preferences.getStringSet(PINNED_MODULES_KEY, emptySet())?.toSet().orEmpty())
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // Read composable labels on every recomposition so a locale change updates the search results.
    val modules = mutableListOf<HomeModule>()
    for (feature in features) {
        val id = feature.contract.descriptor.id
        if (id != "system" && id != "settings") {
            modules += HomeModule(id = id, label = feature.label())
        }
    }
    val orderedIds = compactModuleOrder(modules.map { it.id }, pinnedIds)
    val byId = modules.associateBy { it.id }
    val shown = orderedIds.mapNotNull(byId::get).filter { module ->
        query.isBlank() ||
            module.label.contains(query.trim(), ignoreCase = true) ||
            module.id.contains(query.trim(), ignoreCase = true)
    }
    val systemId = features.firstOrNull { it.contract.descriptor.id == "system" }?.contract?.descriptor?.id
    val settingsId = features.firstOrNull { it.contract.descriptor.id == "settings" }?.contract?.descriptor?.id
    val logsId = features.firstOrNull { it.contract.descriptor.id == "ydiag" }?.contract?.descriptor?.id

    fun togglePin(id: String) {
        val next = if (id in pinnedIds) pinnedIds - id else pinnedIds + id
        pinnedIds = next
        preferences.edit().putStringSet(PINNED_MODULES_KEY, next.toSet()).apply()
    }

    NiaDashboardSurface(
        title = stringResource(R.string.app_name),
        navigationIcon = { YSuiteHostNavigationButton() },
        actions = {
            IconButton(onClick = {
                searching = !searching
                if (!searching) query = ""
            }) {
                Icon(
                    imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = stringResource(
                        if (searching) R.string.common_close else R.string.common_search,
                    ),
                )
            }
            if (logsId != null) {
                IconButton(onClick = { onSelect(logsId) }) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = stringResource(R.string.home_logs),
                    )
                }
            }
            if (settingsId != null) {
                IconButton(onClick = { onSelect(settingsId) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.common_settings),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (systemId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalYAppearance.current.effectiveGapDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.shapes.medium,
                        )
                        .clickable { onSelect(systemId) }
                        .padding(horizontal = LocalYAppearance.current.screenPaddingDp.dp, vertical = LocalYAppearance.current.effectiveGapDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LocalYAppearance.current.sectionSpacingDp.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(LocalYAppearance.current.iconVisualSizeDp.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_system_status),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = stringResource(R.string.home_system_status_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = Int.MAX_VALUE,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(LocalYAppearance.current.iconVisualSizeDp.dp),
                    )
                }
            }

            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LocalYAppearance.current.effectiveGapDp.dp, vertical = LocalYAppearance.current.rowVerticalPaddingDp.dp),
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_search_modules)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = LocalYAppearance.current.screenPaddingDp.dp, end = LocalYAppearance.current.effectiveGapDp.dp, top = LocalYAppearance.current.effectiveGapDp.dp, bottom = LocalYAppearance.current.rowVerticalPaddingDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_modules),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${shown.size}/${modules.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (shown.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (modules.isEmpty()) R.string.home_no_features else R.string.home_no_matches,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = shown, key = { it.id }) { module ->
                        CompactModuleRow(
                            label = module.label,
                            pinned = module.id in pinnedIds,
                            onOpen = { onSelect(module.id) },
                            onTogglePin = { togglePin(module.id) },
                        )
                        HorizontalDivider(
                            thickness = (LocalYAppearance.current.effectiveGapDp / 24f).coerceAtLeast(0.5f).dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactModuleRow(
    label: String,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalYAppearance.current.rowHeightDp.dp)
            // Horizontal gestures do not intercept the LazyColumn's vertical scrolling.
            // Swipe right to pin, left to unpin; the menu remains the accessible fallback.
            .pointerInput(pinned) {
                var horizontalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onDragEnd = {
                        if ((horizontalDrag > 100f && !pinned) ||
                            (horizontalDrag < -100f && pinned)
                        ) {
                            onTogglePin()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        horizontalDrag += dragAmount
                        change.consume()
                    },
                )
            }
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { menuExpanded = true },
            )
            .padding(start = LocalYAppearance.current.screenPaddingDp.dp, end = LocalYAppearance.current.rowVerticalPaddingDp.dp, top = LocalYAppearance.current.rowVerticalPaddingDp.dp, bottom = LocalYAppearance.current.rowVerticalPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalYAppearance.current.sectionSpacingDp.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(LocalYAppearance.current.listIconSizeDp.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (pinned) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.home_pinned),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size((LocalYAppearance.current.iconVisualSizeDp * 0.75f).dp),
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.common_menu),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (pinned) R.string.home_unpin else R.string.home_pin))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (pinned) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_open_module)) },
                    onClick = {
                        menuExpanded = false
                        onOpen()
                    },
                )
            }
        }
    }
}
