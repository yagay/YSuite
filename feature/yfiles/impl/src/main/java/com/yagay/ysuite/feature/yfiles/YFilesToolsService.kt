package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileConflictStrategy
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import com.yagay.ysuite.feature.yfiles.provider.archive.YFilesArchiveController
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YFilesToolsService(
    private val engine: YFilesEngine,
    private val archives: YFilesArchiveController,
    private val cacheDirectory: File,
) {
    suspend fun sha256(
        ref: YFileRef,
    ): Outcome<YHashResult> =
        try {
            val digest =
                MessageDigest.getInstance(
                    "SHA-256",
                )
            stream(ref) {
                digest.update(it)
            }
            Outcome.Success(
                YHashResult(
                    ref = ref,
                    algorithm = "SHA-256",
                    hex = digest.digest()
                        .joinToString("") {
                            byte ->
                            "%02x".format(
                                byte.toInt() and
                                    0xff,
                            )
                        },
                ),
            )
        } catch (error: Throwable) {
            failure(
                "sha256_failed",
                error,
                HASH_MESSAGE,
            )
        }

    suspend fun analyze(
        root: YFileRef,
        maxEntries: Int = 20_000,
    ): Outcome<YDirectoryAnalysis> =
        try {
            var files = 0
            var directories = 0
            var bytes = 0L
            var truncated = false
            val largest =
                mutableListOf<YFileNode>()
            val queue =
                ArrayDeque<YFileRef>()
            queue.add(root)

            while (
                queue.isNotEmpty() &&
                files + directories <
                    maxEntries
            ) {
                val current =
                    queue.removeFirst()
                val listed = engine.list(
                    current,
                    YFileQuery(
                        showHidden = true,
                        maxResults =
                            maxEntries,
                    ),
                )
                if (listed is Outcome.Failure) {
                    return listed
                }
                listed as Outcome.Success

                for (node in listed.value) {
                    if (
                        node.name ==
                            TRASH_DIRECTORY
                    ) {
                        continue
                    }
                    if (
                        node.type ==
                            YFileType.Directory
                    ) {
                        directories += 1
                        queue.addLast(node.ref)
                    } else if (
                        node.type ==
                            YFileType.File
                    ) {
                        files += 1
                        bytes +=
                            node.sizeBytes ?: 0L
                        largest += node
                    }

                    if (
                        files + directories >=
                            maxEntries
                    ) {
                        truncated = true
                        break
                    }
                }
            }

            Outcome.Success(
                YDirectoryAnalysis(
                    root = root,
                    fileCount = files,
                    directoryCount =
                        directories,
                    totalBytes = bytes,
                    largestFiles = largest
                        .sortedByDescending {
                            it.sizeBytes ?: 0L
                        }
                        .take(20),
                    truncated = truncated ||
                        queue.isNotEmpty(),
                ),
            )
        } catch (error: Throwable) {
            failure(
                "analysis_failed",
                error,
                ANALYSIS_MESSAGE,
            )
        }

    suspend fun duplicates(
        root: YFileRef,
        maxFiles: Int = 5_000,
    ): Outcome<List<YDuplicateGroup>> {
        val walked = walkFiles(
            root = root,
            maxFiles = maxFiles,
        )
        if (walked is Outcome.Failure) {
            return walked
        }
        walked as Outcome.Success

        val groups =
            mutableListOf<YDuplicateGroup>()
        val bySize = walked.value
            .groupBy {
                it.sizeBytes ?: -1L
            }
            .filterKeys { it >= 0L }
            .filterValues {
                it.size > 1
            }

        for (
            (size, candidates) in bySize
        ) {
            val byHash =
                linkedMapOf<
                    String,
                    MutableList<YFileNode>,
                    >()
            for (candidate in candidates) {
                val hash =
                    sha256(candidate.ref)
                if (hash is Outcome.Failure) {
                    continue
                }
                hash as Outcome.Success
                byHash.getOrPut(
                    hash.value.hex,
                ) {
                    mutableListOf()
                } += candidate
            }

            for (
                (hash, nodes) in byHash
            ) {
                if (nodes.size > 1) {
                    groups += YDuplicateGroup(
                        sizeBytes = size,
                        hash = hash,
                        nodes = nodes,
                    )
                }
            }
        }

        return Outcome.Success(
            groups.sortedByDescending {
                it.sizeBytes *
                    (it.nodes.size - 1L)
            },
        )
    }

    suspend fun readText(
        ref: YFileRef,
        maxChars: Int = 200_000,
    ): Outcome<YTextDocument> =
        try {
            val bytes =
                readBounded(
                    ref,
                    maxChars * 4,
                )
            if (bytes is Outcome.Failure) {
                return bytes
            }
            bytes as Outcome.Success

            val decoded =
                bytes.value.first
                    .toString(Charsets.UTF_8)
            Outcome.Success(
                YTextDocument(
                    ref = ref,
                    text =
                        decoded.take(maxChars),
                    truncated =
                        bytes.value.second ||
                            decoded.length >
                                maxChars,
                ),
            )
        } catch (error: Throwable) {
            failure(
                "text_read_failed",
                error,
                TEXT_MESSAGE,
            )
        }

    suspend fun writeText(
        ref: YFileRef,
        text: String,
    ): Outcome<Unit> =
        engine.write(
            ref = ref,
            offset = 0L,
            data =
                text.toByteArray(
                    Charsets.UTF_8,
                ),
            truncate = true,
        )

    suspend fun readHex(
        ref: YFileRef,
        maxBytes: Int = 4_096,
    ): Outcome<YHexPreview> {
        val read = readBounded(
            ref,
            maxBytes,
        )
        if (read is Outcome.Failure) {
            return read
        }
        read as Outcome.Success

        val data = read.value.first
        val text = buildString {
            var offset = 0
            while (offset < data.size) {
                val end = minOf(
                    data.size,
                    offset + 16,
                )
                append(
                    offset.toString(16)
                        .padStart(8, '0'),
                )
                append("  ")
                for (index in offset until end) {
                    append(
                        "%02X".format(
                            data[index].toInt() and
                                0xff,
                        ),
                    )
                    append(' ')
                }
                repeat(16 - (end - offset)) {
                    append("   ")
                }
                append(' ')
                for (index in offset until end) {
                    val value =
                        data[index].toInt() and
                            0xff
                    append(
                        if (value in 32..126) {
                            value.toChar()
                        } else {
                            '.'
                        },
                    )
                }
                if (end < data.size) {
                    append('\n')
                }
                offset = end
            }
        }

        return Outcome.Success(
            YHexPreview(
                ref = ref,
                byteCount = data.size,
                text = text,
                truncated =
                    read.value.second,
            ),
        )
    }

    suspend fun previewRename(
        refs: List<YFileRef>,
        rule: YBatchRenameRule,
    ): Outcome<List<YBatchRenameItem>> =
        try {
            val regex = if (rule.regex) {
                Regex(rule.find)
            } else {
                null
            }
            val result =
                mutableListOf<YBatchRenameItem>()
            for (ref in refs.distinct()) {
                val stat = engine.stat(ref)
                if (stat is Outcome.Failure) {
                    return stat
                }
                stat as Outcome.Success

                var name = stat.value.name
                if (rule.find.isNotEmpty()) {
                    name = if (regex != null) {
                        regex.replace(
                            name,
                            rule.replace,
                        )
                    } else {
                        name.replace(
                            rule.find,
                            rule.replace,
                        )
                    }
                }
                name =
                    rule.prefix +
                        name +
                        rule.suffix
                require(name.isNotBlank()) {
                    "Rename result is empty"
                }
                result += YBatchRenameItem(
                    ref = ref,
                    originalName =
                        stat.value.name,
                    newName = name,
                )
            }
            Outcome.Success(result)
        } catch (error: Throwable) {
            failure(
                "rename_preview_failed",
                error,
                RENAME_MESSAGE,
            )
        }

    suspend fun applyRename(
        items: List<YBatchRenameItem>,
    ): YFileBatchResult {
        val staged =
            mutableListOf<
                Pair<
                    YBatchRenameItem,
                    YFileRef,
                    >
                >()
        val failures =
            mutableListOf<YFileFailure>()

        for (item in items) {
            val temporary =
                ".ysuite-" +
                    UUID.randomUUID()
                        .toString()
            when (
                val renamed =
                    engine.rename(
                        item.ref,
                        temporary,
                    )
            ) {
                is Outcome.Success ->
                    staged +=
                        item to
                            renamed.value.ref
                is Outcome.Failure ->
                    failures += YFileFailure(
                        ref = item.ref,
                        code =
                            renamed.error.code,
                        message =
                            renamed.message,
                    )
            }
        }

        var succeeded = 0
        for ((item, tempRef) in staged) {
            when (
                val renamed =
                    engine.rename(
                        tempRef,
                        item.newName,
                    )
            ) {
                is Outcome.Success ->
                    succeeded += 1
                is Outcome.Failure -> {
                    engine.rename(
                        tempRef,
                        item.originalName,
                    )
                    failures += YFileFailure(
                        ref = item.ref,
                        code =
                            renamed.error.code,
                        message =
                            renamed.message,
                    )
                }
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            skipped = 0,
            failures = failures,
        )
    }

    suspend fun createZip(
        refs: List<YFileRef>,
        destination: YFileRef,
        archiveName: String,
    ): Outcome<YFileNode> {
        if (refs.isEmpty()) {
            return Outcome.Failure(
                code = "zip_empty_selection",
                message = ZIP_SELECTION_MESSAGE,
            )
        }

        return try {
            cacheDirectory.mkdirs()
            val temporary = File(
                cacheDirectory,
                "zip-" +
                    UUID.randomUUID() +
                    ".zip",
            )
            ZipOutputStream(
                FileOutputStream(temporary),
            ).use { zip ->
                for (ref in refs.distinct()) {
                    val node =
                        when (
                            val stat =
                                engine.stat(ref)
                        ) {
                            is Outcome.Success ->
                                stat.value
                            is Outcome.Failure ->
                                return stat
                        }
                    addToZip(
                        zip = zip,
                        node = node,
                        entryPath =
                            node.name,
                    )
                }
            }

            val name =
                archiveName.trim()
                    .ifBlank {
                        "archive.zip"
                    }
                    .let {
                        if (
                            it.endsWith(
                                ".zip",
                                ignoreCase = true,
                            )
                        ) {
                            it
                        } else {
                            it + ".zip"
                        }
                    }

            val created =
                engine.createFile(
                    destination,
                    name,
                )
            if (created is Outcome.Failure) {
                temporary.delete()
                return created
            }
            created as Outcome.Success

            FileInputStreamCompat(
                temporary,
            ).use { input ->
                var offset = 0L
                val buffer =
                    ByteArray(CHUNK_BYTES)
                var first = true
                while (true) {
                    val count =
                        input.read(buffer)
                    if (count < 0) {
                        break
                    }
                    val written =
                        engine.write(
                            ref =
                                created.value.ref,
                            offset = offset,
                            data =
                                buffer.copyOf(
                                    count,
                                ),
                            truncate = first,
                        )
                    if (
                        written is
                            Outcome.Failure
                    ) {
                        engine.delete(
                            created.value.ref,
                        )
                        temporary.delete()
                        return written
                    }
                    first = false
                    offset += count
                }
            }
            temporary.delete()
            Outcome.Success(
                engine.stat(
                    created.value.ref,
                ).let {
                    when (it) {
                        is Outcome.Success ->
                            it.value
                        is Outcome.Failure ->
                            created.value
                    }
                },
            )
        } catch (error: Throwable) {
            failure(
                "zip_create_failed",
                error,
                ZIP_MESSAGE,
            )
        }
    }

    suspend fun extractZip(
        archive: YFileRef,
        destination: YFileRef,
    ): YFileBatchResult {
        val mounted = archives.mount(archive)
        if (mounted is Outcome.Failure) {
            return YFileBatchResult(
                succeeded = 0,
                skipped = 0,
                failures = listOf(
                    YFileFailure(
                        ref = archive,
                        code =
                            mounted.error.code,
                        message =
                            mounted.message,
                    ),
                ),
            )
        }
        mounted as Outcome.Success

        return try {
            val roots = engine.list(
                mounted.value,
                YFileQuery(
                    showHidden = true,
                    maxResults = Int.MAX_VALUE,
                ),
            )
            if (roots is Outcome.Failure) {
                return YFileBatchResult(
                    succeeded = 0,
                    skipped = 0,
                    failures = listOf(
                        YFileFailure(
                            ref = archive,
                            code =
                                roots.error.code,
                            message =
                                roots.message,
                        ),
                    ),
                )
            }
            roots as Outcome.Success
            engine.copyBatch(
                sources =
                    roots.value.map {
                        it.ref
                    },
                destinationDirectory =
                    destination,
                strategy =
                    YFileConflictStrategy.Rename,
            )
        } finally {
            archives.unmount(
                mounted.value,
            )
        }
    }

    suspend fun split(
        ref: YFileRef,
        partSizeBytes: Long,
    ): Outcome<Int> {
        if (partSizeBytes <= 0L) {
            return Outcome.Failure(
                code = "split_invalid_size",
                message = SPLIT_SIZE_MESSAGE,
            )
        }
        val parent = when (
            val result = engine.parent(ref)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        } ?: return Outcome.Failure(
            code = "split_missing_parent",
            message = PARENT_MESSAGE,
        )
        val node = when (
            val result = engine.stat(ref)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }

        var part = 1
        var sourceOffset = 0L
        while (true) {
            val partName =
                node.name +
                    ".part" +
                    part.toString()
                        .padStart(3, '0')
            val created =
                engine.createFile(
                    parent,
                    partName,
                )
            if (created is Outcome.Failure) {
                return created
            }
            created as Outcome.Success

            var partOffset = 0L
            var first = true
            while (
                partOffset < partSizeBytes
            ) {
                val want = minOf(
                    CHUNK_BYTES.toLong(),
                    partSizeBytes -
                        partOffset,
                ).toInt()
                val chunk = engine.read(
                    ref,
                    sourceOffset,
                    want,
                )
                if (chunk is Outcome.Failure) {
                    return chunk
                }
                chunk as Outcome.Success
                if (
                    chunk.value.data.isNotEmpty()
                ) {
                    val written =
                        engine.write(
                            ref =
                                created.value.ref,
                            offset = partOffset,
                            data =
                                chunk.value.data,
                            truncate = first,
                        )
                    if (
                        written is
                            Outcome.Failure
                    ) {
                        return written
                    }
                    first = false
                    partOffset +=
                        chunk.value.data.size
                    sourceOffset +=
                        chunk.value.data.size
                }
                if (chunk.value.eof) {
                    return Outcome.Success(part)
                }
            }
            part += 1
        }
    }

    suspend fun join(
        firstPart: YFileRef,
    ): Outcome<YFileNode> {
        val firstNode = when (
            val result = engine.stat(firstPart)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }
        if (
            !firstNode.name.endsWith(
                ".part001",
            )
        ) {
            return Outcome.Failure(
                code = "join_invalid_first_part",
                message = JOIN_PART_MESSAGE,
            )
        }
        val parent = when (
            val result =
                engine.parent(firstPart)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        } ?: return Outcome.Failure(
            code = "join_missing_parent",
            message = PARENT_MESSAGE,
        )

        val outputName =
            firstNode.name.removeSuffix(
                ".part001",
            )
        val output =
            engine.createFile(
                parent,
                outputName,
            )
        if (output is Outcome.Failure) {
            return output
        }
        output as Outcome.Success

        val siblings = engine.list(
            parent,
            YFileQuery(
                showHidden = true,
                maxResults = Int.MAX_VALUE,
            ),
        )
        if (siblings is Outcome.Failure) {
            return siblings
        }
        siblings as Outcome.Success

        val prefix =
            outputName + ".part"
        val parts = siblings.value
            .filter {
                it.name.startsWith(prefix) &&
                    it.name.removePrefix(prefix)
                        .toIntOrNull() != null
            }
            .sortedBy {
                it.name.removePrefix(prefix)
                    .toInt()
            }

        var outputOffset = 0L
        var firstWrite = true
        for (part in parts) {
            var partOffset = 0L
            while (true) {
                val chunk = engine.read(
                    part.ref,
                    partOffset,
                    CHUNK_BYTES,
                )
                if (chunk is Outcome.Failure) {
                    return chunk
                }
                chunk as Outcome.Success
                if (
                    chunk.value.data.isNotEmpty()
                ) {
                    val written =
                        engine.write(
                            ref =
                                output.value.ref,
                            offset =
                                outputOffset,
                            data =
                                chunk.value.data,
                            truncate =
                                firstWrite,
                        )
                    if (
                        written is
                            Outcome.Failure
                    ) {
                        return written
                    }
                    firstWrite = false
                    outputOffset +=
                        chunk.value.data.size
                    partOffset +=
                        chunk.value.data.size
                }
                if (chunk.value.eof) {
                    break
                }
            }
        }

        return engine.stat(output.value.ref)
    }

    suspend fun compare(
        left: YFileRef,
        right: YFileRef,
    ): Outcome<YCompareResult> {
        val leftNode = when (
            val result = engine.stat(left)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }
        val rightNode = when (
            val result = engine.stat(right)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }

        val leftHash = sha256(left)
        if (leftHash is Outcome.Failure) {
            return leftHash
        }
        leftHash as Outcome.Success
        val rightHash = sha256(right)
        if (rightHash is Outcome.Failure) {
            return rightHash
        }
        rightHash as Outcome.Success

        return Outcome.Success(
            YCompareResult(
                left = left,
                right = right,
                identical =
                    leftNode.sizeBytes ==
                        rightNode.sizeBytes &&
                        leftHash.value.hex ==
                            rightHash.value.hex,
                leftSize =
                    leftNode.sizeBytes,
                rightSize =
                    rightNode.sizeBytes,
                leftHash =
                    leftHash.value.hex,
                rightHash =
                    rightHash.value.hex,
            ),
        )
    }

    suspend fun cleanupScan(
        root: YFileRef,
        maxEntries: Int = 20_000,
    ): Outcome<YCleanupReport> {
        val walked = walkAll(
            root = root,
            maxEntries = maxEntries,
        )
        if (walked is Outcome.Failure) {
            return walked
        }
        walked as Outcome.Success

        val now =
            System.currentTimeMillis()
        val oldCutoff =
            now - OLD_FILE_AGE_MILLIS
        val downloadCutoff =
            now - OLD_DOWNLOAD_AGE_MILLIS

        val files = walked.value.first
            .filter {
                it.type == YFileType.File
            }
        val directories =
            walked.value.first.filter {
                it.type ==
                    YFileType.Directory
            }

        val emptyDirectories =
            mutableListOf<YFileNode>()
        for (directory in directories) {
            val listed = engine.list(
                directory.ref,
                YFileQuery(
                    showHidden = true,
                    maxResults = 1,
                ),
            )
            if (
                listed is Outcome.Success &&
                listed.value.isEmpty()
            ) {
                emptyDirectories += directory
            }
        }

        fun containsSegment(
            node: YFileNode,
            segment: String,
        ): Boolean =
            node.ref.path
                .lowercase()
                .contains(
                    segment.lowercase(),
                )

        return Outcome.Success(
            YCleanupReport(
                scanned =
                    walked.value.first.size,
                totalBytes =
                    files.sumOf {
                        it.sizeBytes ?: 0L
                    },
                largeFiles = files
                    .filter {
                        (it.sizeBytes ?: 0L) >=
                            LARGE_FILE_BYTES
                    }
                    .sortedByDescending {
                        it.sizeBytes ?: 0L
                    }
                    .take(100),
                oldFiles = files
                    .filter {
                        (it.modifiedAtMillis ?: now) <
                            oldCutoff
                    }
                    .take(100),
                hiddenFiles = walked.value.first
                    .filter { it.hidden }
                    .take(100),
                apkFiles = files
                    .filter {
                        it.name.endsWith(
                            ".apk",
                            ignoreCase = true,
                        )
                    }
                    .take(100),
                oldDownloads = files
                    .filter {
                        containsSegment(
                            it,
                            "/download",
                        ) &&
                            (
                                it.modifiedAtMillis
                                    ?: now
                                ) <
                            downloadCutoff
                    }
                    .take(100),
                screenshots = files
                    .filter {
                        containsSegment(
                            it,
                            "screenshot",
                        )
                    }
                    .take(100),
                recordings = files
                    .filter {
                        containsSegment(
                            it,
                            "record",
                        )
                    }
                    .take(100),
                emptyDirectories =
                    emptyDirectories.take(100),
                truncated =
                    walked.value.second,
            ),
        )
    }

    private suspend fun walkFiles(
        root: YFileRef,
        maxFiles: Int,
    ): Outcome<List<YFileNode>> {
        val walked = walkAll(
            root,
            maxFiles * 4,
        )
        return when (walked) {
            is Outcome.Success ->
                Outcome.Success(
                    walked.value.first
                        .filter {
                            it.type ==
                                YFileType.File
                        }
                        .take(maxFiles),
                )
            is Outcome.Failure ->
                walked
        }
    }

    private suspend fun walkAll(
        root: YFileRef,
        maxEntries: Int,
    ): Outcome<Pair<List<YFileNode>, Boolean>> {
        val queue =
            ArrayDeque<YFileRef>()
        val result =
            mutableListOf<YFileNode>()
        queue.add(root)

        while (
            queue.isNotEmpty() &&
            result.size < maxEntries
        ) {
            val current =
                queue.removeFirst()
            val listed = engine.list(
                current,
                YFileQuery(
                    showHidden = true,
                    maxResults =
                        maxEntries,
                ),
            )
            if (listed is Outcome.Failure) {
                return listed
            }
            listed as Outcome.Success

            for (node in listed.value) {
                if (
                    node.name ==
                        TRASH_DIRECTORY
                ) {
                    continue
                }
                result += node
                if (
                    node.type ==
                        YFileType.Directory
                ) {
                    queue.addLast(node.ref)
                }
                if (
                    result.size >=
                        maxEntries
                ) {
                    break
                }
            }
        }

        return Outcome.Success(
            result to queue.isNotEmpty(),
        )
    }

    private suspend fun readBounded(
        ref: YFileRef,
        maxBytes: Int,
    ): Outcome<Pair<ByteArray, Boolean>> {
        val output =
            java.io.ByteArrayOutputStream()
        var offset = 0L
        var eof = false

        while (
            output.size() < maxBytes &&
            !eof
        ) {
            val want = minOf(
                CHUNK_BYTES,
                maxBytes - output.size(),
            )
            val chunk = engine.read(
                ref,
                offset,
                want,
            )
            if (chunk is Outcome.Failure) {
                return chunk
            }
            chunk as Outcome.Success

            output.write(chunk.value.data)
            offset += chunk.value.data.size
            eof = chunk.value.eof
        }

        return Outcome.Success(
            output.toByteArray() to !eof,
        )
    }

    private suspend fun stream(
        ref: YFileRef,
        block: (ByteArray) -> Unit,
    ) {
        var offset = 0L
        while (true) {
            val chunk = engine.read(
                ref,
                offset,
                CHUNK_BYTES,
            )
            when (chunk) {
                is Outcome.Success -> {
                    if (
                        chunk.value.data
                            .isNotEmpty()
                    ) {
                        block(
                            chunk.value.data,
                        )
                        offset +=
                            chunk.value.data.size
                    }
                    if (chunk.value.eof) {
                        return
                    }
                }
                is Outcome.Failure ->
                    error(chunk.message)
            }
        }
    }

    private suspend fun addToZip(
        zip: ZipOutputStream,
        node: YFileNode,
        entryPath: String,
    ) {
        when (node.type) {
            YFileType.Directory -> {
                val normalized =
                    entryPath.trimEnd('/') +
                        "/"
                zip.putNextEntry(
                    ZipEntry(normalized),
                )
                zip.closeEntry()

                val children =
                    engine.list(
                        node.ref,
                        YFileQuery(
                            showHidden = true,
                            maxResults =
                                Int.MAX_VALUE,
                        ),
                    )
                if (
                    children is
                        Outcome.Failure
                ) {
                    error(children.message)
                }
                children as Outcome.Success
                for (child in children.value) {
                    if (
                        child.name !=
                            TRASH_DIRECTORY
                    ) {
                        addToZip(
                            zip,
                            child,
                            normalized +
                                child.name,
                        )
                    }
                }
            }

            YFileType.File,
            YFileType.Other -> {
                zip.putNextEntry(
                    ZipEntry(entryPath),
                )
                stream(node.ref) {
                    zip.write(it)
                }
                zip.closeEntry()
            }

            YFileType.SymbolicLink ->
                Unit
        }
    }

    private fun <T> failure(
        code: String,
        error: Throwable,
        fallback: String,
    ): Outcome<T> =
        Outcome.Failure(
            code = code,
            message =
                error.message ?: fallback,
            cause = error,
        )

    private class FileInputStreamCompat(
        file: File,
    ) : java.io.FileInputStream(file)

    companion object {
        private const val CHUNK_BYTES =
            64 * 1024
        private const val TRASH_DIRECTORY =
            ".YSuiteTrash"
        private const val HASH_MESSAGE =
            "Checksum failed"
        private const val ANALYSIS_MESSAGE =
            "Directory analysis failed"
        private const val TEXT_MESSAGE =
            "Text operation failed"
        private const val RENAME_MESSAGE =
            "Batch rename failed"
        private const val ZIP_MESSAGE =
            "ZIP operation failed"
        private const val ZIP_SELECTION_MESSAGE =
            "Select files before creating an archive"
        private const val SPLIT_SIZE_MESSAGE =
            "Part size must be greater than zero"
        private const val JOIN_PART_MESSAGE =
            "Select a .part001 file"
        private const val PARENT_MESSAGE =
            "Parent directory is unavailable"

        private const val LARGE_FILE_BYTES =
            500L * 1024L * 1024L
        private const val OLD_FILE_AGE_MILLIS =
            180L * 24L * 60L * 60L * 1000L
        private const val OLD_DOWNLOAD_AGE_MILLIS =
            30L * 24L * 60L * 60L * 1000L
    }
}
