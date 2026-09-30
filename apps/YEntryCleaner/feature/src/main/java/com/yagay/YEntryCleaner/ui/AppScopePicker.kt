package com.yagay.YEntryCleaner.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.YEntryCleanerRuntime
import com.yagay.YEntryCleaner.domain.AppType
import com.yagay.YEntryCleaner.domain.VisibilityScope
import com.yagay.YEntryCleaner.domain.listCleanerAppType
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureEmpty
import com.yagay.yui.YSearchField
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.visibility_title)) },
                    navigationIcon = {
                        IconButton(onClick = dismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YFeatureCard(
                    title = stringResource(R.string.visibility_match_categories),
                    subtitle = stringResource(R.string.visibility_help)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(VisibilityScope.entries, key = { it.name }) { scope ->
                            val checked = scope in visibilityScopes
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    app.rules.setVisibilityScopes(
                                        if (checked) visibilityScopes - scope else visibilityScopes + scope
                                    )
                                },
                                label = { Text(stringResource(scope.titleRes())) },
                            )
                        }
                    }
                    YStatusRow(
                        label = stringResource(R.string.visibility_match_categories),
                        value = if (visibilityScopes.isEmpty()) {
                            stringResource(R.string.visibility_no_categories)
                        } else {
                            stringResource(R.string.visibility_active_targets, activeTargets.size)
                        },
                        tone = if (visibilityScopes.isEmpty()) YStatusTone.Warning else YStatusTone.Good
                    )
                }

                YSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(R.string.visibility_search_sources)
                )
                YSettingSwitch(
                    title = stringResource(R.string.visibility_show_system),
                    checked = showSystem,
                    onCheckedChange = { showSystem = it }
                )
                YStatusRow(
                    label = stringResource(R.string.visibility_title),
                    value = stringResource(R.string.visibility_summary, visible.size, apps.size, selected.size)
                )

                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    visible.isEmpty() -> {
                        YFeatureEmpty(
                            message = stringResource(R.string.no_matching_components),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                        )
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(visible, key = { it.packageName }) { entry ->
                                val checked = entry.packageName in selected
                                val bitmap = scopeAppIcon(entry.packageName)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectedChange(
                                                if (checked) selected - entry.packageName
                                                else selected + entry.packageName
                                            )
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    bitmap?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { value ->
                                            onSelectedChange(
                                                if (value) selected + entry.packageName
                                                else selected - entry.packageName
                                            )
                                        },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(entry.label, fontWeight = FontWeight.Medium)
                                        Text(
                                            entry.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (checked) {
                                        Text(
                                            stringResource(R.string.visibility_added),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
