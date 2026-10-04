package com.yagay.YEntryCleaner.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.YEntryCleanerRuntime
import com.yagay.YEntryCleaner.domain.AppType
import com.yagay.YEntryCleaner.domain.VisibilityScope
import com.yagay.YEntryCleaner.domain.listCleanerAppType
import com.yagay.yui.YCheckboxItem
import com.yagay.yui.YDimens
import com.yagay.yui.YFilterSpec
import com.yagay.yui.YFullScreenDialog
import com.yagay.yui.YListSkeleton
import com.yagay.yui.YNotice
import com.yagay.yui.YNoticeTone
import com.yagay.yui.YPageRole
import com.yagay.yui.YSearchField
import com.yagay.yui.YSectionHeader
import com.yagay.yui.YStatusItem
import com.yagay.yui.YStatusTone
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YToggleFilterBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScopeAppEntry(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val normalizedLabel: String = label.lowercase(),
    val normalizedPackage: String = packageName.lowercase(),
)

private object ScopeIconCache {
    val icons = LruCache<String, Bitmap>(128)
}

@Suppress("DEPRECATION")
private fun loadScopeApps(pm: PackageManager, selfPackage: String): List<ScopeAppEntry> {
    val installed = if (android.os.Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getInstalledApplications(0)
    }
    return installed.asSequence()
        .filter { it.packageName != selfPackage && it.packageName != "android" }
        .map { info ->
            ScopeAppEntry(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName),
                system = info.listCleanerAppType() == AppType.SYSTEM,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<ScopeAppEntry> { it.normalizedLabel }.thenBy { it.packageName })
        .toList()
}

@Composable
private fun scopeAppIcon(packageName: String): Bitmap? {
    val context = LocalContext.current
    val cached = remember(packageName) { ScopeIconCache.icons.get(packageName) }
    val icon by produceState<Bitmap?>(initialValue = cached, packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(64, 64)
                }.getOrNull()?.also { ScopeIconCache.icons.put(packageName, it) }
            }
        }
    }
    return icon
}

@Composable
internal fun AppScopePickerDialog(
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as YEntryCleanerRuntime
    val visibilityScopes by app.rules.visibilityScopes.collectAsStateWithLifecycle()
    val fullPackages by app.rules.visibilityFullPackages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<ScopeAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadScopeApps(context.packageManager, context.packageName) }
        loading = false
    }

    val visible = remember(apps, query, showSystem, selected) {
        val needle = query.trim().lowercase()
        apps.filter { entry ->
            (showSystem || !entry.system) &&
                (needle.isEmpty() || entry.normalizedLabel.contains(needle) || entry.normalizedPackage.contains(needle))
        }.sortedWith(
            compareBy<ScopeAppEntry> { if (it.packageName in selected) 0 else 1 }
                .thenBy { it.normalizedLabel }
                .thenBy { it.packageName }
        )
    }
    val activeTargets = remember(visibilityScopes, fullPackages) {
        com.yagay.YEntryCleaner.domain.VisibilityCompatConfig(visibilityScopes, fullPackages).activePackages()
    }

    YFullScreenDialog(
        title = stringResource(R.string.visibility_title),
        backContentDescription = stringResource(R.string.common_back),
        onDismissRequest = dismiss,
        role = YPageRole.LIST,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = YDimens.ScreenHorizontal),
            verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) {
            YSectionHeader(
                title = stringResource(R.string.visibility_match_categories),
                subtitle = stringResource(R.string.visibility_help),
            )
            YToggleFilterBar(
                filters = VisibilityScope.entries.map { scope ->
                    val checked = scope in visibilityScopes
                    YFilterSpec(
                        label = stringResource(scope.titleRes()),
                        selected = checked,
                        onClick = {
                            app.rules.setVisibilityScopes(
                                if (checked) visibilityScopes - scope else visibilityScopes + scope
                            )
                        },
                    )
                },
            )
            YStatusItem(
                title = stringResource(R.string.visibility_match_categories),
                value = if (visibilityScopes.isEmpty()) {
                    stringResource(R.string.visibility_no_categories)
                } else {
                    stringResource(R.string.visibility_active_targets, activeTargets.size)
                },
                tone = if (visibilityScopes.isEmpty()) YStatusTone.Warning else YStatusTone.Good,
            )

            YSearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(R.string.visibility_search_sources),
            )
            YSwitchItem(
                title = stringResource(R.string.visibility_show_system),
                checked = showSystem,
                onCheckedChange = { showSystem = it },
            )
            YStatusItem(
                title = stringResource(R.string.visibility_title),
                value = stringResource(R.string.visibility_summary, visible.size, apps.size, selected.size),
            )

            when {
                loading -> YListSkeleton(rows = 5)
                visible.isEmpty() -> YNotice(
                    text = stringResource(R.string.no_matching_components),
                    tone = YNoticeTone.NEUTRAL,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.packageName }) { entry ->
                        val checked = entry.packageName in selected
                        val bitmap = scopeAppIcon(entry.packageName)
                        YCheckboxItem(
                            title = entry.label,
                            subtitle = entry.packageName,
                            detail = if (checked) stringResource(R.string.visibility_added) else null,
                            checked = checked,
                            onCheckedChange = { value ->
                                onSelectedChange(
                                    if (value) selected + entry.packageName
                                    else selected - entry.packageName
                                )
                            },
                            leading = bitmap?.let {
                                {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
