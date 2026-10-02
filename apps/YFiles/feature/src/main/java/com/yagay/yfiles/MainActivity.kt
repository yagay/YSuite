package com.yagay.yfiles

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.yagay.yui.YActionRow
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold
import com.yagay.yui.YSearchField
import com.yagay.yui.YSettingSwitch
import com.yagay.yui.YStatusRow
import com.yagay.yui.YStatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        val repository = remember { FileRepository(this) }
        var path by remember { mutableStateOf(intent?.getStringExtra("path") ?: repository.initialPath()) }
        var query by remember { mutableStateOf("") }
        var showHidden by remember { mutableStateOf(false) }
        var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
        var error by remember { mutableStateOf<String?>(null) }
        var newFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var refresh by remember { mutableStateOf(0) }
        var rootMode by remember { mutableStateOf(false) }
        var rootNames by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(path, query, showHidden, refresh, rootMode) {
            if (rootMode) {
                val result = withContext(Dispatchers.IO) { YFilesSuiteRuntime.rootList(path) }
                result.onSuccess { names -> rootNames = names; error = null }
                    .onFailure { error = it.message }
            } else {
                val result = withContext(Dispatchers.IO) { repository.list(path, showHidden, query) }
                result.onSuccess { entries = it; error = null }
                    .onFailure { error = it.message }
            }
        }

        YFeatureScaffold(
            title = stringResource(R.string.yfiles_title),
            subtitle = stringResource(R.string.yfiles_subtitle),
        ) { padding ->
            YFeatureList(padding) {
                item {
                    YFeatureCard(title = stringResource(R.string.location), subtitle = path) {
                        YSearchField(query, { query = it }, hint = stringResource(R.string.search_files))
                        YSettingSwitch(
                            title = stringResource(R.string.show_hidden),
                            checked = showHidden,
                            onCheckedChange = { showHidden = it },
                            enabled = !rootMode,
                        )
                        YSettingSwitch(
                            title = stringResource(R.string.root_mode),
                            checked = rootMode,
                            onCheckedChange = { rootMode = it },
                            subtitle = stringResource(R.string.root_mode_summary),
                            enabled = YFilesSuiteRuntime.rootAvailable(),
                        )
                        YActionRow {
                            OutlinedButton(
                                onClick = { repository.parent(path)?.let { path = it } },
                                enabled = repository.parent(path) != null,
                            ) { Text(stringResource(R.string.parent)) }
                            OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.refresh)) }
                            Button(onClick = { newFolderDialog = true }, enabled = !rootMode) { Text(stringResource(R.string.new_folder)) }
                        }
                    }
                }

                item {
                    YFeatureCard(title = stringResource(R.string.access_status), subtitle = stringResource(R.string.access_status_summary)) {
                        YStatusRow(
                            stringResource(R.string.all_files_access),
                            if (Environment.isExternalStorageManager()) stringResource(R.string.granted) else stringResource(R.string.not_granted),
                            if (Environment.isExternalStorageManager()) YStatusTone.Good else YStatusTone.Warning,
                        )
                        YStatusRow(
                            stringResource(R.string.root_access),
                            if (YFilesSuiteRuntime.rootAvailable()) stringResource(R.string.available) else stringResource(R.string.unavailable),
                            if (YFilesSuiteRuntime.rootAvailable()) YStatusTone.Good else YStatusTone.Neutral,
                        )
                        if (!Environment.isExternalStorageManager()) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                                }
                            }) { Text(stringResource(R.string.open_settings)) }
                        }
                    }
                }

                error?.let { item { YFeatureCard(title = stringResource(R.string.error), detail = it) } }

                if (rootMode) {
                    items(rootNames, key = { it }) { name ->
                        YFeatureCard(title = name, subtitle = stringResource(R.string.root_entry))
                    }
                } else if (entries.isEmpty()) {
                    item { YFeatureCard(title = stringResource(R.string.empty_folder)) }
                } else {
                    items(entries, key = { it.path }) { entry ->
                        YFeatureCard(
                            title = entry.name,
                            subtitle = if (entry.isDirectory) stringResource(R.string.folder) else formatBytes(entry.size),
                            detail = entry.path,
                        ) {
                            YActionRow {
                                if (entry.isDirectory) {
                                    Button(onClick = { path = entry.path; query = "" }) { Text(stringResource(R.string.open)) }
                                } else {
                                    Button(onClick = { openFile(File(entry.path)) }) { Text(stringResource(R.string.open)) }
                                }
                                OutlinedButton(onClick = {
                                    repository.delete(entry).onFailure { error = it.message }
                                    refresh++
                                }) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }

                item {
                    YFeatureCard(
                        title = stringResource(R.string.documentsui_integration),
                        subtitle = stringResource(R.string.documentsui_summary),
                    ) {
                        YStatusRow(stringResource(R.string.documentsui), stringResource(R.string.preserved), YStatusTone.Good)
                        Text(stringResource(R.string.documentsui_note))
                    }
                }
            }
        }

        if (newFolderDialog) {
            AlertDialog(
                onDismissRequest = { newFolderDialog = false },
                title = { Text(stringResource(R.string.new_folder)) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.folder_name)) },
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        repository.createFolder(path, newFolderName).onFailure { error = it.message }
                        newFolderName = ""
                        newFolderDialog = false
                        refresh++
                    }) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    OutlinedButton(onClick = { newFolderDialog = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }

    private fun openFile(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.yfiles.files", file)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            YFilesSuiteRuntime.log(com.yagay.suite.api.HostLogLevel.WARN, "open file failed: ${file.absolutePath}", it)
        }
    }

    private fun formatBytes(value: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024.0 && unit < units.lastIndex) { size /= 1024.0; unit++ }
        return if (unit == 0) "$value B" else "%.1f %s".format(size, units[unit])
    }
}
