package com.yagay.ysuite.feature.yfiles

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.launch

data class YLinkDraft(
    val name: String = "",
    val target: String = "",
)

data class YFilesToolsUiState(
    val busy: Boolean = false,
    val hash: YHashResult? = null,
    val analysis: YDirectoryAnalysis? = null,
    val duplicates: List<YDuplicateGroup> =
        emptyList(),
    val textDocument: YTextDocument? = null,
    val hexPreview: YHexPreview? = null,
    val renameRule: YBatchRenameRule? = null,
    val renamePreview:
        List<YBatchRenameItem> = emptyList(),
    val zipName: String? = null,
    val splitSizeMiB: String? = null,
    val chmodMode: String? = null,
    val linkDraft: YLinkDraft? = null,
    val compare: YCompareResult? = null,
    val cleanup: YCleanupReport? = null,
    val batchResult: YFileBatchResult? = null,
    val error: String? = null,
    val mutationVersion: Int = 0,
)

class YFilesToolsViewModel(
    private val environment: YFilesEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<
    YFilesToolsUiState,
    Nothing,
    >(
    initialState = YFilesToolsUiState(),
) {
    fun hash(ref: YFileRef) =
        launchTool("hash") {
            when (
                val result =
                    environment.tools.sha256(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            hash = result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun analyze(ref: YFileRef) =
        launchTool("analysis") {
            when (
                val result =
                    environment.tools
                        .analyze(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            analysis =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun duplicates(ref: YFileRef) =
        launchTool("duplicates") {
            when (
                val result =
                    environment.tools
                        .duplicates(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            duplicates =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun openText(ref: YFileRef) =
        launchTool("text") {
            when (
                val result =
                    environment.tools
                        .readText(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            textDocument =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun updateText(value: String) {
        updateState { current ->
            current.copy(
                textDocument =
                    current.textDocument
                        ?.copy(text = value),
            )
        }
    }

    fun saveText() {
        val document =
            state.value.textDocument ?: return
        launchTool("save_text") {
            when (
                val result =
                    environment.tools
                        .writeText(
                            document.ref,
                            document.text,
                        )
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            textDocument = null,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun dismissText() {
        updateState {
            it.copy(textDocument = null)
        }
    }

    fun hex(ref: YFileRef) =
        launchTool("hex") {
            when (
                val result =
                    environment.tools
                        .readHex(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            hexPreview =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun dismissHex() {
        updateState {
            it.copy(hexPreview = null)
        }
    }

    fun beginRename() {
        updateState {
            it.copy(
                renameRule =
                    YBatchRenameRule(),
                renamePreview =
                    emptyList(),
            )
        }
    }

    fun updateRenameRule(
        field: String,
        value: String,
    ) {
        updateState { current ->
            val rule =
                current.renameRule
                    ?: YBatchRenameRule()
            val next = when (field) {
                "prefix" ->
                    rule.copy(prefix = value)
                "suffix" ->
                    rule.copy(suffix = value)
                "find" ->
                    rule.copy(find = value)
                "replace" ->
                    rule.copy(
                        replace = value,
                    )
                else -> rule
            }
            current.copy(
                renameRule = next,
            )
        }
    }

    fun toggleRegex(value: Boolean) {
        updateState { current ->
            current.copy(
                renameRule =
                    (
                        current.renameRule
                            ?: YBatchRenameRule()
                        ).copy(
                        regex = value,
                    ),
            )
        }
    }

    fun previewRename(
        refs: List<YFileRef>,
    ) {
        val rule =
            state.value.renameRule ?: return
        launchTool("rename_preview") {
            when (
                val result =
                    environment.tools
                        .previewRename(
                            refs,
                            rule,
                        )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            renamePreview =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun applyRename() {
        val preview =
            state.value.renamePreview
        if (preview.isEmpty()) {
            return
        }
        launchTool("rename_apply") {
            val result =
                environment.tools
                    .applyRename(preview)
            mutate {
                it.copy(
                    renameRule = null,
                    renamePreview =
                        emptyList(),
                    batchResult = result,
                )
            }
        }
    }

    fun cancelRename() {
        updateState {
            it.copy(
                renameRule = null,
                renamePreview =
                    emptyList(),
            )
        }
    }

    fun beginZip() {
        updateState {
            it.copy(
                zipName = "archive.zip",
            )
        }
    }

    fun updateZipName(value: String) {
        updateState {
            it.copy(zipName = value)
        }
    }

    fun cancelZip() {
        updateState {
            it.copy(zipName = null)
        }
    }

    fun createZip(
        refs: List<YFileRef>,
        destination: YFileRef,
    ) {
        val name =
            state.value.zipName ?: return
        updateState {
            it.copy(zipName = null)
        }
        launchTool("zip_create") {
            when (
                val result =
                    environment.tools
                        .createZip(
                            refs,
                            destination,
                            name,
                        )
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            batchResult =
                                singleSuccess(),
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun extractZip(
        archive: YFileRef,
        destination: YFileRef,
    ) =
        launchTool("zip_extract") {
            val result =
                environment.tools
                    .extractZip(
                        archive,
                        destination,
                    )
            mutate {
                it.copy(
                    batchResult = result,
                )
            }
        }

    fun beginSplit() {
        updateState {
            it.copy(
                splitSizeMiB = "100",
            )
        }
    }

    fun updateSplitSize(value: String) {
        updateState {
            it.copy(splitSizeMiB = value)
        }
    }

    fun cancelSplit() {
        updateState {
            it.copy(splitSizeMiB = null)
        }
    }

    fun split(ref: YFileRef) {
        val sizeMiB =
            state.value.splitSizeMiB
                ?.toLongOrNull()
                ?: return
        updateState {
            it.copy(splitSizeMiB = null)
        }
        launchTool("split") {
            when (
                val result =
                    environment.tools.split(
                        ref,
                        sizeMiB *
                            1024L *
                            1024L,
                    )
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            batchResult =
                                YFileBatchResult(
                                    succeeded =
                                        result.value,
                                    skipped = 0,
                                    failures =
                                        emptyList(),
                                ),
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun join(ref: YFileRef) =
        launchTool("join") {
            when (
                val result =
                    environment.tools.join(ref)
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            batchResult =
                                singleSuccess(),
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun compare(
        refs: List<YFileRef>,
    ) {
        if (refs.size != 2) {
            return
        }
        launchTool("compare") {
            when (
                val result =
                    environment.tools
                        .compare(
                            refs[0],
                            refs[1],
                        )
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            compare =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun cleanup(ref: YFileRef) =
        launchTool("cleanup") {
            when (
                val result =
                    environment.tools
                        .cleanupScan(ref)
            ) {
                is Outcome.Success ->
                    updateState {
                        it.copy(
                            cleanup =
                                result.value,
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }

    fun beginChmod(node: YFileNode) {
        updateState {
            it.copy(
                chmodMode =
                    node.posixMode
                        ?.let {
                            mode ->
                            Integer.toOctalString(
                                mode and 0xFFF,
                            )
                        }
                        ?: "644",
            )
        }
    }

    fun updateChmod(value: String) {
        updateState {
            it.copy(chmodMode = value)
        }
    }

    fun cancelChmod() {
        updateState {
            it.copy(chmodMode = null)
        }
    }

    fun chmod(ref: YFileRef) {
        val mode =
            state.value.chmodMode
                ?.toIntOrNull(radix = 8)
                ?: return
        updateState {
            it.copy(chmodMode = null)
        }
        launchTool("chmod") {
            when (
                val result =
                    environment.engine
                        .setPosixMode(
                            ref,
                            mode,
                        )
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            batchResult =
                                singleSuccess(),
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun beginSymlink() {
        updateState {
            it.copy(
                linkDraft =
                    YLinkDraft(),
            )
        }
    }

    fun updateLink(
        field: String,
        value: String,
    ) {
        updateState { current ->
            val draft =
                current.linkDraft
                    ?: YLinkDraft()
            current.copy(
                linkDraft = when (field) {
                    "name" ->
                        draft.copy(
                            name = value,
                        )
                    "target" ->
                        draft.copy(
                            target = value,
                        )
                    else -> draft
                },
            )
        }
    }

    fun cancelSymlink() {
        updateState {
            it.copy(linkDraft = null)
        }
    }

    fun createSymlink(
        parent: YFileRef,
    ) {
        val draft =
            state.value.linkDraft ?: return
        updateState {
            it.copy(linkDraft = null)
        }
        launchTool("symlink") {
            when (
                val result =
                    environment.engine
                        .createSymbolicLink(
                            parent = parent,
                            name = draft.name,
                            target =
                                draft.target,
                        )
            ) {
                is Outcome.Success ->
                    mutate {
                        it.copy(
                            batchResult =
                                singleSuccess(),
                        )
                    }
                is Outcome.Failure ->
                    fail(result)
            }
        }
    }

    fun clearResults() {
        updateState {
            YFilesToolsUiState(
                mutationVersion =
                    it.mutationVersion,
            )
        }
    }

    private fun launchTool(
        name: String,
        block: suspend () -> Unit,
    ) {
        if (state.value.busy) {
            return
        }
        updateState {
            it.copy(
                busy = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                block()
            } catch (error: Throwable) {
                updateState {
                    it.copy(
                        error =
                            error.message
                                ?: TOOL_ERROR_MESSAGE,
                    )
                }
                logger.error(
                    TAG,
                    name,
                    error,
                )
            } finally {
                updateState {
                    it.copy(busy = false)
                }
            }
        }
    }

    private fun mutate(
        block:
            (YFilesToolsUiState) ->
                YFilesToolsUiState,
    ) {
        updateState {
            block(it).copy(
                mutationVersion =
                    it.mutationVersion + 1,
            )
        }
    }

    private fun fail(
        failure: Outcome.Failure,
    ) {
        updateState {
            it.copy(
                error = failure.message,
            )
        }
        logger.error(
            TAG,
            "YFiles tool failed",
            failure.cause,
        )
    }

    private fun singleSuccess() =
        YFileBatchResult(
            succeeded = 1,
            skipped = 0,
            failures = emptyList(),
        )

    companion object {
        private const val TAG =
            "YSuite/YFilesTools"
        private const val TOOL_ERROR_MESSAGE =
            "File tool failed"
    }
}
