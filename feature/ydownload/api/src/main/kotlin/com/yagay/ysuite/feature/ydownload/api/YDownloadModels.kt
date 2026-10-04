package com.yagay.ysuite.feature.ydownload.api

enum class YDownloadState {
    Pending,
    Connecting,
    Downloading,
    Paused,
    Completed,
    Failed,
    Cancelled,
}

enum class YDownloadTab {
    All,
    Downloading,
    Pending,
    Queue,
    Finished,
    Error,
}

data class YDownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val outputUri: String?,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val state: YDownloadState,
    val speedBytesPerSecond: Long,
    val etaSeconds: Long,
    val addedAtMillis: Long,
    val completedAtMillis: Long?,
    val errorMessage: String?,
    val supportsRanges: Boolean,
    val queued: Boolean,
    val referer: String?,
    val userAgent: String?,
    val cookies: String?,
    val username: String?,
    val password: String?,
    val destinationTreeUri: String? = null,
) {
    val progress: Float?
        get() =
            totalBytes
                .takeIf { it > 0L }
                ?.let {
                    (downloadedBytes.toFloat() / it)
                        .coerceIn(0f, 1f)
                }
}

data class YDownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val totalBytes: Long = -1L,
    val supportsRanges: Boolean = false,
    val referer: String? = null,
    val userAgent: String? = null,
    val cookies: String? = null,
    val username: String? = null,
    val password: String? = null,
    val destinationTreeUri: String? = null,
)

data class YDownloadMetadata(
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val supportsRanges: Boolean,
)

fun List<YDownloadItem>.forTab(
    tab: YDownloadTab,
    query: String = "",
): List<YDownloadItem> {
    val byTab =
        when (tab) {
            YDownloadTab.All -> this
            YDownloadTab.Downloading ->
                filter {
                    it.state == YDownloadState.Downloading ||
                        it.state == YDownloadState.Connecting
                }
            YDownloadTab.Pending ->
                filter {
                    it.state == YDownloadState.Pending &&
                        !it.queued
                }
            YDownloadTab.Queue ->
                filter {
                    it.state == YDownloadState.Pending &&
                        it.queued
                }
            YDownloadTab.Finished ->
                filter {
                    it.state == YDownloadState.Completed
                }
            YDownloadTab.Error ->
                filter {
                    it.state == YDownloadState.Failed
                }
        }

    if (query.isBlank()) return byTab

    return byTab.filter {
        it.fileName.contains(query, ignoreCase = true) ||
            it.url.contains(query, ignoreCase = true)
    }
}
