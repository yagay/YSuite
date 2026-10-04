package com.yagay.ysuite.feature.yfiles

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameItem
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameRule
import com.yagay.ysuite.feature.yfiles.api.YDirectoryAnalysis
import com.yagay.ysuite.feature.yfiles.api.YDuplicateGroup
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileHash
import com.yagay.ysuite.feature.yfiles.api.YFilesToolsRepository
import com.yagay.ysuite.feature.yfiles.api.YHexPreview
import com.yagay.ysuite.feature.yfiles.api.YTextDocument
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.launch

data class YFilesToolsUiState(
    val busy: Boolean = false,
    val hash: YFileHash? = null,
    val analysis: YDirectoryAnalysis? = null,
    val duplicates: List<YDuplicateGroup> = emptyList(),
    val textDocument: YTextDocument? = null,
    val textEntry: YFileEntry? = null,
    val hexPreview: YHexPreview? = null,
    val archiveName: String? = null,
    val renameRule: YBatchRenameRule? = null,
    val renamePreview: List<YBatchRenameItem> = emptyList(),
    val result: YFileBatchResult? = null,
    val error: String? = null,
)

class YFilesToolsViewModel(
    private val repository: YFilesToolsRepository,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<YFilesToolsUiState, Nothing>(
    initialState = YFilesToolsUiState(),
) {
    fun calculateHash(entry: YFileEntry) {
        launchTool("SHA-256") {
            when (val result = repository.sha256(entry)) {
                is Outcome.Success ->
                    updateState {
                        it.copy(hash = result.value)
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun analyze(path: String) {
        launchTool("Directory analysis") {
            when (
                val result =
                    repository.analyzeDirectory(path)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(analysis = result.value)
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun scanDuplicates(path: String) {
        launchTool("Duplicate scan") {
            when (
                val result =
                    repository.findDuplicates(path)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(duplicates = result.value)
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun openText(entry: YFileEntry) {
        launchTool("Text preview") {
            when (val result = repository.readText(entry)) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            textDocument = result.value,
                            textEntry = entry,
                        )
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun updateText(value: String) {
        updateState { current ->
            current.copy(
                textDocument =
                    current.textDocument?.copy(
                        text = value,
                    ),
            )
        }
    }

    fun saveText() {
        val entry = state.value.textEntry ?: return
        val document =
            state.value.textDocument ?: return

        launchTool("Text save") {
            when (
                val result = repository.writeText(
                    entry = entry,
                    text = document.text,
                )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            textDocument = null,
                            textEntry = null,
                            result = YFileBatchResult(
                                succeeded = 1,
                                failures = emptyList(),
                            ),
                        )
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun dismissText() {
        updateState {
            it.copy(
                textDocument = null,
                textEntry = null,
            )
        }
    }

    fun openHex(entry: YFileEntry) {
        launchTool("HEX preview") {
            when (val result = repository.readHex(entry)) {
                is Outcome.Success ->
                    updateState {
                        it.copy(hexPreview = result.value)
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun dismissHex() {
        updateState { it.copy(hexPreview = null) }
    }

    fun beginArchive() {
        updateState {
            it.copy(archiveName = "archive.zip")
        }
    }

    fun updateArchiveName(value: String) {
        updateState { it.copy(archiveName = value) }
    }

    fun cancelArchive() {
        updateState { it.copy(archiveName = null) }
    }

    fun createArchive(
        entries: List<YFileEntry>,
        destinationPath: String,
    ) {
        val name = state.value.archiveName
            ?: return
        updateState { it.copy(archiveName = null) }

        launchTool("Create ZIP") {
            when (
                val result = repository.createZip(
                    entries = entries,
                    destinationPath = destinationPath,
                    archiveName = name,
                )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            result = YFileBatchResult(
                                succeeded =
                                    result.value.addedEntries,
                                failures = emptyList(),
                            ),
                        )
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun extractArchive(
        archive: YFileEntry,
        destinationPath: String,
    ) {
        launchTool("Extract ZIP") {
            when (
                val result = repository.extractZip(
                    archive = archive,
                    destinationPath = destinationPath,
                )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(result = result.value)
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun beginBatchRename() {
        updateState {
            it.copy(
                renameRule = YBatchRenameRule(),
                renamePreview = emptyList(),
            )
        }
    }

    fun updateRenameRule(
        field: String,
        value: String,
    ) {
        updateState { current ->
            val rule = current.renameRule
                ?: YBatchRenameRule()
            val next = when (field) {
                "prefix" -> rule.copy(prefix = value)
                "suffix" -> rule.copy(suffix = value)
                "find" -> rule.copy(find = value)
                "replace" -> rule.copy(replace = value)
                else -> rule
            }
            current.copy(renameRule = next)
        }
    }

    fun cancelBatchRename() {
        updateState {
            it.copy(
                renameRule = null,
                renamePreview = emptyList(),
            )
        }
    }

    fun previewBatchRename(
        entries: List<YFileEntry>,
    ) {
        val rule = state.value.renameRule ?: return

        launchTool("Batch rename preview") {
            when (
                val result =
                    repository.previewBatchRename(
                        entries = entries,
                        rule = rule,
                    )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            renamePreview = result.value,
                        )
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun applyBatchRename() {
        val preview = state.value.renamePreview
        if (preview.isEmpty()) return

        launchTool("Batch rename") {
            when (
                val result =
                    repository.applyBatchRename(preview)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            renameRule = null,
                            renamePreview = emptyList(),
                            result = result.value,
                        )
                    }
                is Outcome.Failure -> fail(result)
            }
        }
    }

    fun clearResults() {
        updateState {
            it.copy(
                hash = null,
                analysis = null,
                duplicates = emptyList(),
                result = null,
                error = null,
            )
        }
    }

    private fun launchTool(
        name: String,
        block: suspend () -> Unit,
    ) {
        if (state.value.busy) return

        updateState {
            it.copy(
                busy = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                block()
            } finally {
                updateState { it.copy(busy = false) }
                logger.debug(
                    TAG,
                    name + " completed",
                )
            }
        }
    }

    private fun fail(result: Outcome.Failure) {
        updateState {
            it.copy(error = result.message)
        }
        logger.error(
            TAG,
            "YFiles tool failed",
            result.cause,
        )
    }

    companion object {
        private const val TAG = "YSuite/YFilesTools"
    }
}
