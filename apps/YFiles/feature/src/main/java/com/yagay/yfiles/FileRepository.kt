package com.yagay.yfiles

import android.content.Context
import android.os.Environment
import java.io.File

class FileRepository(private val context: Context) {
    fun initialPath(): String = Environment.getExternalStorageDirectory().absolutePath

    fun list(path: String, showHidden: Boolean, query: String): Result<List<FileEntry>> = runCatching {
        val directory = File(path)
        require(directory.isDirectory) { "Not a directory: $path" }
        val needle = query.trim().lowercase()
        directory.listFiles().orEmpty()
            .asSequence()
            .filter { showHidden || !it.isHidden }
            .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
            .map(File::toEntry)
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun createFolder(parent: String, name: String): Result<FileEntry> = runCatching {
        val target = File(parent, name.trim())
        require(target.name.isNotBlank()) { "Folder name is empty" }
        require(!target.exists()) { "Already exists" }
        require(target.mkdirs()) { "Unable to create folder" }
        target.toEntry()
    }

    fun delete(entry: FileEntry): Result<Unit> = runCatching {
        val file = File(entry.path)
        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        require(success || !file.exists()) { "Unable to delete ${entry.name}" }
    }

    fun parent(path: String): String? = File(path).parentFile?.absolutePath
}
