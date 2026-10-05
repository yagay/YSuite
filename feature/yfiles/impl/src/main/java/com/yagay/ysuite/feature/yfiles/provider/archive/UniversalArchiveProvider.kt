package com.yagay.ysuite.feature.yfiles.provider.archive

import android.webkit.MimeTypeMap
import com.github.junrar.Archive
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory

class UniversalArchiveProvider(
    private val cacheDirectory: File,
    private val readOnlyMessage: String =
        DEFAULT_READ_ONLY_MESSAGE,
) : YFileProvider {
    private data class Mount(
        val id: String,
        val source: File,
        val displayName: String,
        val root: File,
        val temporarySource: Boolean,
    )

    private data class Parsed(
        val mountId: String,
        val relativePath: String,
    )

    private val mounts =
        linkedMapOf<String, Mount>()

    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Archive,
            capabilities = setOf(
                YFileCapability.Browse,
                YFileCapability.Search,
                YFileCapability.Read,
                YFileCapability.ArchiveMount,
            ),
        )

    override fun root(): YFileRef =
        YFileRef(
            PROVIDER_ID,
            VIRTUAL_ROOT,
        )

    override fun parent(ref: YFileRef): YFileRef? {
        requireProvider(ref)
        if (ref.path == VIRTUAL_ROOT) {
            return null
        }
        val parsed = parse(ref)
        if (parsed.relativePath.isBlank()) {
            return root()
        }
        val parent =
            parsed.relativePath
                .trimEnd('/')
                .substringBeforeLast(
                    '/',
                    missingDelimiterValue = "",
                )
        return ref(parsed.mountId, parent)
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        ioOutcome("archive_list_failed") {
            if (directory.path == VIRTUAL_ROOT) {
                return@ioOutcome sort(
                    mounts.values.map {
                        mountNode(it)
                    },
                    query,
                )
            }
            val parsed = parse(directory)
            val mount =
                mounts[parsed.mountId]
                    ?: error(
                        "Archive is not mounted",
                    )
            val base =
                safeResolve(
                    mount.root,
                    parsed.relativePath,
                )
            require(base.isDirectory) {
                "Archive directory does not exist"
            }
            val needle =
                query.text.trim()
            val files =
                if (
                    query.recursive &&
                    needle.isNotEmpty()
                ) {
                    base.walkTopDown()
                        .drop(1)
                        .filter {
                            query.showHidden ||
                                !it.name.startsWith(".")
                        }
                        .filter {
                            it.name.contains(
                                needle,
                                ignoreCase = true,
                            )
                        }
                        .take(query.maxResults)
                        .toList()
                } else {
                    base.listFiles()
                        .orEmpty()
                        .asSequence()
                        .filter {
                            query.showHidden ||
                                !it.name.startsWith(".")
                        }
                        .filter {
                            needle.isEmpty() ||
                                it.name.contains(
                                    needle,
                                    ignoreCase = true,
                                )
                        }
                        .take(query.maxResults)
                        .toList()
                }
            sort(
                files.map {
                    fileNode(
                        mount,
                        it,
                    )
                },
                query,
            )
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        ioOutcome("archive_stat_failed") {
            requireProvider(ref)
            if (ref.path == VIRTUAL_ROOT) {
                return@ioOutcome YFileNode(
                    ref = root(),
                    name = "Archives",
                    type = YFileType.Directory,
                )
            }
            val parsed = parse(ref)
            val mount =
                mounts[parsed.mountId]
                    ?: error(
                        "Archive is not mounted",
                    )
            if (parsed.relativePath.isBlank()) {
                mountNode(mount)
            } else {
                val file =
                    safeResolve(
                        mount.root,
                        parsed.relativePath,
                    )
                require(file.exists()) {
                    "Archive entry does not exist"
                }
                fileNode(mount, file)
            }
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> = readOnly()

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> = readOnly()

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> = readOnly()

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> = readOnly()

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> = readOnly()

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> = readOnly()

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        ioOutcome("archive_read_failed") {
            require(offset >= 0L)
            require(maxBytes > 0)
            val parsed = parse(ref)
            val mount =
                mounts[parsed.mountId]
                    ?: error(
                        "Archive is not mounted",
                    )
            val file =
                safeResolve(
                    mount.root,
                    parsed.relativePath,
                )
            require(file.isFile) {
                "Archive entry is not a file"
            }
            file.inputStream().use { input ->
                var remaining = offset
                while (remaining > 0L) {
                    val skipped =
                        input.skip(remaining)
                    if (skipped > 0L) {
                        remaining -= skipped
                    } else if (input.read() >= 0) {
                        remaining -= 1L
                    } else {
                        return@use YFileChunk(
                            ByteArray(0),
                            true,
                        )
                    }
                }
                val buffer =
                    ByteArray(maxBytes)
                val count = input.read(buffer)
                if (count <= 0) {
                    YFileChunk(
                        ByteArray(0),
                        true,
                    )
                } else {
                    YFileChunk(
                        buffer.copyOf(count),
                        offset + count >= file.length(),
                    )
                }
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> = readOnly()

    suspend fun mount(
        file: File,
        displayName: String = file.name,
        temporarySource: Boolean = false,
    ): YFileRef =
        withContext(Dispatchers.IO) {
            require(file.isFile) {
                "Archive file does not exist"
            }
            require(
                isSupported(file.name),
            ) {
                "Unsupported archive format"
            }
            require(
                cacheDirectory.mkdirs() ||
                    cacheDirectory.isDirectory,
            )
            val id = UUID.randomUUID().toString()
            val mountRoot =
                File(
                    cacheDirectory,
                    "mount-$id",
                )
            mountRoot.mkdirs()
            try {
                extract(
                    source = file,
                    target = mountRoot,
                )
                mounts[id] =
                    Mount(
                        id = id,
                        source = file,
                        displayName =
                            displayName,
                        root = mountRoot,
                        temporarySource =
                            temporarySource,
                    )
                ref(id, "")
            } catch (error: Throwable) {
                mountRoot.deleteRecursively()
                if (temporarySource) {
                    file.delete()
                }
                throw error
            }
        }

    fun unmount(root: YFileRef) {
        requireProvider(root)
        if (root.path == VIRTUAL_ROOT) {
            return
        }
        val parsed = parse(root)
        val mount =
            mounts.remove(parsed.mountId)
                ?: return
        mount.root.deleteRecursively()
        if (mount.temporarySource) {
            mount.source.delete()
        }
    }

    private fun extract(
        source: File,
        target: File,
    ) {
        val lower =
            source.name.lowercase()
        val budget = ExtractionBudget()
        when {
            lower.endsWith(".zip") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".war") ||
                lower.endsWith(".ear") ->
                extractZip(
                    source,
                    target,
                    budget,
                )
            lower.endsWith(".7z") ->
                extract7z(
                    source,
                    target,
                    budget,
                )
            lower.endsWith(".rar") ->
                extractRar(
                    source,
                    target,
                    budget,
                )
            isTarName(lower) ->
                extractTar(
                    source,
                    target,
                    budget,
                )
            else ->
                error(
                    "Unsupported archive format",
                )
        }
    }

    private fun extractZip(
        source: File,
        target: File,
        budget: ExtractionBudget,
    ) {
        ZipFile(source).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                budget.entry(
                    entry.name,
                    entry.size,
                )
                val output =
                    safeTarget(
                        target,
                        entry.name,
                    )
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    zip.getInputStream(entry).use {
                        input ->
                        FileOutputStream(output)
                            .use { out ->
                                copyBounded(
                                    input,
                                    out,
                                    budget,
                                )
                            }
                    }
                }
            }
        }
    }

    private fun extract7z(
        source: File,
        target: File,
        budget: ExtractionBudget,
    ) {
        SevenZFile.builder()
            .setFile(source)
            .get()
            .use { sevenZ ->
                while (true) {
                    val entry =
                        sevenZ.nextEntry
                            ?: break
                    budget.entry(
                        entry.name,
                        entry.size,
                    )
                    val output =
                        safeTarget(
                            target,
                            entry.name,
                        )
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output)
                            .use { out ->
                                val buffer =
                                    ByteArray(
                                        BUFFER_SIZE,
                                    )
                                var remaining =
                                    entry.size
                                while (
                                    remaining != 0L
                                ) {
                                    val want =
                                        if (
                                            remaining >
                                            0L
                                        ) {
                                            minOf(
                                                buffer.size
                                                    .toLong(),
                                                remaining,
                                            ).toInt()
                                        } else {
                                            buffer.size
                                        }
                                    val count =
                                        sevenZ.read(
                                            buffer,
                                            0,
                                            want,
                                        )
                                    if (count < 0) {
                                        break
                                    }
                                    budget.bytes(count)
                                    out.write(
                                        buffer,
                                        0,
                                        count,
                                    )
                                    if (
                                        remaining > 0L
                                    ) {
                                        remaining -=
                                            count
                                    }
                                }
                            }
                    }
                }
            }
    }

    private fun extractRar(
        source: File,
        target: File,
        budget: ExtractionBudget,
    ) {
        Archive(source).use { archive ->
            for (header in archive) {
                val name =
                    header.fileName
                        .trimEnd('/')
                if (name.isBlank()) continue
                budget.entry(
                    name,
                    header.fullUnpackSize,
                )
                val output =
                    safeTarget(
                        target,
                        name,
                    )
                if (header.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output)
                        .use { raw ->
                            val bounded =
                                object :
                                    java.io.OutputStream() {
                                    override fun write(
                                        b: Int,
                                    ) {
                                        budget.bytes(1)
                                        raw.write(b)
                                    }

                                    override fun write(
                                        b: ByteArray,
                                        off: Int,
                                        len: Int,
                                    ) {
                                        budget.bytes(len)
                                        raw.write(
                                            b,
                                            off,
                                            len,
                                        )
                                    }
                                }
                            archive.extractFile(
                                header,
                                bounded,
                            )
                        }
                }
            }
        }
    }

    private fun extractTar(
        source: File,
        target: File,
        budget: ExtractionBudget,
    ) {
        TarArchiveInputStream(
            openTarInput(source),
        ).use { tar ->
            while (true) {
                val entry =
                    tar.nextTarEntry
                        ?: break
                if (
                    entry.isSymbolicLink ||
                    entry.isLink ||
                    (
                        !entry.isDirectory &&
                            !entry.isFile
                        )
                ) {
                    error(
                        "Unsupported TAR entry: " +
                            entry.name,
                    )
                }
                budget.entry(
                    entry.name,
                    entry.size,
                )
                val output =
                    safeTarget(
                        target,
                        entry.name,
                    )
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output)
                        .use { out ->
                            copyBounded(
                                tar,
                                out,
                                budget,
                            )
                        }
                }
            }
        }
    }

    private fun openTarInput(
        source: File,
    ): java.io.InputStream {
        val input =
            BufferedInputStream(
                FileInputStream(source),
            )
        val name =
            source.name.lowercase()
        return when {
            name.endsWith(".tar.gz") ||
                name.endsWith(".tgz") ->
                CompressorStreamFactory()
                    .createCompressorInputStream(
                        CompressorStreamFactory.GZIP,
                        input,
                    )
            name.endsWith(".tar.bz2") ||
                name.endsWith(".tbz2") ->
                CompressorStreamFactory()
                    .createCompressorInputStream(
                        CompressorStreamFactory.BZIP2,
                        input,
                    )
            name.endsWith(".tar.xz") ||
                name.endsWith(".txz") ->
                CompressorStreamFactory()
                    .createCompressorInputStream(
                        CompressorStreamFactory.XZ,
                        input,
                    )
            name.endsWith(".tar.zst") ||
                name.endsWith(".tzst") ->
                CompressorStreamFactory()
                    .createCompressorInputStream(
                        CompressorStreamFactory.ZSTANDARD,
                        input,
                    )
            else -> input
        }
    }

    private fun copyBounded(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        budget: ExtractionBudget,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            budget.bytes(count)
            output.write(buffer, 0, count)
        }
    }

    private class ExtractionBudget {
        private var entries = 0
        private var bytes = 0L

        fun entry(
            name: String,
            declaredSize: Long,
        ) {
            entries += 1
            require(entries <= MAX_ENTRIES) {
                "Archive contains too many entries"
            }
            val depth =
                name.replace('\\', '/')
                    .split('/')
                    .count {
                        it.isNotBlank()
                    }
            require(depth <= MAX_DEPTH) {
                "Archive nesting is too deep"
            }
            if (declaredSize > 0L) {
                require(
                    declaredSize <=
                        MAX_SINGLE_ENTRY_BYTES,
                ) {
                    "Archive entry is too large"
                }
            }
        }

        fun bytes(count: Int) {
            bytes =
                Math.addExact(
                    bytes,
                    count.toLong(),
                )
            require(
                bytes <= MAX_TOTAL_BYTES,
            ) {
                "Archive expands beyond the safety limit"
            }
        }
    }

    private fun safeTarget(
        root: File,
        name: String,
    ): File {
        val clean =
            name.replace('\\', '/')
                .trimStart('/')
        require(
            clean.isNotBlank() &&
                !clean.split('/')
                    .any {
                        it == ".." ||
                            it == "."
                    },
        ) {
            "Unsafe archive entry path"
        }
        val target = File(root, clean)
        val canonicalRoot =
            root.canonicalFile
        val canonical =
            target.canonicalFile
        require(
            canonical.path ==
                canonicalRoot.path ||
                canonical.path.startsWith(
                    canonicalRoot.path +
                        File.separator,
                ),
        ) {
            "Archive entry escapes extraction root"
        }
        return canonical
    }

    private fun safeResolve(
        root: File,
        relative: String,
    ): File =
        safeTarget(
            root,
            relative.ifBlank { "_" },
        ).let {
            if (relative.isBlank()) {
                root.canonicalFile
            } else {
                it
            }
        }

    private fun mountNode(
        mount: Mount,
    ): YFileNode =
        YFileNode(
            ref = ref(mount.id, ""),
            name = mount.displayName,
            type = YFileType.Directory,
            mimeType = "inode/directory",
            readable = true,
            writable = false,
        )

    private fun fileNode(
        mount: Mount,
        file: File,
    ): YFileNode {
        val relative =
            file.relativeTo(mount.root)
                .invariantSeparatorsPath
        val directory = file.isDirectory
        val mime =
            if (directory) {
                "inode/directory"
            } else {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(
                        file.extension.lowercase(),
                    )
                    ?: "application/octet-stream"
            }
        return YFileNode(
            ref =
                ref(
                    mount.id,
                    relative,
                ),
            name = file.name,
            type =
                if (directory) {
                    YFileType.Directory
                } else {
                    YFileType.File
                },
            sizeBytes =
                if (directory) {
                    null
                } else {
                    file.length()
                },
            modifiedAtMillis =
                file.lastModified()
                    .takeIf { it > 0L },
            mimeType = mime,
            hidden = file.name.startsWith("."),
            readable = true,
            writable = false,
        )
    }

    private fun ref(
        mountId: String,
        relative: String,
    ): YFileRef =
        YFileRef(
            PROVIDER_ID,
            mountId +
                SEPARATOR +
                relative.replace('\\', '/'),
        )

    private fun parse(ref: YFileRef): Parsed {
        requireProvider(ref)
        require(ref.path != VIRTUAL_ROOT)
        val separator =
            ref.path.indexOf(SEPARATOR)
        require(separator > 0) {
            "Invalid archive reference"
        }
        return Parsed(
            mountId =
                ref.path.substring(
                    0,
                    separator,
                ),
            relativePath =
                ref.path.substring(
                    separator +
                        SEPARATOR.length,
                ),
        )
    }

    private fun sort(
        entries: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator =
            when (query.sort) {
                YFileSort.Name ->
                    compareBy<YFileNode> {
                        it.name.lowercase()
                    }
                YFileSort.Modified ->
                    compareBy {
                        it.modifiedAtMillis ?: 0L
                    }
                YFileSort.Size ->
                    compareBy {
                        it.sizeBytes ?: 0L
                    }
                YFileSort.Type ->
                    compareBy {
                        it.type.ordinal
                    }
            }.thenBy {
                it.name.lowercase()
            }
        val directed =
            if (query.descending) {
                comparator.reversed()
            } else {
                comparator
            }
        return entries.sortedWith(
            compareByDescending<YFileNode> {
                it.type == YFileType.Directory
            }.then(directed),
        )
    }

    private fun requireProvider(
        ref: YFileRef,
    ) {
        require(
            ref.providerId == PROVIDER_ID,
        )
    }

    private suspend inline fun <T> ioOutcome(
        code: String,
        crossinline block: () -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                Outcome.Success(block())
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = code,
                    message =
                        error.message
                            ?: "Archive operation failed",
                    cause = error,
                )
            }
        }

    private fun <T> readOnly(): Outcome<T> =
        Outcome.Failure(
            code = "archive_read_only",
            message = readOnlyMessage,
        )

    companion object {
        private const val DEFAULT_READ_ONLY_MESSAGE =
            "Archive is read-only"
        const val PROVIDER_ID = "archive"
        private const val VIRTUAL_ROOT =
            "archive://virtual-root"
        private const val SEPARATOR = "::"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ENTRIES = 50_000
        private const val MAX_DEPTH = 64
        private const val MAX_SINGLE_ENTRY_BYTES =
            2L * 1024L * 1024L * 1024L
        private const val MAX_TOTAL_BYTES =
            8L * 1024L * 1024L * 1024L

        fun isSupported(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".zip") ||
                lower.endsWith(".jar") ||
                lower.endsWith(".war") ||
                lower.endsWith(".ear") ||
                lower.endsWith(".7z") ||
                lower.endsWith(".rar") ||
                isTarName(lower)
        }

        private fun isTarName(
            lower: String,
        ): Boolean =
            lower.endsWith(".tar") ||
                lower.endsWith(".tar.gz") ||
                lower.endsWith(".tgz") ||
                lower.endsWith(".tar.bz2") ||
                lower.endsWith(".tbz2") ||
                lower.endsWith(".tar.xz") ||
                lower.endsWith(".txz") ||
                lower.endsWith(".tar.zst") ||
                lower.endsWith(".tzst")
    }
}
