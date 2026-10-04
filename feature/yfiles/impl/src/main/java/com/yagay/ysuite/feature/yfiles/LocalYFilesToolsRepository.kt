package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YArchiveResult
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameItem
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameRule
import com.yagay.ysuite.feature.yfiles.api.YDirectoryAnalysis
import com.yagay.ysuite.feature.yfiles.api.YDuplicateGroup
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileHash
import com.yagay.ysuite.feature.yfiles.api.YFilesToolsRepository
import com.yagay.ysuite.feature.yfiles.api.YHexPreview
import com.yagay.ysuite.feature.yfiles.api.YTextDocument
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalYFilesToolsRepository : YFilesToolsRepository {
    override suspend fun createZip(
        entries: List<YFileEntry>,
        destinationPath: String,
        archiveName: String,
    ): Outcome<YArchiveResult> =
        ioOutcome("zip_create_failed") {
            require(entries.isNotEmpty()) {
                "Nothing selected"
            }
            val destination = File(destinationPath)
            require(destination.isDirectory) {
                "Destination is not a directory"
            }

            val cleanName = archiveName.trim()
                .ifBlank { "archive.zip" }
                .let {
                    if (it.endsWith(".zip", true)) {
                        it
                    } else {
                        it + ".zip"
                    }
                }
                .replace('/', '_')
                .replace('\\', '_')

            val archive = uniqueTarget(
                destination,
                cleanName,
            )

            var added = 0
            ZipOutputStream(
                BufferedOutputStream(
                    FileOutputStream(archive),
                ),
            ).use { zip ->
                for (item in entries.distinctBy(YFileEntry::path)) {
                    val source = File(item.path)
                    if (!source.exists()) continue
                    val baseName = source.name.ifBlank { "item" }
                    added += addToZip(
                        source = source,
                        zip = zip,
                        entryName = baseName,
                    )
                }
            }

            YArchiveResult(
                archive = toEntry(archive),
                addedEntries = added,
            )
        }

    override suspend fun extractZip(
        archive: YFileEntry,
        destinationPath: String,
    ): Outcome<YFileBatchResult> =
        ioOutcome("zip_extract_failed") {
            val archiveFile = File(archive.path)
            require(archiveFile.isFile) {
                "Archive does not exist"
            }
            val destination = File(destinationPath)
            require(destination.mkdirs() || destination.isDirectory) {
                "Unable to create destination"
            }
            val destinationCanonical =
                destination.canonicalFile

            var succeeded = 0
            val failures = mutableListOf<YFileFailure>()

            ZipInputStream(
                BufferedInputStream(
                    FileInputStream(archiveFile),
                ),
            ).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(
                        destinationCanonical,
                        entry.name,
                    ).canonicalFile

                    val safePrefix =
                        destinationCanonical.path +
                            File.separator
                    if (
                        target.path != destinationCanonical.path &&
                        !target.path.startsWith(safePrefix)
                    ) {
                        failures += YFileFailure(
                            path = entry.name,
                            code = "zip_slip_blocked",
                        )
                        zip.closeEntry()
                        entry = zip.nextEntry
                        continue
                    }

                    try {
                        if (entry.isDirectory) {
                            require(
                                target.mkdirs() ||
                                    target.isDirectory,
                            ) {
                                "Unable to create directory"
                            }
                        } else {
                            target.parentFile?.let { parent ->
                                require(
                                    parent.mkdirs() ||
                                        parent.isDirectory,
                                ) {
                                    "Unable to create parent"
                                }
                            }
                            BufferedOutputStream(
                                FileOutputStream(target),
                            ).use { output ->
                                zip.copyTo(output)
                            }
                        }
                        succeeded += 1
                    } catch (error: Throwable) {
                        failures += YFileFailure(
                            path = entry.name,
                            code = error::class.java.simpleName,
                        )
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            YFileBatchResult(
                succeeded = succeeded,
                failures = failures,
            )
        }

    override suspend fun sha256(
        entry: YFileEntry,
    ): Outcome<YFileHash> =
        ioOutcome("sha256_failed") {
            val file = File(entry.path)
            require(file.isFile) {
                "Not a file"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }

            YFileHash(
                path = file.absolutePath,
                algorithm = "SHA-256",
                hex = digest.digest()
                    .joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xff)
                    },
            )
        }

    override suspend fun findDuplicates(
        rootPath: String,
        maxFiles: Int,
    ): Outcome<List<YDuplicateGroup>> =
        ioOutcome("duplicates_failed") {
            val files = walkFiles(
                root = File(rootPath),
                maxFiles = maxFiles,
            ).first

            val candidates = files
                .groupBy(File::length)
                .values
                .filter { it.size > 1 }

            candidates.flatMap { sameSize ->
                sameSize
                    .groupBy { file ->
                        hashHex(file)
                    }
                    .values
                    .filter { it.size > 1 }
                    .map { group ->
                        YDuplicateGroup(
                            sizeBytes = group.first().length(),
                            hash = hashHex(group.first()),
                            entries = group.map(::toEntry),
                        )
                    }
            }.sortedByDescending { group ->
                group.sizeBytes *
                    (group.entries.size - 1L)
            }
        }

    override suspend fun analyzeDirectory(
        rootPath: String,
        maxFiles: Int,
    ): Outcome<YDirectoryAnalysis> =
        ioOutcome("analysis_failed") {
            val root = File(rootPath)
            require(root.isDirectory) {
                "Not a directory"
            }

            val queue = ArrayDeque<File>()
            queue.add(root)
            var fileCount = 0
            var directoryCount = 0
            var totalBytes = 0L
            var truncated = false
            val largest = mutableListOf<File>()

            while (queue.isNotEmpty()) {
                val directory = queue.removeFirst()
                for (child in directory.listFiles().orEmpty()) {
                    if (child.name == MANAGED_TRASH_NAME) {
                        continue
                    }
                    if (
                        child.isDirectory &&
                        !java.nio.file.Files.isSymbolicLink(
                            child.toPath(),
                        )
                    ) {
                        directoryCount += 1
                        queue.addLast(child)
                    } else if (child.isFile) {
                        fileCount += 1
                        totalBytes += child.length()
                        largest += child
                        if (fileCount >= maxFiles) {
                            truncated = queue.isNotEmpty()
                            break
                        }
                    }
                }
                if (fileCount >= maxFiles) {
                    truncated = true
                    break
                }
            }

            YDirectoryAnalysis(
                path = root.absolutePath,
                fileCount = fileCount,
                directoryCount = directoryCount,
                totalBytes = totalBytes,
                largestFiles = largest
                    .sortedByDescending(File::length)
                    .take(10)
                    .map(::toEntry),
                truncated = truncated,
            )
        }

    override suspend fun readText(
        entry: YFileEntry,
        maxChars: Int,
    ): Outcome<YTextDocument> =
        ioOutcome("text_read_failed") {
            val file = File(entry.path)
            require(file.isFile) {
                "Not a file"
            }
            require(maxChars > 0) {
                "Invalid max chars"
            }

            val buffer = CharArray(4_096)
            val builder = StringBuilder()
            var truncated = false

            file.bufferedReader(Charsets.UTF_8).use { reader ->
                while (builder.length < maxChars) {
                    val remaining =
                        maxChars - builder.length
                    val read = reader.read(
                        buffer,
                        0,
                        minOf(buffer.size, remaining),
                    )
                    if (read < 0) break
                    builder.append(buffer, 0, read)
                }
                truncated = reader.read() >= 0
            }

            YTextDocument(
                path = file.absolutePath,
                text = builder.toString(),
                truncated = truncated,
            )
        }

    override suspend fun writeText(
        entry: YFileEntry,
        text: String,
    ): Outcome<YFileEntry> =
        ioOutcome("text_write_failed") {
            val file = File(entry.path)
            require(file.isFile) {
                "Not a file"
            }
            file.writeText(
                text = text,
                charset = Charsets.UTF_8,
            )
            toEntry(file)
        }

    override suspend fun readHex(
        entry: YFileEntry,
        maxBytes: Int,
    ): Outcome<YHexPreview> =
        ioOutcome("hex_read_failed") {
            val file = File(entry.path)
            require(file.isFile) {
                "Not a file"
            }
            require(maxBytes > 0) {
                "Invalid max bytes"
            }

            val data = ByteArray(maxBytes)
            val count = FileInputStream(file).use {
                it.read(data)
            }.coerceAtLeast(0)

            val lines = buildString {
                var offset = 0
                while (offset < count) {
                    val lineEnd = minOf(
                        count,
                        offset + HEX_LINE_BYTES,
                    )
                    append(
                        offset.toString(16)
                            .padStart(8, '0'),
                    )
                    append("  ")

                    for (index in offset until lineEnd) {
                        append(
                            "%02X".format(
                                data[index].toInt() and 0xff,
                            ),
                        )
                        append(' ')
                    }

                    if (lineEnd < offset + HEX_LINE_BYTES) {
                        repeat(
                            offset +
                                HEX_LINE_BYTES -
                                lineEnd,
                        ) {
                            append("   ")
                        }
                    }

                    append(" ")
                    for (index in offset until lineEnd) {
                        val value =
                            data[index].toInt() and 0xff
                        append(
                            if (value in 32..126) {
                                value.toChar()
                            } else {
                                '.'
                            },
                        )
                    }
                    if (lineEnd < count) {
                        append('\n')
                    }
                    offset = lineEnd
                }
            }

            YHexPreview(
                path = file.absolutePath,
                byteCount = count,
                text = lines,
                truncated = file.length() > count,
            )
        }

    override suspend fun previewBatchRename(
        entries: List<YFileEntry>,
        rule: YBatchRenameRule,
    ): Outcome<List<YBatchRenameItem>> =
        ioOutcome("rename_preview_failed") {
            entries.distinctBy(YFileEntry::path)
                .map { entry ->
                    val source = File(entry.path)
                    var newName = source.name

                    if (rule.find.isNotEmpty()) {
                        newName = newName.replace(
                            rule.find,
                            rule.replace,
                        )
                    }

                    newName =
                        rule.prefix +
                            newName +
                            rule.suffix
                    newName = sanitizeName(newName)

                    require(newName.isNotBlank()) {
                        "Rename result is empty"
                    }

                    YBatchRenameItem(
                        path = source.absolutePath,
                        originalName = source.name,
                        newName = newName,
                    )
                }
        }

    override suspend fun applyBatchRename(
        items: List<YBatchRenameItem>,
    ): Outcome<YFileBatchResult> =
        ioOutcome("batch_rename_failed") {
            val failures = mutableListOf<YFileFailure>()
            var succeeded = 0

            for (item in items.distinctBy(YBatchRenameItem::path)) {
                try {
                    val source = File(item.path)
                    require(source.exists()) {
                        "Source no longer exists"
                    }
                    val parent = source.parentFile
                        ?: error("Missing parent")
                    val target = File(
                        parent,
                        sanitizeName(item.newName),
                    )
                    if (source.absolutePath == target.absolutePath) {
                        succeeded += 1
                        continue
                    }
                    require(!target.exists()) {
                        "Target already exists"
                    }
                    java.nio.file.Files.move(
                        source.toPath(),
                        target.toPath(),
                    )
                    succeeded += 1
                } catch (error: Throwable) {
                    failures += YFileFailure(
                        path = item.path,
                        code = error::class.java.simpleName,
                    )
                }
            }

            YFileBatchResult(
                succeeded = succeeded,
                failures = failures,
            )
        }

    private fun addToZip(
        source: File,
        zip: ZipOutputStream,
        entryName: String,
    ): Int {
        if (
            java.nio.file.Files.isSymbolicLink(
                source.toPath(),
            )
        ) {
            return 0
        }

        if (source.isDirectory) {
            val normalized =
                entryName.trimEnd('/') + "/"
            zip.putNextEntry(ZipEntry(normalized))
            zip.closeEntry()

            var count = 1
            for (child in source.listFiles().orEmpty()) {
                if (child.name == MANAGED_TRASH_NAME) {
                    continue
                }
                count += addToZip(
                    child,
                    zip,
                    normalized + child.name,
                )
            }
            return count
        }

        zip.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(
            FileInputStream(source),
        ).use { input ->
            input.copyTo(zip)
        }
        zip.closeEntry()
        return 1
    }

    private fun walkFiles(
        root: File,
        maxFiles: Int,
    ): Pair<List<File>, Boolean> {
        require(root.isDirectory) {
            "Not a directory"
        }

        val queue = ArrayDeque<File>()
        val files = mutableListOf<File>()
        queue.add(root)
        var truncated = false

        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            for (child in directory.listFiles().orEmpty()) {
                if (child.name == MANAGED_TRASH_NAME) {
                    continue
                }
                if (
                    child.isDirectory &&
                    !java.nio.file.Files.isSymbolicLink(
                        child.toPath(),
                    )
                ) {
                    queue.addLast(child)
                } else if (child.isFile) {
                    files += child
                    if (files.size >= maxFiles) {
                        truncated = true
                        return files to truncated
                    }
                }
            }
        }

        return files to truncated
    }

    private fun hashHex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
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
                        ?: "File tool operation failed",
                    cause = error,
                    code = code,
                    retryable = true,
                )
            }
        }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val HEX_LINE_BYTES = 16
        private const val MANAGED_TRASH_NAME = ".YSuiteTrash"
    }
}
