package com.yagay.ysuite.feature.yfiles

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class YFilesUiState(
    val providers: List<YFileProviderDescriptor>,
    val activeProviderId: String?,
    val directory: YFileRef?,
    val entries: List<YFileNode> = emptyList(),
    val query: String = "",
    val recursive: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class YFilesViewModel(
    private val engine: YFilesEngine,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<YFilesUiState, Nothing>(
    initialState = YFilesUiState(
        providers = engine.providers,
        activeProviderId =
            engine.providers.firstOrNull()?.id,
        directory = null,
    ),
) {
    private var loadJob: Job? = null

    init {
        engine.providers.firstOrNull()?.let {
            selectProvider(it.id)
        } ?: updateState {
            it.copy(
                loading = false,
                error = "No file providers",
            )
        }
    }

    fun selectProvider(providerId: String) {
        when (val root = engine.root(providerId)) {
            is Outcome.Success -> {
                updateState {
                    it.copy(
                        activeProviderId = providerId,
                        directory = root.value,
                        query = "",
                        entries = emptyList(),
                        error = null,
                    )
                }
                refresh()
            }
            is Outcome.Failure ->
                showFailure(root)
        }
    }

    fun open(node: YFileNode) {
        if (node.type != YFileType.Directory) {
            return
        }
        updateState {
            it.copy(
                directory = node.ref,
                query = "",
                entries = emptyList(),
                error = null,
            )
        }
        refresh()
    }

    fun parent() {
        val current = state.value.directory ?: return
        when (val parent = engine.parent(current)) {
            is Outcome.Success -> {
                val next = parent.value ?: return
                updateState {
                    it.copy(
                        directory = next,
                        query = "",
                        entries = emptyList(),
                        error = null,
                    )
                }
                refresh()
            }
            is Outcome.Failure ->
                showFailure(parent)
        }
    }

    fun setQuery(value: String) {
        updateState {
            it.copy(query = value)
        }
        scheduleRefresh()
    }

    fun setRecursive(value: Boolean) {
        updateState {
            it.copy(recursive = value)
        }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            load()
        }
    }

    private fun scheduleRefresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(250L)
            load()
        }
    }

    private suspend fun load() {
        val current = state.value
        val directory = current.directory
        if (directory == null) {
            updateState {
                it.copy(loading = false)
            }
            return
        }

        updateState {
            it.copy(
                loading = true,
                error = null,
            )
        }

        when (
            val result = engine.list(
                directory = directory,
                query = YFileQuery(
                    text = current.query,
                    recursive =
                        current.recursive,
                ),
            )
        ) {
            is Outcome.Success -> {
                updateState {
                    it.copy(
                        entries = result.value,
                        loading = false,
                        error = null,
                    )
                }
                logger.debug(
                    TAG,
                    "Loaded " +
                        result.value.size +
                        " entries from " +
                        directory.providerId,
                )
            }
            is Outcome.Failure ->
                showFailure(result)
        }
    }

    private fun showFailure(
        failure: Outcome.Failure,
    ) {
        updateState {
            it.copy(
                loading = false,
                entries = emptyList(),
                error = failure.message,
            )
        }
        logger.error(
            TAG,
            "YFiles engine operation failed",
            failure.cause,
        )
    }

    companion object {
        private const val TAG = "YSuite/YFiles"
    }
}
