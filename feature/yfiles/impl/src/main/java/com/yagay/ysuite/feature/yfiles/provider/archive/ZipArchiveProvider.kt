package com.yagay.ysuite.feature.yfiles.provider.archive

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
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZipArchiveProvider : YFileProvider {
    private data class Mount(
        val id: String,
        val file: File,
        val displayName: String,
        val temporary: Boolean,
    )

    private data class ParsedPath(
        val mountId: String,
        val entryPath: String,
    )

    private val mounts =
        ConcurrentHashMap<String, Mount>()

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
        ref(VIRTUAL_ROOT)

    override fun parent(
        ref: YFileRef,
    ): YFileRef? {
        requireProvider(ref)
        if (ref.path == VIRTUAL_ROOT) {
            return null
        }

        val parsed = parse(ref.path)
        if (parsed.entryPath.isEmpty()) {
            return root()
        }

        val trimmed =
            parsed.entryPath.trimEnd('/')
        val parentPath =
            trimmed.substringBeforeLast(
                '/',
                missingDelimiterValue = "",
            )
        return ref(
            encode(
                parsed.mountId,
                if (parentPath.isEmpty()) {
                    ""
                } else {
                    parentPath + "/"
                },
            ),
        )
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        ioOutcome("archive_list_failed") {
            if (directory.path == VIRTUAL_ROOT) {
                return@ioOutcome mounts.values
                    .sortedBy { it.displayName.lowercase() }
                    .map {
                        mountRootNode(it)
                    }
            }

            val parsed = parse(directory.path)
            val mount =
                mounts[parsed.mountId]
                    ?: error("Archive is not mounted")
            val needle =
                query.text.trim().lowercase()
            val nodes = if (
                query.recursive &&
                needle.isNotEmpty()
            ) {
                allNodes(mount)
                    .filter {
                        it.name.lowercase()
                            .contains(needle)
                    }
                    .take(query.maxResults)
            } else {
                directChildren(
                    mount,
                    parsed.entryPath,
                ).asSequence()
                    .filter {
                        query.showHidden ||
                            !it.hidden
                    }
                    .filter {
                        needle.isEmpty() ||
                            it.name.lowercase()
                                .contains(needle)
                    }
                    .take(query.maxResults)
                    .toList()
            }
            sort(nodes, query)
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        ioOutcome("archive_stat_failed") {
            requireProvider(ref)
            if (ref.path == VIRTUAL_ROOT) {
                return@ioOutcome YFileNode(
                    ref = root(),
                    name = PROVIDER_ID,
                    type = YFileType.Directory,
                )
            }
            val parsed = parse(ref.path)
            val mount =
                mounts[parsed.mountId]
                    ?: error("Archive is not mounted")
            if (parsed.entryPath.isEmpty()) {
                return@ioOutcome mountRootNode(
                    mount,
                )
            }

            findNode(
                mount,
                parsed.entryPath,
            ) ?: error("Archive entry does not exist")
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        readOnly()

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        readOnly()

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        readOnly()

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        readOnly()

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        readOnly()

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        readOnly()

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        ioOutcome("archive_read_failed") {
            requireProvider(ref)
            require(offset >= 0L && maxBytes > 0) {
                "Invalid read range"
            }
            val parsed = parse(ref.path)
            val mount =
                mounts[parsed.mountId]
                    ?: error("Archive is not mounted")

            ZipFile(mount.file).use { zip ->
                val entry =
                    zip.getEntry(parsed.entryPath)
                        ?: error(
                            "Archive entry does not exist",
                        )
                require(!entry.isDirectory) {
                    "Archive entry is a directory"
                }

                zip.getInputStream(entry).use { input ->
                    var remainingSkip = offset
                    while (remainingSkip > 0L) {
                        val skipped =
                            input.skip(remainingSkip)
                        if (skipped <= 0L) {
                            if (input.read() < 0) {
                                return@use YFileChunk(
                                    data = ByteArray(0),
                                    eof = true,
                                )
                            }
                            remainingSkip -= 1L
                        } else {
                            remainingSkip -= skipped
                        }
                    }

                    val buffer =
                        ByteArray(maxBytes)
                    val count = input.read(buffer)
                    if (count <= 0) {
                        YFileChunk(
                            data = ByteArray(0),
                            eof = true,
                        )
                    } else {
                        YFileChunk(
                            data =
                                buffer.copyOf(count),
                            eof =
                                entry.size >= 0L &&
                                    offset + count >=
                                        entry.size,
                        )
                    }
                }
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        readOnly()

    fun mount(
        file: File,
        displayName: String =
            file.name,
        temporary: Boolean = false,
    ): YFileRef {
        require(file.isFile) {
            "Archive file does not exist"
        }
        ZipFile(file).use {
            it.entries().hasMoreElements()
        }

        val id = UUID.randomUUID().toString()
        mounts[id] = Mount(
            id = id,
            file = file,
            displayName = displayName,
            temporary = temporary,
        )
        return ref(encode(id, ""))
    }

    fun unmount(
        root: YFileRef,
    ) {
        requireProvider(root)
        if (root.path == VIRTUAL_ROOT) {
            return
        }
        val parsed = parse(root.path)
        val mount = mounts.remove(
            parsed.mountId,
        ) ?: return
        if (mount.temporary) {
            mount.file.delete()
        }
    }

    private fun directChildren(
        mount: Mount,
        directoryPath: String,
    ): List<YFileNode> {
        val prefix =
            directoryPath
                .trimStart('/')
                .let {
                    if (
                        it.isEmpty() ||
                        it.endsWith("/")
                    ) {
                        it
                    } else {
                        it + "/"
                    }
                }
        val children =
            linkedMapOf<String, YFileNode>()

        ZipFile(mount.file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val raw =
                    normalizeEntryName(entry.name)
                if (
                    raw.isEmpty() ||
                    !raw.startsWith(prefix) ||
                    raw == prefix
                ) {
                    continue
                }

                val remainder =
                    raw.removePrefix(prefix)
                val slash =
                    remainder.indexOf('/')
                val childName = if (slash >= 0) {
                    remainder.substring(0, slash)
                } else {
                    remainder.trimEnd('/')
                }
                if (childName.isEmpty()) {
                    continue
                }

                val childPath =
                    prefix +
                        childName +
                        if (
                            slash >= 0 ||
                            entry.isDirectory
                        ) {
                            "/"
                        } else {
                            ""
                        }

                val existing =
                    children[childName]
                if (
                    existing == null ||
                    existing.type !=
                        YFileType.Directory
                ) {
                    children[childName] =
                        nodeForEntryPath(
                            mount = mount,
                            entryPath = childPath,
                            entry = if (
                                slash < 0
                            ) {
                                entry
                            } else {
                                null
                            },
                        )
                }
            }
        }

        return children.values.toList()
    }

    private fun allNodes(
        mount: Mount,
    ): List<YFileNode> {
        val paths =
            linkedMapOf<String, YFileNode>()
        ZipFile(mount.file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val path =
                    normalizeEntryName(entry.name)
                if (path.isEmpty()) {
                    continue
                }

                val segments =
                    path.trimEnd('/')
                        .split('/')
                var current = ""
                for (
                    index in segments.indices
                ) {
                    current += segments[index]
                    val isLast =
                        index ==
                            segments.lastIndex
                    val directory =
                        !isLast ||
                            entry.isDirectory
                    if (directory) {
                        current += "/"
                    }
                    paths.putIfAbsent(
                        current,
                        nodeForEntryPath(
                            mount,
                            current,
                            if (isLast) {
                                entry
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }
        return paths.values.toList()
    }

    private fun findNode(
        mount: Mount,
        entryPath: String,
    ): YFileNode? {
        val normalized =
            normalizeEntryName(entryPath)
        ZipFile(mount.file).use { zip ->
            val direct =
                zip.getEntry(normalized)
                    ?: zip.getEntry(
                        normalized.trimEnd('/') + "/",
                    )
            if (direct != null) {
                return nodeForEntryPath(
                    mount,
                    if (direct.isDirectory) {
                        normalized.trimEnd('/') + "/"
                    } else {
                        normalized.trimEnd('/')
                    },
                    direct,
                )
            }

            val prefix =
                normalized.trimEnd('/') + "/"
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                if (
                    normalizeEntryName(
                        entries.nextElement().name,
                    ).startsWith(prefix)
                ) {
                    return nodeForEntryPath(
                        mount,
                        prefix,
                        null,
                    )
                }
            }
        }
        return null
    }

    private fun mountRootNode(
        mount: Mount,
    ): YFileNode =
        YFileNode(
            ref = ref(
                encode(mount.id, ""),
            ),
            name = mount.displayName,
            type = YFileType.Directory,
            readable = true,
            writable = false,
        )

    private fun nodeForEntryPath(
        mount: Mount,
        entryPath: String,
        entry: ZipEntry?,
    ): YFileNode {
        val directory =
            entryPath.endsWith("/") ||
                entry?.isDirectory == true
        val trimmed =
            entryPath.trimEnd('/')
        val name =
            trimmed.substringAfterLast('/')
                .ifBlank {
                    mount.displayName
                }

        return YFileNode(
            ref = ref(
                encode(
                    mount.id,
                    if (directory) {
                        trimmed + "/"
                    } else {
                        trimmed
                    },
                ),
            ),
            name = name,
            type = if (directory) {
                YFileType.Directory
            } else {
                YFileType.File
            },
            sizeBytes =
                entry?.size
                    ?.takeIf {
                        !directory &&
                            it >= 0L
                    },
            modifiedAtMillis =
                entry?.time
                    ?.takeIf { it > 0L },
            hidden = name.startsWith("."),
            readable = true,
            writable = false,
        )
    }

    private fun sort(
        entries: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator = when (query.sort) {
            YFileSort.Name ->
                compareBy<YFileNode> {
                    it.name.lowercase()
                }
            YFileSort.Modified ->
                compareBy<YFileNode> {
                    it.modifiedAtMillis ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Size ->
                compareBy<YFileNode> {
                    it.sizeBytes ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Type ->
                compareBy<YFileNode> {
                    it.type.ordinal
                }.thenBy {
                    it.name.lowercase()
                }
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

    private fun normalizeEntryName(
        value: String,
    ): String {
        val replaced =
            value.replace('\\', '/')
                .trimStart('/')
        val directory =
            replaced.endsWith("/")
        val segments =
            replaced.split('/')
                .filter(String::isNotEmpty)
        require(
            segments.none { it == ".." },
        ) {
            "Unsafe archive entry"
        }
        val normalized =
            segments
                .filterNot { it == "." }
                .joinToString("/")
        return if (
            directory &&
            normalized.isNotEmpty()
        ) {
            normalized + "/"
        } else {
            normalized
        }
    }

    private fun encode(
        mountId: String,
        entryPath: String,
    ): String =
        mountId +
            SEPARATOR +
            normalizeEntryName(entryPath)

    private fun parse(
        path: String,
    ): ParsedPath {
        val index = path.indexOf(SEPARATOR)
        require(index > 0) {
            "Invalid archive reference"
        }
        return ParsedPath(
            mountId = path.substring(0, index),
            entryPath =
                path.substring(
                    index + SEPARATOR.length,
                ),
        )
    }

    private fun ref(
        path: String,
    ): YFileRef =
        YFileRef(
            providerId = PROVIDER_ID,
            path = path,
        )

    private fun requireProvider(
        ref: YFileRef,
    ) {
        require(
            ref.providerId == PROVIDER_ID,
        ) {
            "Provider mismatch"
        }
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
                    message = error.message
                        ?: ARCHIVE_OPERATION_MESSAGE,
                    cause = error,
                )
            }
        }

    private fun <T> readOnly(): Outcome<T> =
        Outcome.Failure(
            code = "archive_read_only",
            message = ARCHIVE_READ_ONLY_MESSAGE,
        )

    companion object {
        const val PROVIDER_ID = "archive"
        private const val VIRTUAL_ROOT =
            "archive://virtual-root"
        private const val SEPARATOR = "::"
        private const val ARCHIVE_OPERATION_MESSAGE =
            "Archive operation failed"
        private const val ARCHIVE_READ_ONLY_MESSAGE =
            "Archive provider is read-only"
    }
}
