package com.yagay.yfiles

import java.io.File

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val isHidden: Boolean,
)

data class FileProperties(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean,
    val hidden: Boolean,
    val childCount: Int? = null,
)

enum class FileTransferMode { COPY, MOVE }

data class PendingFileTransfer(
    val source: FileEntry,
    val mode: FileTransferMode,
)

fun File.toEntry(): FileEntry = FileEntry(
    name = name.ifBlank { absolutePath },
    path = absolutePath,
    isDirectory = isDirectory,
    size = if (isFile) length() else 0L,
    modified = lastModified(),
    isHidden = isHidden,
)
