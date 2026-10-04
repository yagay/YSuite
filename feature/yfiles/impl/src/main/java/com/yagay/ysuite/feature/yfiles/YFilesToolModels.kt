package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileRef

data class YFilesPlacesSnapshot(
    val favorites: List<YFileLocationRecord>,
    val recent: List<YFileLocationRecord>,
)

data class YFileLocationRecord(
    val ref: YFileRef,
    val label: String,
)

data class YTrashRecord(
    val id: String,
    val originalParent: YFileRef,
    val originalName: String,
    val trashedRef: YFileRef,
    val deletedAtMillis: Long,
)

data class YHashResult(
    val ref: YFileRef,
    val algorithm: String,
    val hex: String,
)

data class YDirectoryAnalysis(
    val root: YFileRef,
    val fileCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    val largestFiles: List<YFileNode>,
    val truncated: Boolean,
)

data class YDuplicateGroup(
    val sizeBytes: Long,
    val hash: String,
    val nodes: List<YFileNode>,
)

data class YTextDocument(
    val ref: YFileRef,
    val text: String,
    val truncated: Boolean,
)

data class YHexPreview(
    val ref: YFileRef,
    val byteCount: Int,
    val text: String,
    val truncated: Boolean,
)

data class YBatchRenameRule(
    val prefix: String = "",
    val suffix: String = "",
    val find: String = "",
    val replace: String = "",
    val regex: Boolean = false,
)

data class YBatchRenameItem(
    val ref: YFileRef,
    val originalName: String,
    val newName: String,
)

data class YCompareResult(
    val left: YFileRef,
    val right: YFileRef,
    val identical: Boolean,
    val leftSize: Long?,
    val rightSize: Long?,
    val leftHash: String,
    val rightHash: String,
)

data class YCleanupReport(
    val scanned: Int,
    val totalBytes: Long,
    val largeFiles: List<YFileNode>,
    val oldFiles: List<YFileNode>,
    val hiddenFiles: List<YFileNode>,
    val apkFiles: List<YFileNode>,
    val oldDownloads: List<YFileNode>,
    val screenshots: List<YFileNode>,
    val recordings: List<YFileNode>,
    val emptyDirectories: List<YFileNode>,
    val truncated: Boolean,
)
