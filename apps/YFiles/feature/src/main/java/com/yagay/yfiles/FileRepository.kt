package com.yagay.yfiles

import android.content.Context
import android.os.Environment
import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        val target = File(parent, sanitizeName(name))
        require(target.name.isNotBlank()) { "Folder name is empty" }
        require(!target.exists()) { "Already exists" }
        require(target.mkdirs()) { "Unable to create folder" }
        target.toEntry()
    }

    fun createFile(parent: String, name: String): Result<FileEntry> = runCatching {
        val target = File(parent, sanitizeName(name))
        require(target.name.isNotBlank()) { "File name is empty" }
        require(!target.exists()) { "Already exists" }
        target.parentFile?.mkdirs()
        require(target.createNewFile()) { "Unable to create file" }
        target.toEntry()
    }

    fun rename(entry: FileEntry, newName: String): Result<FileEntry> = runCatching {
        val source = File(entry.path)
        require(source.exists()) { "Source no longer exists" }
        val clean = sanitizeName(newName)
        require(clean.isNotBlank()) { "Name is empty" }
        require(clean != "." && clean != "..") { "Invalid name" }
        val target = File(source.parentFile ?: error("Missing parent folder"), clean)
        require(source.absolutePath != target.absolutePath) { "Name is unchanged" }
        require(!target.exists()) { "Already exists" }
        Files.move(source.toPath(), target.toPath())
        target.toEntry()
    }

    fun properties(entry: FileEntry): Result<FileProperties> = runCatching {
        val file = File(entry.path)
        require(file.exists()) { "File no longer exists" }
        val size = if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
        FileProperties(
            name = file.name.ifBlank { file.absolutePath },
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = size,
            modified = file.lastModified(),
            readable = file.canRead(),
            writable = file.canWrite(),
            executable = file.canExecute(),
            hidden = file.isHidden,
            childCount = if (file.isDirectory) file.listFiles()?.size else null,
        )
    }

    fun transfer(transfer: PendingFileTransfer, destinationDirectory: String): Result<FileEntry> = runCatching {
        val source = File(transfer.source.path)
        val destination = File(destinationDirectory)
        require(source.exists()) { "Source no longer exists" }
        require(destination.isDirectory) { "Destination is not a directory" }

        val canonicalSource = source.canonicalFile
        val canonicalDestination = destination.canonicalFile
        if (canonicalSource.isDirectory) {
            require(
                canonicalDestination.path != canonicalSource.path &&
                    !canonicalDestination.path.startsWith(canonicalSource.path + File.separator)
            ) { "Cannot copy or move a folder into itself" }
        }

        val target = uniqueTarget(canonicalDestination, canonicalSource.name)
        when (transfer.mode) {
            FileTransferMode.COPY -> copyRecursively(canonicalSource, target)
            FileTransferMode.MOVE -> moveWithFallback(canonicalSource, target)
        }
        target.toEntry()
    }

    fun delete(entry: FileEntry): Result<Unit> = runCatching {
        val file = File(entry.path)
        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        require(success || !file.exists()) { "Unable to delete ${entry.name}" }
    }

    fun parent(path: String): String? = File(path).parentFile?.absolutePath

    private fun moveWithFallback(source: File, target: File) {
        runCatching {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(source.toPath(), target.toPath())
        }.recoverCatching {
            copyRecursively(source, target)
            val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
            require(deleted || !source.exists()) { "Copied but could not remove original" }
        }.getOrThrow()
    }

    private fun copyRecursively(source: File, target: File) {
        if (source.isDirectory) {
            require(target.mkdirs() || target.isDirectory) { "Unable to create ${target.name}" }
            source.listFiles().orEmpty().forEach { child ->
                copyRecursively(child, File(target, child.name))
            }
            runCatching { target.setLastModified(source.lastModified()) }
            return
        }

        target.parentFile?.mkdirs()
        val options: Array<CopyOption> = arrayOf(
            StandardCopyOption.COPY_ATTRIBUTES,
            StandardCopyOption.REPLACE_EXISTING,
        )
        Files.copy(source.toPath(), target.toPath(), *options)
    }

    private fun uniqueTarget(parent: File, originalName: String): File {
        var candidate = File(parent, originalName)
        if (!candidate.exists()) return candidate

        val dot = originalName.lastIndexOf('.')
        val hasExtension = dot > 0 && dot < originalName.lastIndex
        val base = if (hasExtension) originalName.substring(0, dot) else originalName
        val extension = if (hasExtension) originalName.substring(dot) else ""
        var index = 1
        while (candidate.exists()) {
            candidate = File(parent, "$base ($index)$extension")
            index++
        }
        return candidate
    }

    private fun sanitizeName(raw: String): String = raw.trim()
        .replace('/', '_')
        .replace('\\', '_')
        .replace('\u0000', '_')
}
