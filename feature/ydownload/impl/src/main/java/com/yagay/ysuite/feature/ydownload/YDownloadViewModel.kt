package com.yagay.ysuite.feature.ydownload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadRequest
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.feature.ydownload.api.YDownloadTab
import com.yagay.ysuite.logging.api.YSuiteLogger
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class YDownloadPage {
    Main,
    Settings,
}

enum class YDownloadSort {
    Added,
    Name,
}

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
    val destinationTreeUri: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class YDownloadUiState(
    val items: List<YDownloadItem> = emptyList(),
    val settings: YDownloadSettings = YDownloadSettings(),
    val page: YDownloadPage = YDownloadPage.Main,
    val sort: YDownloadSort = YDownloadSort.Added,
    val selectedTab: YDownloadTab = YDownloadTab.All,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val addDialogVisible: Boolean = false,
    val addDraft: YDownloadAddDraft = YDownloadAddDraft(),
    val propertiesItemId: String? = null,
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
    private var fetchJob: Job? = null

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
        viewModelScope.launch {
            environment.settings.settings.collect { settings ->
                mutableState.update {
                    it.copy(settings = settings)
                }
                if (
                    mutableState.value.items.any {
                        it.state == YDownloadState.Pending &&
                            it.queued
                    }
                ) {
                    YDownloadService.pump(appContext)
                }
            }
        }
    }

    fun selectTab(tab: YDownloadTab) {
        mutableState.update {
            it.copy(selectedTab = tab)
        }
    }

    fun showSettings() {
        mutableState.update {
            it.copy(page = YDownloadPage.Settings)
        }
    }

    fun backToMain() {
        mutableState.update {
            it.copy(page = YDownloadPage.Main)
        }
    }

    fun sortByDate() {
        mutableState.update {
            it.copy(sort = YDownloadSort.Added)
        }
    }

    fun sortByName() {
        mutableState.update {
            it.copy(sort = YDownloadSort.Name)
        }
    }

    fun sorted(
        items: List<YDownloadItem>,
    ): List<YDownloadItem> =
        when (state.value.sort) {
            YDownloadSort.Added ->
                items.sortedByDescending {
                    it.addedAtMillis
                }
            YDownloadSort.Name ->
                items.sortedBy {
                    it.fileName.lowercase()
                }
        }

    fun clearCompleted() {
        val completed =
            state.value.items.filter {
                it.state == YDownloadState.Completed
            }
        viewModelScope.launch {
            completed.forEach {
                environment.engine.remove(
                    item = it,
                    deleteFile = false,
                )
            }
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
        val defaultUa =
            state.value.settings.defaultUserAgent
        mutableState.update {
            it.copy(
                addDialogVisible = true,
                addDraft =
                    YDownloadAddDraft(
                        url = initialUrl,
                        userAgent = defaultUa,
                    ),
            )
        }
        if (initialUrl.isNotBlank()) {
            scheduleAutoFetch(initialUrl)
        }
    }

    fun dismissAddDialog() {
        fetchJob?.cancel()
        mutableState.update {
            it.copy(
                addDialogVisible = false,
                addDraft = YDownloadAddDraft(),
            )
        }
    }

    fun updateUrl(value: String) {
        updateDraft {
            copy(
                url = value,
                fileName = "",
                totalBytes = -1L,
                mimeType = "",
                supportsRanges = false,
                error = null,
            )
        }
        scheduleAutoFetch(value)
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

    fun updateDestinationTreeUri(value: String?) =
        updateDraft {
            copy(destinationTreeUri = value)
        }

    fun fetchMetadata() {
        fetchJob?.cancel()
        fetchMetadataInternal()
    }

    private fun scheduleAutoFetch(
        rawUrl: String,
    ) {
        fetchJob?.cancel()
        val url = rawUrl.trim()
        if (
            !url.startsWith("http://") &&
            !url.startsWith("https://")
        ) {
            return
        }
        fetchJob =
            viewModelScope.launch {
                delay(800L)
                fetchMetadataInternal()
            }
    }

    private fun fetchMetadataInternal() {
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
                referer =
                    draft.referer.takeIf(
                        String::isNotBlank,
                    ),
                userAgent =
                    draft.userAgent
                        .ifBlank {
                            state.value.settings
                                .defaultUserAgent
                        }
                        .takeIf(String::isNotBlank),
                cookies =
                    draft.cookies.takeIf(
                        String::isNotBlank,
                    ),
                username =
                    draft.username.takeIf(
                        String::isNotBlank,
                    ),
                password =
                    draft.password.takeIf(
                        String::isNotBlank,
                    ),
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
                            totalBytes =
                                metadata.totalBytes,
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
                            fileName =
                                fileName.ifBlank {
                                    fileNameFromUrl(url)
                                },
                            loading = false,
                            error = error.message
                                ?: error.javaClass.simpleName,
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

    fun importUrls(urls: List<String>) {
        val cleaned =
            urls.flatMap { raw ->
                raw.split(
                    Regex("[\\s,]+"),
                )
            }
                .map(String::trim)
                .filter {
                    it.startsWith("http://") ||
                        it.startsWith("https://")
                }
                .distinct()
        if (cleaned.isEmpty()) return

        showAddDialog(cleaned.first())
        cleaned.drop(1).forEach(::queueBulkUrl)
    }

    private fun queueBulkUrl(url: String) {
        viewModelScope.launch {
            environment.repository.add(
                request =
                    YDownloadRequest(
                        url = url,
                        fileName =
                            YDownloadMetadataFetcher
                                .sanitizeFileName(
                                    fileNameFromUrl(url),
                                ),
                        userAgent =
                            state.value.settings
                                .defaultUserAgent,
                        destinationTreeUri =
                            state.value.settings
                                .defaultTreeUri,
                    ),
                queued = true,
            )
            YDownloadService.pump(appContext)
        }
    }

    fun pause(id: String) {
        YDownloadService.pause(appContext, id)
    }

    fun resume(id: String) {
        YDownloadService.resume(appContext, id)
    }

    fun cancel(id: String) {
        YDownloadService.cancel(appContext, id)
    }

    fun retry(id: String) {
        YDownloadService.resume(appContext, id)
    }

    fun remove(id: String) {
        val item = item(id) ?: return
        viewModelScope.launch {
            environment.engine.remove(
                item = item,
                deleteFile = false,
            )
            YDownloadService.pump(appContext)
        }
    }

    fun redownload(id: String) {
        val source = item(id) ?: return
        fetchJob?.cancel()
        mutableState.update { current ->
            current.copy(
                addDialogVisible = true,
                addDraft =
                    YDownloadAddDraft(
                        url = source.url,
                        fileName = source.fileName,
                        mimeType = source.mimeType,
                        totalBytes = source.totalBytes,
                        supportsRanges = source.supportsRanges,
                        referer = source.referer.orEmpty(),
                        userAgent =
                            source.userAgent
                                ?: current.settings
                                    .defaultUserAgent,
                        cookies = source.cookies.orEmpty(),
                        username = source.username.orEmpty(),
                        password = source.password.orEmpty(),
                        destinationTreeUri =
                            source.destinationTreeUri,
                    ),
            )
        }
    }

    fun showProperties(id: String) {
        mutableState.update {
            it.copy(propertiesItemId = id)
        }
    }

    fun dismissProperties() {
        mutableState.update {
            it.copy(propertiesItemId = null)
        }
    }

    fun open(id: String) {
        val item = item(id) ?: return
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
        startActivity(intent, "open", item.fileName)
    }

    fun share(id: String) {
        val item = item(id) ?: return
        val uri =
            item.outputUri
                ?.let(Uri::parse)
                ?: return
        val intent =
            Intent(Intent.ACTION_SEND)
                .setType(
                    item.mimeType.ifBlank { "*/*" },
                )
                .putExtra(
                    Intent.EXTRA_STREAM,
                    uri,
                )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
        startActivity(
            Intent.createChooser(
                intent,
                item.fileName,
            ).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
            "share",
            item.fileName,
        )
    }

    fun copyLink(id: String) {
        val item = item(id) ?: return
        val clipboard =
            appContext.getSystemService(
                Context.CLIPBOARD_SERVICE,
            ) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                item.fileName,
                item.url,
            ),
        )
    }

    fun openFolder(id: String) {
        val item = item(id) ?: return
        val customTree =
            item.destinationTreeUri
                ?: state.value.settings.defaultTreeUri
        val uri =
            customTree?.let(Uri::parse)
                ?: Uri.parse(
                    "content://com.android.externalstorage.documents/" +
                        "document/primary%3ADownload%2FYDownload",
                )
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    uri,
                    "vnd.android.document/directory",
                )
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
        startActivity(intent, "folder", id)
    }

    fun setDefaultTreeUri(uri: String?) {
        viewModelScope.launch {
            environment.settings.setDefaultTreeUri(uri)
        }
    }

    fun setMaxConcurrent(value: Int) {
        viewModelScope.launch {
            environment.settings
                .setMaxConcurrentDownloads(value)
            YDownloadService.pump(appContext)
        }
    }

    fun setSpeedLimit(value: Long) {
        viewModelScope.launch {
            environment.settings
                .setGlobalSpeedLimit(value)
        }
    }

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            environment.settings.setWifiOnly(value)
            YDownloadService.pump(appContext)
        }
    }

    fun setAutoResumeNetwork(value: Boolean) {
        viewModelScope.launch {
            environment.settings
                .setAutoResumeNetwork(value)
            YDownloadService.pump(appContext)
        }
    }

    fun setNotifications(value: Boolean) {
        viewModelScope.launch {
            environment.settings
                .setNotificationsEnabled(value)
        }
    }

    fun setDefaultUserAgent(value: String) {
        viewModelScope.launch {
            environment.settings
                .setDefaultUserAgent(value)
        }
    }

    private fun add(
        startNow: Boolean,
    ) {
        val draft = state.value.addDraft
        if (draft.url.isBlank()) return

        val fileName =
            draft.fileName.ifBlank {
                fileNameFromUrl(draft.url)
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
                                        fileName,
                                    ),
                            mimeType = draft.mimeType,
                            totalBytes = draft.totalBytes,
                            supportsRanges =
                                draft.supportsRanges,
                            referer =
                                draft.referer.takeIf(
                                    String::isNotBlank,
                                ),
                            userAgent =
                                draft.userAgent
                                    .ifBlank {
                                        state.value.settings
                                            .defaultUserAgent
                                    }
                                    .takeIf(
                                        String::isNotBlank,
                                    ),
                            cookies =
                                draft.cookies.takeIf(
                                    String::isNotBlank,
                                ),
                            username =
                                draft.username.takeIf(
                                    String::isNotBlank,
                                ),
                            password =
                                draft.password.takeIf(
                                    String::isNotBlank,
                                ),
                            destinationTreeUri =
                                draft.destinationTreeUri
                                    ?: state.value.settings
                                        .defaultTreeUri,
                        ),
                    queued = !startNow,
                )

            dismissAddDialog()

            if (startNow) {
                YDownloadService.start(
                    appContext,
                    id,
                )
            } else {
                YDownloadService.pump(appContext)
            }
        }
    }

    private fun item(id: String): YDownloadItem? =
        state.value.items
            .firstOrNull { it.id == id }

    private fun startActivity(
        intent: Intent,
        operation: String,
        subject: String,
    ) {
        runCatching {
            appContext.startActivity(intent)
        }.onFailure { error ->
            logger.error(
                TAG,
                "Unable to " + operation +
                    " " + subject,
                error,
            )
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

        fun fileNameFromUrl(url: String): String {
            val raw =
                url.substringBefore('?')
                    .substringAfterLast('/')
                    .ifBlank { "download" }
            return runCatching {
                URLDecoder.decode(
                    raw,
                    StandardCharsets.UTF_8.name(),
                )
            }.getOrDefault(raw)
        }
    }
}
