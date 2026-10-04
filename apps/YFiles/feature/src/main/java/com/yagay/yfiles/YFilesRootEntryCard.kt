package com.yagay.yfiles

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YFormDialog
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YTextField
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
            YPrimaryButton(
                text = stringResource(R.string.yfiles_open),
                onClick = {
                    if (busy) return@YPrimaryButton
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
            )
            YSecondaryButton(
                text = stringResource(R.string.share),
                onClick = {
                    if (busy) return@YSecondaryButton
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
            )
            YSecondaryButton(
                text = stringResource(R.string.copy),
                onClick = {
                    transferMode = FileTransferMode.COPY
                    destinationPath = parentPath
                },
                enabled = !busy,
            )
            YSecondaryButton(
                text = stringResource(R.string.move),
                onClick = {
                    transferMode = FileTransferMode.MOVE
                    destinationPath = parentPath
                },
                enabled = !busy,
            )
        }
        YHorizontalActions {
            YSecondaryButton(
                text = stringResource(R.string.rename),
                onClick = {
                    renameValue = name
                    renameDialog = true
                },
                enabled = !busy,
            )
            YSecondaryButton(
                text = stringResource(R.string.delete),
                onClick = { deleteDialog = true },
                enabled = !busy,
            )
        }
    }

    if (renameDialog) {
        YFormDialog(
            title = stringResource(R.string.rename),
            onDismissRequest = { if (!busy) renameDialog = false },
            actions = listOf(
                YActionSpec(
                    label = stringResource(R.string.yfiles_cancel),
                    enabled = !busy,
                    onClick = { renameDialog = false },
                ),
                YActionSpec(
                    label = stringResource(R.string.rename),
                    enabled = renameValue.isNotBlank() && !busy,
                    style = YActionStyle.PRIMARY,
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootRename(fullPath, renameValue).map { Unit }
                            }.also { result -> if (result.isSuccess) renameDialog = false }
                        }
                    },
                ),
            ),
        ) {
            YTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                label = stringResource(R.string.new_name),
            )
        }
    }

    transferMode?.let { mode ->
        YFormDialog(
            title = if (mode == FileTransferMode.COPY) {
                stringResource(R.string.copy)
            } else {
                stringResource(R.string.move)
            },
            onDismissRequest = { if (!busy) transferMode = null },
            actions = listOf(
                YActionSpec(
                    label = stringResource(R.string.yfiles_cancel),
                    enabled = !busy,
                    onClick = { transferMode = null },
                ),
                YActionSpec(
                    label = stringResource(R.string.confirm),
                    enabled = destinationPath.isNotBlank() && !busy,
                    style = YActionStyle.PRIMARY,
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootTransfer(fullPath, destinationPath.trim(), mode).map { Unit }
                            }.also { result -> if (result.isSuccess) transferMode = null }
                        }
                    },
                ),
            ),
        ) {
            YTextField(
                value = destinationPath,
                onValueChange = { destinationPath = it },
                label = stringResource(R.string.destination_folder),
            )
        }
    }

    if (deleteDialog) {
        YFormDialog(
            title = stringResource(R.string.confirm_delete),
            onDismissRequest = { if (!busy) deleteDialog = false },
            actions = listOf(
                YActionSpec(
                    label = stringResource(R.string.yfiles_cancel),
                    enabled = !busy,
                    onClick = { deleteDialog = false },
                ),
                YActionSpec(
                    label = stringResource(R.string.delete),
                    enabled = !busy,
                    style = YActionStyle.DANGER,
                    onClick = {
                        runRoot {
                            withContext(Dispatchers.IO) {
                                YFilesSuiteRuntime.rootDelete(fullPath)
                            }.also { result -> if (result.isSuccess) deleteDialog = false }
                        }
                    },
                ),
            ),
        ) {
            Text(stringResource(R.string.confirm_delete_summary, name))
        }
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
