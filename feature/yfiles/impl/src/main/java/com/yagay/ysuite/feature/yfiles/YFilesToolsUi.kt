package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteConfirmDialog
import com.yagay.ysuite.designsystem.component.YSuiteFormField
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteTextEditorDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextFormDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameRule
import com.yagay.ysuite.feature.yfiles.api.YDirectoryAnalysis
import com.yagay.ysuite.feature.yfiles.api.YDuplicateGroup
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFilesToolsRepository
import com.yagay.ysuite.logging.api.YSuiteLogger

@Composable
fun YFilesToolsPanel(
    browserState: YFilesUiState,
    repository: YFilesToolsRepository,
    logger: YSuiteLogger,
    onFilesChanged: () -> Unit,
) {
    if (browserState.viewMode != YFilesViewMode.Files) {
        return
    }

    val model: YFilesToolsViewModel = viewModel(
        factory = YFilesToolsViewModelFactory(
            repository = repository,
            logger = logger,
        ),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val selected = browserState.entries.filter {
        it.path in browserState.selectedPaths
    }
    val singleFile = selected.singleOrNull()
        ?.takeUnless(YFileEntry::directory)

    YSuiteSection(
        title = stringResource(R.string.yfiles_tools),
    ) {
        YSuiteSecondaryButton(
            text = stringResource(
                R.string.yfiles_analyze_folder,
            ),
            onClick = {
                model.analyze(browserState.path)
            },
        )
        YSuiteSecondaryButton(
            text = stringResource(
                R.string.yfiles_find_duplicates,
            ),
            onClick = {
                model.scanDuplicates(browserState.path)
            },
        )

        if (selected.isNotEmpty()) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_create_zip,
                ),
                onClick = model::beginArchive,
            )
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_batch_rename,
                ),
                onClick = model::beginBatchRename,
            )
        }

        if (singleFile != null) {
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_sha256,
                ),
                onClick = {
                    model.calculateHash(singleFile)
                },
            )
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_text_editor,
                ),
                onClick = {
                    model.openText(singleFile)
                },
            )
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_hex_preview,
                ),
                onClick = {
                    model.openHex(singleFile)
                },
            )

            if (singleFile.name.endsWith(".zip", true)) {
                YSuiteSecondaryButton(
                    text = stringResource(
                        R.string.yfiles_extract_here,
                    ),
                    onClick = {
                        model.extractArchive(
                            archive = singleFile,
                            destinationPath =
                                browserState.path,
                        )
                    },
                )
            }
        }

        if (state.busy) {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string.yfiles_tool_running,
                ),
                tone = YSuiteStatusTone.Neutral,
            )
        }

        state.result?.let {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string.yfiles_operation_result,
                    it.succeeded,
                    it.failed,
                ),
                tone = if (it.failed == 0) {
                    YSuiteStatusTone.Positive
                } else {
                    YSuiteStatusTone.Warning
                },
            )
        }

        state.error?.let {
            YSuiteStatusBadge(
                text = it,
                tone = YSuiteStatusTone.Error,
            )
        }
    }

    state.hash?.let { hash ->
        YSuiteSection(
            title = stringResource(R.string.yfiles_sha256),
        ) {
            YSuiteListItem(
                title = hash.algorithm,
                subtitle = hash.hex,
            )
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_clear_tool_results,
                ),
                onClick = model::clearResults,
            )
        }
    }

    state.analysis?.let {
        AnalysisSection(it)
    }

    if (state.duplicates.isNotEmpty()) {
        DuplicateSection(state.duplicates)
    }

    state.hexPreview?.let {
        YSuiteSection(
            title = stringResource(
                R.string.yfiles_hex_preview,
            ),
        ) {
            YSuiteListItem(
                title = stringResource(
                    R.string.yfiles_hex_bytes,
                    it.byteCount,
                ),
                subtitle = it.text,
            )
            if (it.truncated) {
                YSuiteStatusBadge(
                    text = stringResource(
                        R.string.yfiles_preview_truncated,
                    ),
                    tone = YSuiteStatusTone.Warning,
                )
            }
            YSuiteSecondaryButton(
                text = stringResource(
                    R.string.yfiles_close,
                ),
                onClick = model::dismissHex,
            )
        }
    }

    state.archiveName?.let { archiveName ->
        YSuiteTextInputDialog(
            title = stringResource(
                R.string.yfiles_create_zip,
            ),
            label = stringResource(
                R.string.yfiles_archive_name,
            ),
            value = archiveName,
            confirmText = stringResource(
                R.string.yfiles_confirm,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange = model::updateArchiveName,
            onConfirm = {
                model.createArchive(
                    entries = selected,
                    destinationPath = browserState.path,
                )
                onFilesChanged()
            },
            onDismiss = model::cancelArchive,
        )
    }

    state.textDocument?.let { document ->
        YSuiteTextEditorDialog(
            title = stringResource(
                R.string.yfiles_text_editor,
            ),
            value = document.text,
            confirmText = stringResource(
                R.string.yfiles_save,
            ),
            dismissText = stringResource(
                R.string.yfiles_cancel,
            ),
            onValueChange = model::updateText,
            onConfirm = {
                model.saveText()
                onFilesChanged()
            },
            onDismiss = model::dismissText,
        )
    }

    state.renameRule?.let { rule ->
        if (state.renamePreview.isEmpty()) {
            RenameRuleDialog(
                rule = rule,
                onValueChange = model::updateRenameRule,
                onPreview = {
                    model.previewBatchRename(selected)
                },
                onDismiss = model::cancelBatchRename,
            )
        } else {
            RenamePreviewDialog(
                count = state.renamePreview.size,
                onConfirm = {
                    model.applyBatchRename()
                    onFilesChanged()
                },
                onDismiss = model::cancelBatchRename,
            )
        }
    }
}

