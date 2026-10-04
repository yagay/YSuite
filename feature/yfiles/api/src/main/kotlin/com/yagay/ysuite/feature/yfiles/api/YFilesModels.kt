package com.yagay.ysuite.feature.yfiles.api

data class YFileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val hidden: Boolean,
)

data class YFileProperties(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean,
    val hidden: Boolean,
    val childCount: Int?,
)

enum class YFileSort {
    Name,
    Modified,
    Size,
    Type,
}

enum class YFileTransferMode {
    Copy,
    Move,
}

data class YFileQuery(
    val path: String,
    val text: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val maxResults: Int = 500,
)

data class YFileFailure(
    val path: String,
    val code: String,
)

data class YFileBatchResult(
    val succeeded: Int,
    val failures: List<YFileFailure>,
) {
    val failed: Int
        get() = failures.size
}

data class YFileClipboard(
    val entries: List<YFileEntry>,
    val mode: YFileTransferMode,
)

data class YTrashEntry(
    val id: String,
    val originalPath: String,
    val entry: YFileEntry,
)

data class YFilesPlacesSnapshot(
    val favoritePaths: Set<String> = emptySet(),
    val recentPaths: List<String> = emptyList(),
)
