package com.yagay.YSuite

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yagay.suite.core.FeatureSpec
import com.yagay.yui.YDimens

private const val HOME_SETTINGS_NAME = "ysuite_compact_home"
private const val PINNED_IDS_KEY = "pinned_ids"

internal data class HomeModuleEntry(
    val feature: FeatureSpec,
    val label: String,
    val description: String,
    val enabled: Boolean,
)

internal fun orderedHomeIds(ids: List<String>, pinned: Set<String>): List<String> =
    ids.filter { it in pinned } + ids.filterNot { it in pinned }

/**
 * Compact host-only home. Feature runtime, standalone settings and diagnostic actions
 * remain on the existing management screen; this component only changes presentation.
 */
@Composable
internal fun CompactSuiteHome(
    modules: List<HomeModuleEntry>,
    rootAvailable: Boolean?,
    xposedConnected: Boolean,
    padding: PaddingValues,
    onOpen: (FeatureSpec, String) -> Unit,
    onManage: (String?) -> Unit,
    onToggleEnabled: (FeatureSpec, Boolean) -> Unit,
    onExportModule: (FeatureSpec, String) -> Unit,
    onExportAll: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(HOME_SETTINGS_NAME, Context.MODE_PRIVATE)
    }
    var pinned by remember(prefs) {
        mutableStateOf(prefs.getStringSet(PINNED_IDS_KEY, emptySet())?.toSet().orEmpty())
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    fun setPinned(id: String) {
        val updated = if (id in pinned) pinned - id else pinned + id
        pinned = updated
        prefs.edit().putStringSet(PINNED_IDS_KEY, updated.toSet()).apply()
    }

    // Do not sort the registry itself: preserve its order for existing feature contracts.
    val moduleById = modules.associateBy { it.feature.id }
    val ordered = orderedHomeIds(modules.map { it.feature.id }, pinned)
        .mapNotNull(moduleById::get)
    val visible = ordered.filter { module ->
        val query = search.trim()
        query.isEmpty() ||
            module.label.contains(query, ignoreCase = true) ||
            module.description.contains(query, ignoreCase = true) ||
            module.feature.id.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
    ) {
        item(key = "home_status") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = YDimens.SectionGap, vertical = YDimens.SpacingXsmall)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.shapes.medium,
                    )
                    .clickable { onManage(null) }
                    .padding(horizontal = YDimens.ScreenHorizontal, vertical = YDimens.ControlGap),
                horizontalArrangement = Arrangement.spacedBy(YDimens.ScreenHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.capability_root) + ": " +
                        when (rootAvailable) {
                            true -> stringResource(R.string.status_authorized)
                            false -> stringResource(R.string.status_not_authorized)
                            null -> stringResource(R.string.status_checking)
                        },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rootAvailable == true) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.capability_lsposed) + ": " +
                        stringResource(
                            if (xposedConnected) R.string.status_connected
                            else R.string.status_not_connected,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    color = if (xposedConnected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        item(key = "home_actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_modules),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${visible.size}/${modules.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) search = ""
                }) {
                    Icon(
                        imageVector = if (searching) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = stringResource(R.string.home_search),
                    )
                }
                IconButton(onClick = onExportAll) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = stringResource(R.string.export_full_diagnostic),
                    )
                }
                IconButton(onClick = { onManage(null) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.home_manage),
                    )
                }
            }
        }

        if (searching) {
            item(key = "home_search_field") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = YDimens.ScreenHorizontal, vertical = YDimens.SpacingXsmall),
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
        }

        if (visible.isEmpty()) {
            item(key = "home_empty") {
                Text(
                    text = stringResource(R.string.home_no_match),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(items = visible, key = { it.feature.id }) { module ->
                CompactSuiteModuleRow(
                    module = module,
                    isPinned = module.feature.id in pinned,
                    onOpen = {
                        if (module.enabled) onOpen(module.feature, module.label)
                        else onManage(module.feature.id)
                    },
                    onManage = { onManage(module.feature.id) },
                    onToggleEnabled = { onToggleEnabled(module.feature, !module.enabled) },
                    onPin = { setPinned(module.feature.id) },
                    onExport = { onExportModule(module.feature, module.label) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSuiteModuleRow(
    module: HomeModuleEntry,
    isPinned: Boolean,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onToggleEnabled: () -> Unit,
    onPin: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = YDimens.OptionRowHeight)
            .pointerInput(isPinned) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = {
                        if ((dragDistance > 100f && !isPinned) ||
                            (dragDistance < -100f && isPinned)
                        ) onPin()
                    },
                    onHorizontalDrag = { change, delta ->
                        dragDistance += delta
                        change.consume()
                    },
                )
            }
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
            .padding(start = YDimens.ScreenHorizontal, end = YDimens.SpacingXsmall, top = YDimens.SpacingSmall, bottom = YDimens.SpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YDimens.SectionGap),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (module.feature.id) {
                        "yentrycleaner" -> Icons.Default.ListAlt
                        "ydiag" -> Icons.Default.BugReport
                        "ynotify" -> Icons.Default.Notifications
                        "ypower" -> Icons.Default.Security
                        "yminiguard" -> Icons.Default.PictureInPictureAlt
                        "ynfc" -> Icons.Default.Nfc
                        "ytaskmanager" -> Icons.Default.Memory
                        "yparam" -> Icons.Default.Tune
                        "yfloat" -> Icons.Default.TouchApp
                        "ydownload" -> Icons.Default.FileDownload
                        "yfiles" -> Icons.Default.Folder
                        else -> Icons.Default.Apps
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (module.enabled) module.description else stringResource(R.string.home_disabled),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPinned) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(R.string.home_pinned),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.home_manage),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_manage)) },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                    onClick = { menuOpen = false; onManage() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (isPinned) R.string.home_unpin else R.string.home_pin)) },
                    leadingIcon = { Icon(if (isPinned) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null) },
                    onClick = { menuOpen = false; onPin() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (module.enabled) R.string.home_disable else R.string.home_enable)) },
                    onClick = { menuOpen = false; onToggleEnabled() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.diagnostic_package)) },
                    onClick = { menuOpen = false; onExport() },
                )
            }
        }
    }
}
