package com.yagay.yfiles

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object YFilesBatchSelectionState {
    val selected = mutableStateMapOf<String, FileEntry>()
    var pendingMode by mutableStateOf<FileTransferMode?>(null)

    fun toggle(entry: FileEntry) {
        if (selected.containsKey(entry.path)) selected.remove(entry.path) else selected[entry.path] = entry
        if (selected.isEmpty()) pendingMode = null
    }

    fun selectAll(entries: List<FileEntry>) {
        entries.forEach { selected[it.path] = it }
    }

    fun clear() {
        selected.clear()
        pendingMode = null
    }
}

@Composable
fun YFilesSelectionToggle(entry: FileEntry) {
    val selected = YFilesBatchSelectionState.selected.containsKey(entry.path)
    YSecondaryButton(
        text = if (selected) {
                stringResource(R.string.yfiles_selected)
            } else {
                stringResource(R.string.yfiles_select)
            },
        onClick = { YFilesBatchSelectionState.toggle(entry) },
    )
}

@Composable
fun YFilesBatchToolbar(
    path: String,
    onChanged: () -> Unit,
    onError: (String?) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { FileRepository(context) }
    val extrasStore = remember { YFilesExtrasStore(context) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    // Reading a snapshot from a SnapshotStateMap makes this composable react to selection changes.
    val selected = YFilesBatchSelectionState.selected.values.toList()
    val pendingMode = YFilesBatchSelectionState.pendingMode

    if (selected.isEmpty()) {
        YSecondaryButton(
            text = stringResource(R.string.yfiles_select_folder_items),
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.list(path, showHidden = false, query = "").getOrThrow()
                    }
                    runCatching { result }
                        .onSuccess {
                            YFilesBatchSelectionState.selectAll(it)
                            onError(null)
                        }
                        .onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        )
        return
    }

    YStatusLine(
        stringResource(R.string.yfiles_batch_selection),
        stringResource(R.string.yfiles_selected_count, selected.size),
        YStatusTone.Warning,
    )

    pendingMode?.let { mode ->
        val modeLabel = if (mode == FileTransferMode.COPY) {
            stringResource(R.string.copy)
        } else {
            stringResource(R.string.move)
        }
        YStatusLine(
            stringResource(R.string.file_clipboard),
            stringResource(R.string.yfiles_batch_pending, modeLabel, selected.size),
            YStatusTone.Warning,
        )
        YHorizontalActions {
            YPrimaryButton(
                text = stringResource(R.string.paste_here),
                onClick = {
                    busy = true
                    scope.launch {
                        val snapshot = YFilesBatchSelectionState.selected.values.toList()
                        val results = withContext(Dispatchers.IO) {
                            snapshot.map { entry ->
                                entry to repository.transfer(PendingFileTransfer(entry, mode), path)
                            }
                        }
                        val succeeded = results.filter { it.second.isSuccess }.map { it.first.path }.toSet()
                        succeeded.forEach(YFilesBatchSelectionState.selected::remove)
                        val failures = results.mapNotNull { it.second.exceptionOrNull() }
                        if (failures.isEmpty()) {
                            YFilesBatchSelectionState.clear()
                            onError(null)
                        } else {
                            onError(
                                context.getString(
                                    R.string.yfiles_batch_partial_failure,
                                    succeeded.size,
                                    failures.size,
                                    failures.first().message ?: context.getString(R.string.error),
                                ),
                            )
                        }
                        onChanged()
                        busy = false
                    }
                },
                enabled = !busy,
            )
            YSecondaryButton(
                text = stringResource(R.string.yfiles_cancel),
                onClick = { YFilesBatchSelectionState.pendingMode = null },
                enabled = !busy,
            )
        }
    }

    YHorizontalActions {
        YSecondaryButton(
            text = stringResource(R.string.yfiles_select_folder_items),
            onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.list(path, showHidden = false, query = "").getOrThrow()
                    }
                    runCatching { result }
                        .onSuccess {
                            YFilesBatchSelectionState.selectAll(it)
                            onError(null)
                        }
                        .onFailure { onError(it.message) }
                    busy = false
                }
            },
            enabled = !busy,
        )
        YSecondaryButton(
            text = stringResource(R.string.yfiles_clear_selection),
            onClick = { YFilesBatchSelectionState.clear() },
            enabled = !busy,
        )
    }

    YHorizontalActions {
        YSecondaryButton(
            text = stringResource(R.string.copy),
            onClick = { YFilesBatchSelectionState.pendingMode = FileTransferMode.COPY },
            enabled = !busy,
        )
        YSecondaryButton(
            text = stringResource(R.string.move),
            onClick = { YFilesBatchSelectionState.pendingMode = FileTransferMode.MOVE },
            enabled = !busy,
        )
        YSecondaryButton(
            text = stringResource(R.string.share),
            onClick = {
                val uris = selected
                    .asSequence()
                    .filterNot(FileEntry::isDirectory)
                    .mapNotNull { entry -> YFilesSuiteRuntime.sharedFileUri(File(entry.path)) }
                    .toList()
                if (uris.isEmpty()) {
                    onError(context.getString(R.string.yfiles_batch_share_files_only))
                } else {
                    val send = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND)
                            .setType("*/*")
                            .putExtra(Intent.EXTRA_STREAM, uris.first())
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType("*/*")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
                    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching {
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                    }.onFailure { onError(it.message) }
                }
            },
            enabled = !busy,
        )
    }

    YSecondaryButton(
        text = stringResource(R.string.yfiles_move_selected_to_bin),
        onClick = {
            busy = true
            scope.launch {
                val snapshot = YFilesBatchSelectionState.selected.values.toList()
                val results = withContext(Dispatchers.IO) {
                    snapshot.map { entry -> entry to extrasStore.moveToTrash(entry) }
                }
                val succeeded = results.filter { it.second.isSuccess }.map { it.first.path }.toSet()
                succeeded.forEach(YFilesBatchSelectionState.selected::remove)
                val failures = results.mapNotNull { it.second.exceptionOrNull() }
                if (failures.isEmpty()) {
                    YFilesBatchSelectionState.clear()
                    onError(null)
                } else {
                    onError(
                        context.getString(
                            R.string.yfiles_batch_partial_failure,
                            succeeded.size,
                            failures.size,
                            failures.first().message ?: context.getString(R.string.error),
                        ),
                    )
                }
                onChanged()
                busy = false
            }
        },
        enabled = !busy,
    )
}