@Composable
private fun AnalysisSection(
    analysis: YDirectoryAnalysis,
) {
    YSuiteSection(
        title = stringResource(
            R.string.yfiles_folder_analysis,
        ),
    ) {
        YSuiteListItem(
            title = stringResource(
                R.string.yfiles_analysis_files,
            ),
            subtitle = analysis.fileCount.toString(),
        )
        YSuiteListItem(
            title = stringResource(
                R.string.yfiles_analysis_folders,
            ),
            subtitle = analysis.directoryCount.toString(),
        )
        YSuiteListItem(
            title = stringResource(
                R.string.yfiles_analysis_size,
            ),
            subtitle = formatToolBytes(
                analysis.totalBytes,
            ),
        )
        if (analysis.truncated) {
            YSuiteStatusBadge(
                text = stringResource(
                    R.string.yfiles_scan_truncated,
                ),
                tone = YSuiteStatusTone.Warning,
            )
        }
        analysis.largestFiles.forEach { entry ->
            YSuiteListItem(
                title = entry.name,
                subtitle = formatToolBytes(
                    entry.sizeBytes,
                ),
            )
        }
    }
}

@Composable
private fun DuplicateSection(
    groups: List<YDuplicateGroup>,
) {
    YSuiteSection(
        title = stringResource(
            R.string.yfiles_duplicates,
        ),
    ) {
        groups.forEach { group ->
            YSuiteListItem(
                title = stringResource(
                    R.string.yfiles_duplicate_group,
                    group.entries.size,
                    formatToolBytes(group.sizeBytes),
                ),
                subtitle = group.entries
                    .joinToString("\n") { it.path },
            )
        }
    }
}

@Composable
private fun RenameRuleDialog(
    rule: YBatchRenameRule,
    onValueChange: (String, String) -> Unit,
    onPreview: () -> Unit,
    onDismiss: () -> Unit,
) {
    YSuiteTextFormDialog(
        title = stringResource(
            R.string.yfiles_batch_rename,
        ),
        fields = listOf(
            YSuiteFormField(
                id = "prefix",
                label = stringResource(
                    R.string.yfiles_rename_prefix,
                ),
                value = rule.prefix,
            ),
            YSuiteFormField(
                id = "suffix",
                label = stringResource(
                    R.string.yfiles_rename_suffix,
                ),
                value = rule.suffix,
            ),
            YSuiteFormField(
                id = "find",
                label = stringResource(
                    R.string.yfiles_rename_find,
                ),
                value = rule.find,
            ),
            YSuiteFormField(
                id = "replace",
                label = stringResource(
                    R.string.yfiles_rename_replace,
                ),
                value = rule.replace,
            ),
        ),
        confirmText = stringResource(
            R.string.yfiles_preview,
        ),
        dismissText = stringResource(
            R.string.yfiles_cancel,
        ),
        onValueChange = onValueChange,
        onConfirm = onPreview,
        onDismiss = onDismiss,
    )
}

@Composable
private fun RenamePreviewDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    YSuiteConfirmDialog(
        title = stringResource(
            R.string.yfiles_batch_rename,
        ),
        message = stringResource(
            R.string.yfiles_rename_preview_count,
            count,
        ),
        confirmText = stringResource(
            R.string.yfiles_apply,
        ),
        dismissText = stringResource(
            R.string.yfiles_cancel,
        ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private fun formatToolBytes(value: Long): String {
    if (value < 1024L) {
        return value.toString() + " B"
    }

    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var amount = value.toDouble()
    var index = -1
    do {
        amount /= 1024.0
        index += 1
    } while (
        amount >= 1024.0 &&
        index < units.lastIndex
    )
    return String.format("%.1f %s", amount, units[index])
}

private class YFilesToolsViewModelFactory(
    private val repository: YFilesToolsRepository,
    private val logger: YSuiteLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        YFilesToolsViewModel(
            repository = repository,
            logger = logger,
        ) as T
}
