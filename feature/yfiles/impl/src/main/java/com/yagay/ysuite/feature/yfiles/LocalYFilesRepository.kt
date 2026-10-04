package com.yagay.ysuite.feature.yfiles

import android.os.Environment
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileProperties
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileTransferMode
import com.yagay.ysuite.feature.yfiles.api.YFilesRepository
import com.yagay.ysuite.feature.yfiles.api.YTrashEntry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalYFilesRepository(
    private val rootPath: String =
        Environment.getExternalStorageDirectory().absolutePath,
) : YFilesRepository {
    private val trashRoot: File
        get() = File(rootPath, TRASH_DIRECTORY)

    override fun initialPath(): String = rootPath

    override suspend fun list(
        query: YFileQuery,
    ): Outcome<List<YFileEntry>> =
        ioOutcome("list_failed") {
            val root = File(query.path)
            require(root.isDirectory) {
                "Not a readable directory"
            }

            val text = query.text.trim().lowercase()
            val entries =
                if (query.recursive && text.isNotBlank()) {
                    recursiveEntries(
                        root = root,
                        text = text,
                        showHidden = query.showHidden,
                        maxResults = query.maxResults,
                    )
                } else {
                    root.listFiles()
                        .orEmpty()
                        .asSequence()
                        .filterNot(::isManagedTrash)
                        .filter {
                            query.showHidden || !it.isHidden
                        }
                        .filter {
                            text.isBlank() ||
                                it.name.lowercase().contains(text)
                        }
                        .map(::toEntry)
                        .toList()
                }

            sort(
                entries = entries,
                mode = query.sort,
                descending = query.descending,
            )
        }

    override suspend fun properties(
        entry: YFileEntry,
    ): Outcome<YFileProperties> =
        ioOutcome("properties_failed") {
            val file = File(entry.path)
            require(file.exists()) {
                "File no longer exists"
            }
            require(!isManagedTrash(file)) {
                "Managed trash item"
            }

            YFileProperties(
                name = file.name.ifBlank { file.absolutePath },
                path = file.absolutePath,
                directory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                modifiedAtMillis = file.lastModified(),
                readable = file.canRead(),
                writable = file.canWrite(),
                executable = file.canExecute(),
                hidden = file.isHidden,
                childCount = if (file.isDirectory) {
                    file.listFiles()
                        .orEmpty()
                        .count { !isManagedTrash(it) }
                } else {
                    null
                },
            )
        }

    override suspend fun createDirectory(
        parentPath: String,
        name: String,
    ): Outcome<YFileEntry> =
        ioOutcome("create_directory_failed") {
            val target = createTarget(parentPath, name)
            require(target.mkdirs()) {
                "Unable to create directory"
            }
            toEntry(target)
        }

    override suspend fun createFile(
        parentPath: String,
        name: String,
    ): Outcome<YFileEntry> =
        ioOutcome("create_file_failed") {
            val target = createTarget(parentPath, name)
            target.parentFile?.mkdirs()
            require(target.createNewFile()) {
                "Unable to create file"
            }
            toEntry(target)
        }

    override suspend fun rename(
        entry: YFileEntry,
        newName: String,
    ): Outcome<YFileEntry> =
        ioOutcome("rename_failed") {
            val source = File(entry.path)
            require(source.exists()) {
                "Source no longer exists"
            }
            require(!isManagedTrash(source)) {
                "Managed trash item"
            }

            val cleanName = sanitizeName(newName)
            require(cleanName.isNotBlank()) {
                "Name is empty"
            }
            val parent = source.parentFile
                ?: error("Missing parent directory")
            val target = File(parent, cleanName)
            require(!target.exists()) {
                "Target already exists"
            }

            Files.move(source.toPath(), target.toPath())
            toEntry(target)
        }

    override suspend fun transfer(
        entries: List<YFileEntry>,
        destinationPath: String,
        mode: YFileTransferMode,
    ): Outcome<YFileBatchResult> =
        ioOutcome("transfer_failed") {
            val destination = File(destinationPath).canonicalFile
            require(destination.isDirectory) {
                "Destination is not a directory"
            }

            batch(entries) { entry ->
                val source = File(entry.path).canonicalFile
                require(source.exists()) {
                    "Source no longer exists"
                }
                require(!isManagedTrash(source)) {
                    "Managed trash item"
                }

                if (source.isDirectory) {
                    require(
                        destination.path != source.path &&
                            !destination.path.startsWith(
                                source.path + File.separator,
                            ),
                    ) {
                        "Cannot transfer a directory into itself"
                    }
                }

                val target = uniqueTarget(
                    destination,
                    source.name,
                )
                when (mode) {
                    YFileTransferMode.Copy ->
                        copyRecursively(source, target)
                    YFileTransferMode.Move ->
                        moveWithFallback(source, target)
                }
            }
        }

    override suspend fun moveToTrash(
        entries: List<YFileEntry>,
    ): Outcome<YFileBatchResult> =
        ioOutcome("trash_failed") {
            ensureTrashRoot()
            batch(entries) { entry ->
                val source = File(entry.path).canonicalFile
                require(source.exists()) {
                    "Source no longer exists"
                }
                require(!isManagedTrash(source)) {
                    "Item is already managed by trash"
                }

                val bucket = File(
                    trashRoot,
                    UUID.randomUUID().toString(),
                )
                require(bucket.mkdirs()) {
                    "Unable to create trash bucket"
                }

                File(bucket, ORIGIN_FILE).writeText(
                    source.absolutePath,
                )
                val payload = File(bucket, PAYLOAD_NAME)
                try {
                    moveWithFallback(source, payload)
                } catch (error: Throwable) {
                    bucket.deleteRecursively()
                    throw error
                }
            }
        }

    override suspend fun listTrash(): Outcome<List<YTrashEntry>> =
        ioOutcome("list_trash_failed") {
            if (!trashRoot.exists()) {
                return@ioOutcome emptyList()
            }

            trashRoot.listFiles()
                .orEmpty()
                .asSequence()
                .filter(File::isDirectory)
                .mapNotNull(::readTrashEntry)
                .sortedByDescending {
                    File(trashRoot, it.id).lastModified()
                }
                .toList()
        }

    override suspend fun restoreTrash(
        ids: Set<String>,
    ): Outcome<YFileBatchResult> =
        ioOutcome("restore_trash_failed") {
            val targets = ids.map { id ->
                File(trashRoot, id)
            }
            batchFiles(targets) { bucket ->
                val origin = File(bucket, ORIGIN_FILE)
                    .takeIf(File::isFile)
                    ?.readText()
                    ?.trim()
                    .orEmpty()
                require(origin.isNotBlank()) {
                    "Missing trash origin"
                }

                val payload = File(bucket, PAYLOAD_NAME)
                require(payload.exists()) {
                    "Missing trash payload"
                }

                val original = File(origin)
                val parent = original.parentFile
                    ?: error("Missing restore directory")
                require(parent.mkdirs() || parent.isDirectory) {
                    "Unable to restore parent directory"
                }

                val target = if (original.exists()) {
                    uniqueTarget(parent, original.name)
                } else {
                    original
                }
                moveWithFallback(payload, target)
                bucket.deleteRecursively()
            }
        }

    override suspend fun emptyTrash(): Outcome<Int> =
        ioOutcome("empty_trash_failed") {
            if (!trashRoot.exists()) {
                return@ioOutcome 0
            }

            val buckets = trashRoot.listFiles()
                .orEmpty()
                .filter(File::isDirectory)
            var removed = 0
            for (bucket in buckets) {
                if (bucket.deleteRecursively()) {
                    removed += 1
                }
            }
            if (trashRoot.listFiles().isNullOrEmpty()) {
                trashRoot.delete()
            }
            removed
        }

    override suspend fun resolve(
        paths: Collection<String>,
    ): Outcome<List<YFileEntry>> =
        ioOutcome("resolve_failed") {
            paths.asSequence()
                .map(::File)
                .filter(File::exists)
                .filterNot(::isManagedTrash)
                .map(::toEntry)
                .toList()
        }

    override fun parent(path: String): String? =
        File(path).parentFile?.absolutePath

    private fun createTarget(
        parentPath: String,
        rawName: String,
    ): File {
        val parent = File(parentPath)
        require(parent.isDirectory) {
            "Parent is not a directory"
        }
        val cleanName = sanitizeName(rawName)
        require(cleanName.isNotBlank()) {
            "Name is empty"
        }
        val target = File(parent, cleanName)
        require(!isManagedTrash(target)) {
            "Reserved trash path"
        }
        require(!target.exists()) {
            "Target already exists"
        }
        return target
    }

    private fun recursiveEntries(
        root: File,
        text: String,
        showHidden: Boolean,
        maxResults: Int,
    ): List<YFileEntry> {
        val queue = ArrayDeque<File>()
        val result = mutableListOf<YFileEntry>()
        queue.add(root)

        while (queue.isNotEmpty() && result.size < maxResults) {
            val directory = queue.removeFirst()
            val children = directory.listFiles().orEmpty()

            for (child in children) {
                if (isManagedTrash(child)) continue
                if (!showHidden && child.isHidden) continue

                if (child.name.lowercase().contains(text)) {
                    result += toEntry(child)
                    if (result.size >= maxResults) break
                }

                if (
                    child.isDirectory &&
                    !Files.isSymbolicLink(child.toPath())
                ) {
                    queue.addLast(child)
                }
            }
        }

        return result
    }

    private fun sort(
        entries: List<YFileEntry>,
        mode: YFileSort,
        descending: Boolean,
    ): List<YFileEntry> {
        val detail = when (mode) {
            YFileSort.Name ->
                compareBy<YFileEntry> { it.name.lowercase() }
            YFileSort.Modified ->
                compareBy<YFileEntry> { it.modifiedAtMillis }
                    .thenBy { it.name.lowercase() }
            YFileSort.Size ->
                compareBy<YFileEntry> { it.sizeBytes }
                    .thenBy { it.name.lowercase() }
            YFileSort.Type ->
                compareBy<YFileEntry> {
                    if (it.directory) {
                        ""
                    } else {
                        it.name.substringAfterLast(
                            delimiter = '.',
                            missingDelimiterValue = "",
                        ).lowercase()
                    }
                }.thenBy { it.name.lowercase() }
        }

        val direction =
            if (descending) detail.reversed() else detail

        return entries.sortedWith(
            compareByDescending<YFileEntry> { it.directory }
                .then(direction),
        )
    }

    private fun batch(
        entries: List<YFileEntry>,
        action: (YFileEntry) -> Unit,
    ): YFileBatchResult {
        val failures = mutableListOf<YFileFailure>()
        var succeeded = 0

        for (entry in entries.distinctBy(YFileEntry::path)) {
            try {
                action(entry)
                succeeded += 1
            } catch (error: Throwable) {
                failures += YFileFailure(
                    path = entry.path,
                    code = error::class.java.simpleName,
                )
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            failures = failures,
        )
    }

    private fun batchFiles(
        files: List<File>,
        action: (File) -> Unit,
    ): YFileBatchResult {
        val failures = mutableListOf<YFileFailure>()
        var succeeded = 0

        for (file in files.distinctBy(File::getAbsolutePath)) {
            try {
                action(file)
                succeeded += 1
            } catch (error: Throwable) {
                failures += YFileFailure(
                    path = file.absolutePath,
                    code = error::class.java.simpleName,
                )
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            failures = failures,
        )
    }

    private fun ensureTrashRoot() {
        require(trashRoot.mkdirs() || trashRoot.isDirectory) {
            "Unable to create trash directory"
        }
    }

    private fun readTrashEntry(
        bucket: File,
    ): YTrashEntry? {
        val originFile = File(bucket, ORIGIN_FILE)
        val payload = File(bucket, PAYLOAD_NAME)
        if (!originFile.isFile || !payload.exists()) {
            return null
        }
        val origin = originFile.readText().trim()
        if (origin.isBlank()) {
            return null
        }

        val originalName = File(origin).name
        val entry = toEntry(payload).copy(
            name = originalName.ifBlank {
                payload.name
            },
        )
        return YTrashEntry(
            id = bucket.name,
            originalPath = origin,
            entry = entry,
        )
    }

    private fun moveWithFallback(
        source: File,
        target: File,
    ) {
        runCatching {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.recoverCatching {
            Files.move(source.toPath(), target.toPath())
        }.recoverCatching {
            copyRecursively(source, target)
            val removed =
                if (
                    source.isDirectory &&
                    !Files.isSymbolicLink(source.toPath())
                ) {
                    source.deleteRecursively()
                } else {
                    source.delete()
                }
            require(removed || !source.exists()) {
                "Unable to remove original"
            }
        }.getOrThrow()
    }

    private fun copyRecursively(
        source: File,
        target: File,
    ) {
        val sourcePath = source.toPath()
        if (Files.isSymbolicLink(sourcePath)) {
            target.parentFile?.mkdirs()
            Files.createSymbolicLink(
                target.toPath(),
                Files.readSymbolicLink(sourcePath),
            )
            return
        }

        if (source.isDirectory) {
            require(target.mkdirs() || target.isDirectory) {
                "Unable to create destination directory"
            }
            for (child in source.listFiles().orEmpty()) {
                if (!isManagedTrash(child)) {
                    copyRecursively(
                        child,
                        File(target, child.name),
                    )
                }
            }
            return
        }

        target.parentFile?.mkdirs()
        Files.copy(
            sourcePath,
            target.toPath(),
            StandardCopyOption.COPY_ATTRIBUTES,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun uniqueTarget(
        parent: File,
        originalName: String,
    ): File {
        var candidate = File(parent, originalName)
        if (!candidate.exists()) {
            return candidate
        }

        val dot = originalName.lastIndexOf('.')
        val hasExtension =
            dot > 0 && dot < originalName.lastIndex
        val base = if (hasExtension) {
            originalName.substring(0, dot)
        } else {
            originalName
        }
        val extension = if (hasExtension) {
            originalName.substring(dot)
        } else {
            ""
        }

        var index = 1
        while (candidate.exists()) {
            candidate = File(
                parent,
                base + " (" + index + ")" + extension,
            )
            index += 1
        }
        return candidate
    }

    private fun isManagedTrash(file: File): Boolean {
        val trash = trashRoot.absoluteFile
            .toPath()
            .normalize()
        val candidate = file.absoluteFile
            .toPath()
            .normalize()
        return candidate == trash ||
            candidate.startsWith(trash)
    }

    private fun sanitizeName(raw: String): String =
        raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')

    private fun toEntry(file: File): YFileEntry =
        YFileEntry(
            name = file.name.ifBlank {
                file.absolutePath
            },
            path = file.absolutePath,
            directory = file.isDirectory,
            sizeBytes = if (file.isFile) {
                file.length()
            } else {
                0L
            },
            modifiedAtMillis = file.lastModified(),
            hidden = file.isHidden,
        )

    private suspend inline fun <T> ioOutcome(
        code: String,
        crossinline block: () -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                Outcome.Success(block())
            } catch (error: Throwable) {
                Outcome.Failure(
                    message = error.message
                        ?: "File operation failed",
                    cause = error,
                    code = code,
                    retryable = true,
                )
            }
        }

    companion object {
        private const val TRASH_DIRECTORY = ".YSuiteTrash"
        private const val ORIGIN_FILE = ".origin"
        private const val PAYLOAD_NAME = "payload"
    }
}
