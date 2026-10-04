package com.yagay.ysuite.feature.ydownload

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadRequest
import com.yagay.ysuite.feature.ydownload.api.YDownloadTab
import com.yagay.ysuite.logging.api.YSuiteLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YDownloadAddDraft(
    val url: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val totalBytes: Long = -1L,
    val supportsRanges: Boolean = false,
    val referer: String = "",
    val userAgent: String = "",
    val cookies: String = "",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

data class YDownloadUiState(
    val items: List<YDownloadItem> = emptyList(),
    val selectedTab: YDownloadTab = YDownloadTab.All,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val addDialogVisible: Boolean = false,
    val addDraft: YDownloadAddDraft = YDownloadAddDraft(),
)

class YDownloadViewModel(
    context: Context,
    private val environment: YDownloadEnvironment,
    private val logger: YSuiteLogger,
) : ViewModel() {
    private val appContext =
        context.applicationContext
    private val mutableState =
        MutableStateFlow(YDownloadUiState())

    val state: StateFlow<YDownloadUiState> =
        mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            environment.repository.refresh()
            environment.repository.items.collect { items ->
                mutableState.update {
                    it.copy(items = items)
                }
            }
        }
    }

    fun selectTab(tab: YDownloadTab) {
        mutableState.update {
            it.copy(selectedTab = tab)
        }
    }

    fun toggleSearch() {
        mutableState.update {
            it.copy(
                searchActive = !it.searchActive,
                searchQuery = "",
            )
        }
    }

    fun setSearchQuery(value: String) {
        mutableState.update {
            it.copy(searchQuery = value)
        }
    }

    fun showAddDialog(
        initialUrl: String = "",
    ) {
        mutableState.update {
            it.copy(
                addDialogVisible = true,
                addDraft =
                    YDownloadAddDraft(
                        url = initialUrl,
                    ),
            )
        }
    }

    fun dismissAddDialog() {
        mutableState.update {
            it.copy(
                addDialogVisible = false,
                addDraft = YDownloadAddDraft(),
            )
        }
    }

    fun updateUrl(value: String) =
        updateDraft {
            copy(
                url = value,
                error = null,
            )
        }

    fun updateFileName(value: String) =
        updateDraft {
            copy(fileName = value)
        }

    fun updateReferer(value: String) =
        updateDraft {
            copy(referer = value)
        }

    fun updateUserAgent(value: String) =
        updateDraft {
            copy(userAgent = value)
        }

    fun updateCookies(value: String) =
        updateDraft {
            copy(cookies = value)
        }

    fun updateUsername(value: String) =
        updateDraft {
            copy(username = value)
        }

    fun updatePassword(value: String) =
        updateDraft {
            copy(password = value)
        }

    fun fetchMetadata() {
        val draft = state.value.addDraft
        if (draft.url.isBlank()) return

        updateDraft {
            copy(
                loading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            environment.metadataFetcher.fetch(
                url = draft.url.trim(),
                referer = draft.referer
                    .takeIf(String::isNotBlank),
                userAgent = draft.userAgent
                    .takeIf(String::isNotBlank),
                cookies = draft.cookies
                    .takeIf(String::isNotBlank),
                username = draft.username
                    .takeIf(String::isNotBlank),
                password = draft.password
                    .takeIf(String::isNotBlank),
            ).fold(
                onSuccess = { metadata ->
                    updateDraft {
                        copy(
                            fileName =
                                if (fileName.isBlank()) {
                                    metadata.fileName
                                } else {
                                    fileName
                                },
                            mimeType = metadata.mimeType,
                            totalBytes = metadata.totalBytes,
                            supportsRanges =
                                metadata.supportsRanges,
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    updateDraft {
                        copy(
                            loading = false,
                            error =
                                error.message
                                    ?: "Metadata request failed",
                        )
                    }
                },
            )
        }
    }

    fun addToQueue() {
        add(startNow = false)
    }

    fun addAndStart() {
        add(startNow = true)
    }

    fun pause(id: String) {
        YDownloadService.pause(
            appContext,
            id,
        )
    }

    fun resume(id: String) {
        YDownloadService.resume(
            appContext,
            id,
        )
    }

    fun cancel(id: String) {
        YDownloadService.cancel(
            appContext,
            id,
        )
    }

    fun retry(id: String) {
        YDownloadService.resume(
            appContext,
            id,
        )
    }

    fun remove(id: String) {
        val item =
            state.value.items
                .firstOrNull { it.id == id }
                ?: return
        viewModelScope.launch {
            environment.engine.remove(
                item = item,
                deleteFile = false,
            )
        }
    }

    fun open(id: String) {
        val item =
            state.value.items
                .firstOrNull { it.id == id }
                ?: return
        val uri =
            item.outputUri
                ?.let(Uri::parse)
                ?: return

        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    uri,
                    item.mimeType.ifBlank { "*/*" },
                )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )

        runCatching {
            appContext.startActivity(intent)
        }.onFailure { error ->
            logger.error(
                TAG,
                "Unable to open " + item.fileName,
                error,
            )
        }
    }

    private fun add(
        startNow: Boolean,
    ) {
        val draft = state.value.addDraft
        if (
            draft.url.isBlank() ||
            draft.fileName.isBlank()
        ) {
            return
        }

        viewModelScope.launch {
            val id =
                environment.repository.add(
                    request =
                        YDownloadRequest(
                            url = draft.url.trim(),
                            fileName =
                                YDownloadMetadataFetcher
                                    .sanitizeFileName(
                                        draft.fileName,
                                    ),
                            mimeType = draft.mimeType,
                            totalBytes = draft.totalBytes,
                            supportsRanges =
                                draft.supportsRanges,
                            referer =
                                draft.referer
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                            userAgent =
                                draft.userAgent
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                            cookies =
                                draft.cookies
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                            username =
                                draft.username
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                            password =
                                draft.password
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                        ),
                    queued = !startNow,
                )

            dismissAddDialog()

            if (startNow) {
                YDownloadService.start(
                    appContext,
                    id,
                )
            }
        }
    }

    private fun updateDraft(
        transform:
            YDownloadAddDraft.() -> YDownloadAddDraft,
    ) {
        mutableState.update {
            it.copy(
                addDraft =
                    it.addDraft.transform(),
            )
        }
    }

    class Factory(
        private val context: Context,
        private val environment:
            YDownloadEnvironment,
        private val logger: YSuiteLogger,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YDownloadViewModel(
                context = context,
                environment = environment,
                logger = logger,
            ) as T
    }

    private companion object {
        const val TAG = "YDownload/ViewModel"
    }
}
