package com.yagay.yfiles

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YSection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YFilesRootEntryCard(
    parentPath: String,
    name: String,
    onNavigate: (String) -> Unit,
    onChanged: () -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fullPath = remember(parentPath, name) {
        if (parentPath == "/") "/$name" else parentPath.trimEnd('/') + "/" + name
    }
    var busy by remember(fullPath) { mutableStateOf(false) }
    var renameDialog by remember(fullPath) { mutableStateOf(false) }
    var renameValue by remember(fullPath) { mutableStateOf(name) }
    var deleteDialog by remember(fullPath) { mutableStateOf(false) }
    var transferMode by remember(fullPath) { mutableStateOf<FileTransferMode?>(null) }
    var destinationPath by remember(fullPath) { mutableStateOf(parentPath) }

    fun runRoot(block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        scope.launch {
            val result = block()
            result.onSuccess {
                onError(null)
                onChanged()
            }.onFailure { onError(it.message ?: it.javaClass.simpleName) }
            busy = false
        }
    }

    YSection(
        title = name,
        subtitle = stringResource(R.string.root_entry),
        detail = fullPath,
    ) {
        YHorizontalActions {
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val directory = withContext(Dispatchers.IO) {
                            YFilesSuiteRuntime.rootIsDirectory(fullPath)
                        }
                        directory.onSuccess { isDirectory ->
                            if (isDirectory) {
                                onNavigate(fullPath)
                                onError(null)
                            } else {
                                val staged = withContext(Dispatchers.IO) {
                                    YFilesSuiteRuntime.rootStageFile(fullPath)
                                }
                                staged.onSuccess { file ->
                                    runCatching { openStagedFile(context, file) }
                                        .onFailure { onError(it.message ?: it.javaClass.simpleName) }
                                }.onFailure { onError(it.message ?: it.javaClass.simpleName) }
                            }
                        }.onFailure { onError(it.message ?: it.javaClass.simpleName) }
                        busy = false
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.yfiles_open)) }
            OutlinedButton(
                onClick = {
                    if (busy) return@OutlinedButton
                    busy = true
                    scope.launch {
                        val staged = withContext(Dispatchers.IO) {
                            YFilesSuiteRuntime.rootStageFile(fullPath)
                        }
                        staged.onSuccess { file ->
                            runCatching { shareStagedFile(context, file) }
                                .onFailure { onError(it.message ?: it.javaClass.simpleName) }
                        }.onFailure { onError(it.message ?: it.javaClass.simpleName) }
                        busy = false
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.share)) }
            OutlinedButton(
                onClick = {
                    transferMode = FileTransferMode.COPY
                    destinationPath = parentPath
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.copy)) }
            OutlinedButton(
                onClick = {
                    transferMode = FileTransferMode.MOVE
                    destinationPath = parentPath
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.move)) }
        }
        YHorizontalActions {
            OutlinedButton(
                onClick = {
                    renameValue = name
                    renameDialog = true
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.rename)) }
            OutlinedButton(
                onClick = { deleteDialog = true },
                enabled = !busy,
            ) { Text(stringResource(R.string.delete)) }
        }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) renameDialog = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.new_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootRename(fullPath, renameValue).map { Unit }
                            }.also { result -> if (result.isSuccess) renameDialog = false }
                        }
                    },
                    enabled = renameValue.isNotBlank() && !busy,
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { renameDialog = false }, enabled = !busy) {
                    Text(stringResource(R.string.yfiles_cancel))
                }
            },
        )
    }

    transferMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { if (!busy) transferMode = null },
            title = {
                Text(
                    if (mode == FileTransferMode.COPY) {
                        stringResource(R.string.copy)
                    } else {
                        stringResource(R.string.move)
                    },
                )
            },
            text = {
                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    label = { Text(stringResource(R.string.destination_folder)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootTransfer(fullPath, destinationPath.trim(), mode).map { Unit }
                            }.also { result -> if (result.isSuccess) transferMode = null }
                        }
                    },
                    enabled = destinationPath.isNotBlank() && !busy,
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { transferMode = null }, enabled = !busy) {
                    Text(stringResource(R.string.yfiles_cancel))
                }
            },
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) deleteDialog = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_summary, name)) },
            confirmButton = {
                Button(
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootDelete(fullPath)
                            }.also { result -> if (result.isSuccess) deleteDialog = false }
                        }
                    },
                    enabled = !busy,
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteDialog = false }, enabled = !busy) {
                    Text(stringResource(R.string.yfiles_cancel))
                }
            },
        )
    }
}

private fun openStagedFile(context: android.content.Context, file: File) {
    val uri = requireNotNull(YFilesSuiteRuntime.sharedFileUri(file)) {
        "Host file-share capability is unavailable"
    }
    val mime = context.contentResolver.getType(uri) ?: "*/*"
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun shareStagedFile(context: android.content.Context, file: File) {
    val uri = requireNotNull(YFilesSuiteRuntime.sharedFileUri(file)) {
        "Host file-share capability is unavailable"
    }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            context.getString(R.string.share),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
