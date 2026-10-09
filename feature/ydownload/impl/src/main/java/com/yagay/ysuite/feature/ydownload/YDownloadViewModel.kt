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
import kotlinx.coroutines.CancellationException
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
    val threadCount: Int = 4,
    val speedLimitBytesPerSecond: Long = 0L,
    val customHeadersText: String = "",
    val scheduledAtMillis: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

data class YDownloadUiState(
    val items: List<YDownloadItem> = emptyList(),
    val listLoadError: String? = null,
    val settings: YDownloadSettings = YDownloadSettings(),
    val systemPatch:
        YDownloadSystemPatchSettings =
        YDownloadSystemPatchSettings(),
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
    private val systemPatch =
        YDownloadSystemPatchStore(
            appContext,
            environment.hookGateway,
        )
    private val mutableState =
        MutableStateFlow(
            YDownloadUiState(
                systemPatch =
                    systemPatch.load(),
            ),
        )
    private var fetchJob: Job? = null
    private var reloadJob: Job? = null
    private var addInProgress = false

    val state: StateFlow<YDownloadUiState> =
        mutableState.asStateFlow()

    init {
        YDownloadIncomingUrlStore
            .consume(appContext)
            ?.let(::showAddDialog)

        viewModelScope.launch {
            YDownloadIncomingUrlStore.urls.collect { url ->
                YDownloadIncomingUrlStore.clearIfMatches(appContext, url)
                showAddDialog(url)
            }
        }

        // Keep the UI subscribed even if the initial database read fails.
        viewModelScope.launch {
            environment.repository.items.collect { items ->
                mutableState.update { it.copy(items = items) }
            }
        }
        retryLoad()

        viewModelScope.launch {
            environment.settings.settings.collect { settings ->
                mutableState.update { it.copy(settings = settings) }
                if (mutableState.value.items.any {
                        it.state == YDownloadState.Pending && it.queued
                    }) {
                    YDownloadService.pump(appContext)
                }
            }
        }
    }

    fun retryLoad() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            try {
                environment.repository.refresh()
                mutableState.update { it.copy(listLoadError = null) }
                environment.repository.items.value
                    .filter { it.state == YDownloadState.Scheduled }
                    .forEach { item ->
                        runCatching {
                            val scheduledAt = item.scheduledAtMillis
                            if (scheduledAt == null ||
                                scheduledAt <= System.currentTimeMillis()
                            ) {
                                YDownloadService.start(appContext, item.id)
                            } else {
                                environment.scheduler.schedule(
                                    downloadId = item.id,
                                    scheduledAtMillis = scheduledAt,
                                )
                            }
                        }.onFailure { error ->
                            logger.error(
                                "YSuite/YDownload",
                                "Unable to restore scheduled task: " + item.id,
                                error,
                            )
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(listLoadError = error.message ?: error.javaClass.simpleName)
                }
                logger.error("YSuite/YDownload", "Unable to read download tasks", error)
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

    fun pauseAll() {
        state.value.items
            .filter {
                it.backend ==
                    com.yagay.ysuite.feature.ydownload.api.YDownloadBackend.Private &&
                    (
                        it.state ==
                            YDownloadState.Pending ||
                            it.state ==
                            YDownloadState.Connecting ||
                            it.state ==
                            YDownloadState.Downloading
                    )
            }
            .forEach {
                YDownloadService.pause(
                    appContext,
                    it.id,
                )
            }
    }

    fun resumeAll() {
        state.value.items
            .filter {
                it.backend ==
                    com.yagay.ysuite.feature.ydownload.api.YDownloadBackend.Private &&
                    it.state ==
                        YDownloadState.Paused
            }
            .forEach {
                YDownloadService.resume(
                    appContext,
                    it.id,
                )
            }
    }

    fun retryFailed() {
        state.value.items
            .filter {
                it.state == YDownloadState.Failed
            }
            .forEach {
                YDownloadService.resume(
                    appContext,
                    it.id,
                )
            }
    }

    fun clearFinished() {
        val finished =
            state.value.items.filter {
                it.state == YDownloadState.Completed ||
                    it.state == YDownloadState.Cancelled
            }
        viewModelScope.launch {
            finished.forEach {
                environment.scheduler.cancel(it.id)
                environment.engine.remove(
                    item = it,
                    deleteFile = false,
                )
            }
            YDownloadService.pump(appContext)
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
                        threadCount =
                            state.value.settings
                                .defaultThreadCount,
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

    fun updateThreadCount(value: Int) =
        updateDraft {
            copy(threadCount = value.coerceIn(1, 16))
        }

    fun updateSpeedLimitBytesPerSecond(value: Long) =
        updateDraft {
            copy(
                speedLimitBytesPerSecond =
                    value.coerceAtLeast(0L),
            )
        }

    fun updateCustomHeaders(value: String) =
        updateDraft {
            copy(customHeadersText = value)
        }

    fun updateScheduledAt(value: Long?) =
        updateDraft {
            copy(
                scheduledAtMillis =
                    value?.takeIf {
                        it > System.currentTimeMillis()
                    },
            )
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
                customHeaders =
                    parseHeaders(
                        draft.customHeadersText,
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
                            threadCount =
                                if (metadata.supportsRanges) {
                                    threadCount.coerceIn(1, 16)
                                } else {
                                    1
                                },
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
                        backend =
                            state.value.settings
                                .defaultBackend,
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
                        threadCount =
                            state.value.settings
                                .defaultThreadCount,
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
        val current = item(id) ?: return
        if (current.state == YDownloadState.Scheduled) {
            viewModelScope.launch {
                environment.scheduler.cancel(id)
                environment.repository.updateState(
                    id = id,
                    state = YDownloadState.Cancelled,
                    queued = false,
                )
            }
        } else {
            YDownloadService.cancel(appContext, id)
        }
    }

    fun retry(id: String) {
        YDownloadService.resume(appContext, id)
    }

    fun remove(id: String) {
        val item = item(id) ?: return
        viewModelScope.launch {
            environment.scheduler.cancel(id)
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
                        threadCount =
                            source.threadCount,
                        speedLimitBytesPerSecond =
                            source.speedLimitBytesPerSecond,
                        customHeadersText =
                            formatHeaders(
                                source.customHeaders,
                            ),
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

    fun setDefaultBackend(
        value: com.yagay.ysuite.feature.ydownload.api.YDownloadBackend,
    ) {
        viewModelScope.launch {
            environment.settings
                .setDefaultBackend(value)
        }
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

    fun setDefaultThreadCount(value: Int) {
        viewModelScope.launch {
            environment.settings
                .setDefaultThreadCount(value)
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

    fun setAutoRetry(value: Boolean) {
        viewModelScope.launch { environment.settings.setAutoRetry(value) }
    }

    fun setMaxRetries(value: Int) {
        viewModelScope.launch { environment.settings.setMaxRetries(value) }
    }

    fun setCalculateSha256(value: Boolean) {
        viewModelScope.launch { environment.settings.setCalculateSha256(value) }
    }

    fun setNotifications(value: Boolean) {
        viewModelScope.launch {
            environment.settings
                .setNotificationsEnabled(value)
        }
    }

    fun setSystemPatchEnabled(
        value: Boolean,
    ) = updateSystemPatch {
        copy(enabled = value)
    }

    fun setSystemPatchAllowMetered(
        value: Boolean,
    ) = updateSystemPatch {
        copy(allowMetered = value)
    }

    fun setSystemPatchAllowRoaming(
        value: Boolean,
    ) = updateSystemPatch {
        copy(allowRoaming = value)
    }

    fun setSystemPatchRequireCharging(
        value: Boolean,
    ) = updateSystemPatch {
        copy(requireCharging = value)
    }

    fun setSystemPatchRequireIdle(
        value: Boolean,
    ) = updateSystemPatch {
        copy(requireDeviceIdle = value)
    }

    fun setSystemPatchCompletionNotification(
        value: Boolean,
    ) = updateSystemPatch {
        copy(
            forceCompletionNotification = value,
        )
    }

    fun systemPatchScopeCount(): Int =
        systemPatch.recommendedTargets().size

    fun requestSystemPatchScope() {
        viewModelScope.launch {
            systemPatch
                .requestRecommendedScope()
        }
    }

    fun syncSystemPatch() {
        viewModelScope.launch {
            systemPatch.sync(
                mutableState.value.systemPatch,
            )
        }
    }

    private fun updateSystemPatch(
        transform:
            YDownloadSystemPatchSettings.() ->
            YDownloadSystemPatchSettings,
    ) {
        val next =
            mutableState.value.systemPatch
                .transform()
        mutableState.update {
            it.copy(
                systemPatch = next,
            )
        }
        viewModelScope.launch {
            systemPatch.save(next)
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
        if (draft.url.isBlank() || addInProgress) return
        addInProgress = true

        val fileName =
            draft.fileName.ifBlank {
                fileNameFromUrl(draft.url)
            }
        val scheduledAt =
            draft.scheduledAtMillis
                ?.takeIf {
                    it > System.currentTimeMillis()
                }

        viewModelScope.launch {
            try {
            val id =
                environment.repository.add(
                    request =
                        YDownloadRequest(
                            backend =
                                state.value.settings
                                    .defaultBackend,
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
                            threadCount =
                                if (
                                    draft.supportsRanges &&
                                    draft.totalBytes > 0L
                                ) {
                                    draft.threadCount
                                        .coerceIn(1, 16)
                                } else {
                                    1
                                },
                            speedLimitBytesPerSecond =
                                draft.speedLimitBytesPerSecond
                                    .coerceAtLeast(0L),
                            customHeaders =
                                parseHeaders(
                                    draft.customHeadersText,
                                ),
                            scheduledAtMillis = scheduledAt,
                        ),
                    queued =
                        scheduledAt == null &&
                            !startNow,
                )

            dismissAddDialog()

            if (scheduledAt != null) {
                val scheduled =
                    environment.scheduler.schedule(
                        downloadId = id,
                        scheduledAtMillis =
                            scheduledAt,
                    )
                if (!scheduled) {
                    environment.repository.updateState(
                        id = id,
                        state = YDownloadState.Failed,
                        error =
                            "Unable to schedule download",
                        queued = false,
                    )
                }
            } else if (startNow) {
                YDownloadService.start(
                    appContext,
                    id,
                )
            } else {
                YDownloadService.pump(appContext)
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.error(TAG, "Unable to add download task", error)
                updateDraft {
                    copy(error = error.message ?: error.javaClass.simpleName)
                }
            } finally {
                addInProgress = false
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

        fun parseHeaders(
            value: String,
        ): Map<String, String> =
            value.lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (
                        trimmed.isBlank() ||
                        trimmed.startsWith("#")
                    ) {
                        return@mapNotNull null
                    }
                    val index = trimmed.indexOf(':')
                    if (index <= 0) {
                        return@mapNotNull null
                    }
                    val name =
                        trimmed.substring(0, index).trim()
                    val headerValue =
                        trimmed.substring(index + 1).trim()
                    if (
                        name.isBlank() ||
                        headerValue.isBlank()
                    ) {
                        null
                    } else {
                        name to headerValue
                    }
                }
                .toMap()

        fun formatHeaders(
            headers: Map<String, String>,
        ): String =
            headers.entries.joinToString("\n") {
                it.key + ": " + it.value
            }

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
