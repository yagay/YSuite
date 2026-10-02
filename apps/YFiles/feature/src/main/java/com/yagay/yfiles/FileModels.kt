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

fun File.toEntry(): FileEntry = FileEntry(
    name = name.ifBlank { absolutePath },
    path = absolutePath,
    isDirectory = isDirectory,
    size = if (isFile) length() else 0L,
    modified = lastModified(),
    isHidden = isHidden,
)
