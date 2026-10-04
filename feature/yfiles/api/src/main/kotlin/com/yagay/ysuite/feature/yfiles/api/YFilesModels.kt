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

data class YFileQuery(
    val path: String,
    val text: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val maxResults: Int = 500,
)
