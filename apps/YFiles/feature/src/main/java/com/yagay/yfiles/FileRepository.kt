package com.yagay.yfiles

import android.content.Context
import android.os.Environment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val context: Context) {
    fun initialPath(): String = Environment.getExternalStorageDirectory().absolutePath

    fun list(
        path: String,
        showHidden: Boolean,
        query: String,
        sortMode: FileSortMode = FileSortMode.NAME,
        descending: Boolean = false,
    ): Result<List<FileEntry>> = runCatching {
        val directory = File(path)
        require(directory.isDirectory) { "Not a directory: $path" }
        val needle = query.trim().lowercase()
        val entries = directory.listFiles().orEmpty()
            .asSequence()
            .filter { showHidden || !it.isHidden }
            .filter { needle.isBlank() || it.name.lowercase().contains(needle) }
            .map(File::toEntry)
            .toList()
        sortEntries(entries, sortMode, descending)
    }

    fun searchRecursive(
        path: String,
        showHidden: Boolean,
        query: String,
        sortMode: FileSortMode = FileSortMode.NAME,
        descending: Boolean = false,
        maxResults: Int = 500,
    ): Result<List<FileEntry>> = runCatching {
        val root = File(path)
        require(root.isDirectory) { "Not a directory: $path" }
        val needle = query.trim().lowercase()
        require(needle.isNotBlank()) { "Search query is empty" }

        val queue = ArrayDeque<File>()
        val results = ArrayList<FileEntry>()
        queue.add(root)
        while (queue.isNotEmpty() && results.size < maxResults) {
            val directory = queue.removeFirst()
            directory.listFiles().orEmpty().forEach { child ->
                if (!showHidden && child.isHidden) return@forEach
                if (child.name.lowercase().contains(needle)) {
                    results += child.toEntry()
                    if (results.size >= maxResults) return@forEach
                }
                if (child.isDirectory && !Files.isSymbolicLink(child.toPath())) queue.add(child)
            }
        }
        sortEntries(results, sortMode, descending)
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

    fun duplicate(entry: FileEntry): Result<FileEntry> = runCatching {
        val source = File(entry.path)
        require(source.exists()) { "Source no longer exists" }
        val parent = source.parentFile ?: error("Missing parent folder")
        val target = uniqueTarget(parent, source.name)
        try {
            copyRecursively(source, target)
            target.toEntry()
        } catch (t: Throwable) {
            removePartialTarget(target)
            throw t
        }
    }

    fun properties(entry: FileEntry): Result<FileProperties> = runCatching {
        val file = File(entry.path)
        require(file.exists()) { "File no longer exists" }
        FileProperties(
            name = file.name.ifBlank { file.absolutePath },
            path = file.absolutePath,
            isDirectory = file.isDirectory,
            size = if (file.isDirectory) directorySize(file) else file.length(),
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
        try {
            when (transfer.mode) {
                FileTransferMode.COPY -> copyRecursively(canonicalSource, target)
                FileTransferMode.MOVE -> moveWithFallback(canonicalSource, target)
            }
            target.toEntry()
        } catch (t: Throwable) {
            if (transfer.mode == FileTransferMode.COPY && canonicalSource.exists()) removePartialTarget(target)
            throw t
        }
    }

    fun compressZip(entry: FileEntry): Result<FileEntry> = runCatching {
        val source = File(entry.path)
        require(source.exists()) { "Source no longer exists" }
        val parent = source.parentFile ?: error("Missing parent folder")
        val target = uniqueTarget(parent, source.name + ".zip")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
                addToZip(source, source.parentFile ?: parent, zip)
            }
            target.toEntry()
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun extractZip(entry: FileEntry): Result<FileEntry> = runCatching {
        val source = File(entry.path)
        require(source.isFile) { "Archive no longer exists" }
        require(source.extension.equals("zip", ignoreCase = true)) { "Only ZIP archives are supported" }
        val parent = source.parentFile ?: error("Missing parent folder")
        val baseName = source.nameWithoutExtension.ifBlank { "archive" }
        val targetDirectory = uniqueTarget(parent, baseName)
        require(targetDirectory.mkdirs()) { "Unable to create extraction folder" }
        val rootPath = targetDirectory.canonicalFile.toPath()

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
                while (true) {
                    val next = zip.nextEntry ?: break
                    val output = File(targetDirectory, next.name).canonicalFile
                    require(output.toPath().startsWith(rootPath)) { "Unsafe ZIP entry: ${next.name}" }
                    if (next.isDirectory) {
                        require(output.mkdirs() || output.isDirectory) { "Unable to create ${next.name}" }
                    } else {
                        output.parentFile?.let { require(it.mkdirs() || it.isDirectory) }
                        BufferedOutputStream(FileOutputStream(output)).use { stream ->
                            zip.copyTo(stream, COPY_BUFFER_SIZE)
                        }
                        if (next.time > 0L) runCatching { output.setLastModified(next.time) }
                    }
                    zip.closeEntry()
                }
            }
            targetDirectory.toEntry()
        } catch (t: Throwable) {
            targetDirectory.deleteRecursively()
            throw t
        }
    }

    fun delete(entry: FileEntry): Result<Unit> = runCatching {
        val file = File(entry.path)
        val success = if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
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
            val deleted = if (source.isDirectory && !Files.isSymbolicLink(source.toPath())) {
                source.deleteRecursively()
            } else {
                source.delete()
            }
            require(deleted || !source.exists()) { "Copied but could not remove original" }
        }.getOrThrow()
    }

    private fun copyRecursively(source: File, target: File) {
        val sourcePath = source.toPath()
        if (Files.isSymbolicLink(sourcePath)) {
            target.parentFile?.mkdirs()
            Files.createSymbolicLink(target.toPath(), Files.readSymbolicLink(sourcePath))
            return
        }
        if (source.isDirectory) {
            require(target.mkdirs() || target.isDirectory) { "Unable to create ${target.name}" }
            source.listFiles().orEmpty().forEach { child -> copyRecursively(child, File(target, child.name)) }
            runCatching { target.setLastModified(source.lastModified()) }
            return
        }
        target.parentFile?.mkdirs()
        val options: Array<CopyOption> = arrayOf(StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
        Files.copy(sourcePath, target.toPath(), *options)
    }

    private fun addToZip(source: File, rootParent: File, zip: ZipOutputStream) {
        if (Files.isSymbolicLink(source.toPath())) return
        val relative = rootParent.toPath().relativize(source.toPath()).toString().replace(File.separatorChar, '/')
        if (source.isDirectory) {
            val directoryName = relative.trimEnd('/') + "/"
            if (directoryName != "/") {
                zip.putNextEntry(ZipEntry(directoryName).apply { time = source.lastModified() })
                zip.closeEntry()
            }
            source.listFiles().orEmpty().forEach { child -> addToZip(child, rootParent, zip) }
            return
        }
        zip.putNextEntry(ZipEntry(relative).apply { time = source.lastModified() })
        BufferedInputStream(FileInputStream(source)).use { input -> input.copyTo(zip, COPY_BUFFER_SIZE) }
        zip.closeEntry()
    }

    private fun directorySize(root: File): Long {
        var total = 0L
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            queue.removeFirst().listFiles().orEmpty().forEach { child ->
                if (Files.isSymbolicLink(child.toPath())) return@forEach
                if (child.isDirectory) queue.add(child) else total += child.length()
            }
        }
        return total
    }

    private fun sortEntries(entries: List<FileEntry>, sortMode: FileSortMode, descending: Boolean): List<FileEntry> {
        val detailComparator: Comparator<FileEntry> = when (sortMode) {
            FileSortMode.NAME -> compareBy { it.name.lowercase() }
            FileSortMode.MODIFIED -> compareBy<FileEntry> { it.modified }.thenBy { it.name.lowercase() }
            FileSortMode.SIZE -> compareBy<FileEntry> { it.size }.thenBy { it.name.lowercase() }
            FileSortMode.TYPE -> compareBy<FileEntry> {
                if (it.isDirectory) "" else it.name.substringAfterLast('.', "").lowercase()
            }.thenBy { it.name.lowercase() }
        }
        val effective = if (descending) detailComparator.reversed() else detailComparator
        return entries.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.then(effective))
    }

    private fun removePartialTarget(target: File) {
        runCatching {
            if (target.isDirectory && !Files.isSymbolicLink(target.toPath())) target.deleteRecursively() else target.delete()
        }
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

    companion object {
        private const val COPY_BUFFER_SIZE = 128 * 1024
    }
}
