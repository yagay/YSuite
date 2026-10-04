package com.yagay.ysuite.feature.yfiles.provider.local

import android.os.Environment
import android.system.Os
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
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFileProvider(
    rootPath: String =
        Environment.getExternalStorageDirectory()
            .absolutePath,
) : YFileProvider {
    private val rootFile = File(rootPath)
        .absoluteFile
        .normalize()

    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Local,
            capabilities = setOf(
                YFileCapability.Browse,
                YFileCapability.Search,
                YFileCapability.Read,
                YFileCapability.Write,
                YFileCapability.Create,
                YFileCapability.Rename,
                YFileCapability.Delete,
                YFileCapability.Copy,
                YFileCapability.Move,
                YFileCapability.PosixMode,
                YFileCapability.SymbolicLink,
            ),
        )

    override fun root(): YFileRef =
        ref(rootFile)

    override fun parent(
        ref: YFileRef,
    ): YFileRef? {
        val file = checkedFile(ref)
            .getOrNull()
            ?: return null
        if (file == rootFile) {
            return null
        }
        val parent = file.parentFile
            ?: return null
        return parent
            .takeIf(::isInsideRoot)
            ?.let(::ref)
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        ioOutcome("local_list_failed") {
            val root = requireDirectory(directory)
            val needle = query.text
                .trim()
                .lowercase()

            val entries =
                if (
                    query.recursive &&
                    needle.isNotEmpty()
                ) {
                    recursiveSearch(
                        root = root,
                        needle = needle,
                        showHidden = query.showHidden,
                        maxResults = query.maxResults,
                    )
                } else {
                    root.listFiles()
                        .orEmpty()
                        .asSequence()
                        .filter {
                            query.showHidden ||
                                !it.isHidden
                        }
                        .filter {
                            needle.isEmpty() ||
                                it.name.lowercase()
                                    .contains(needle)
                        }
                        .map(::node)
                        .take(query.maxResults)
                        .toList()
                }

            sort(entries, query)
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        ioOutcome("local_stat_failed") {
            node(requireExisting(ref))
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        ioOutcome("local_create_directory_failed") {
            val target = target(
                parent = requireDirectory(parent),
                name = name,
                replace = false,
            )
            require(target.mkdir()) {
                "Unable to create directory"
            }
            node(target)
        }

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        ioOutcome("local_create_file_failed") {
            val target = target(
                parent = requireDirectory(parent),
                name = name,
                replace = false,
            )
            require(target.createNewFile()) {
                "Unable to create file"
            }
            node(target)
        }

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        ioOutcome("local_rename_failed") {
            val source = requireExisting(ref)
            val parent = source.parentFile
                ?: error("Missing parent directory")
            val target = target(
                parent = parent,
                name = newName,
                replace = false,
            )
            Files.move(
                source.toPath(),
                target.toPath(),
            )
            node(target)
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        ioOutcome("local_delete_failed") {
            val source = requireExisting(ref)
            require(source != rootFile) {
                "Provider root cannot be deleted"
            }
            val deleted =
                if (
                    source.isDirectory &&
                    !Files.isSymbolicLink(
                        source.toPath(),
                    )
                ) {
                    source.deleteRecursively()
                } else {
                    source.delete()
                }
            require(deleted || !source.exists()) {
                "Unable to delete item"
            }
            Unit
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        ioOutcome("local_copy_failed") {
            val sourceFile = requireExisting(source)
            val destination =
                requireDirectory(
                    destinationDirectory,
                )
            preventSelfTransfer(
                sourceFile,
                destination,
            )
            val target = target(
                parent = destination,
                name = targetName,
                replace = replace,
            )
            copyTree(sourceFile, target)
            node(target)
        }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        ioOutcome("local_move_failed") {
            val sourceFile = requireExisting(source)
            val destination =
                requireDirectory(
                    destinationDirectory,
                )
            preventSelfTransfer(
                sourceFile,
                destination,
            )
            val target = target(
                parent = destination,
                name = targetName,
                replace = replace,
            )

            val options = if (replace) {
                arrayOf(
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } else {
                emptyArray()
            }

            runCatching {
                Files.move(
                    sourceFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    *options,
                )
            }.recoverCatching {
                Files.move(
                    sourceFile.toPath(),
                    target.toPath(),
                    *options,
                )
            }.recoverCatching {
                copyTree(sourceFile, target)
                val removed =
                    if (
                        sourceFile.isDirectory &&
                        !Files.isSymbolicLink(
                            sourceFile.toPath(),
                        )
                    ) {
                        sourceFile.deleteRecursively()
                    } else {
                        sourceFile.delete()
                    }
                require(
                    removed || !sourceFile.exists(),
                ) {
                    "Unable to remove original"
                }
            }.getOrThrow()

            node(target)
        }

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        ioOutcome("local_read_failed") {
            val source = requireExisting(ref)
            require(source.isFile) {
                "Item is not a regular file"
            }
            require(offset >= 0L && maxBytes > 0) {
                "Invalid read range"
            }

            RandomAccessFile(source, "r").use {
                if (offset >= it.length()) {
                    return@use YFileChunk(
                        data = ByteArray(0),
                        eof = true,
                    )
                }
                it.seek(offset)
                val buffer = ByteArray(maxBytes)
                val count = it.read(buffer)
                if (count <= 0) {
                    YFileChunk(
                        data = ByteArray(0),
                        eof = true,
                    )
                } else {
                    YFileChunk(
                        data = buffer.copyOf(count),
                        eof =
                            offset + count >=
                                it.length(),
                    )
                }
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        ioOutcome("local_write_failed") {
            val target = requireExisting(ref)
            require(target.isFile) {
                "Item is not a regular file"
            }
            require(offset >= 0L) {
                "Invalid write offset"
            }

            RandomAccessFile(target, "rw").use {
                if (truncate) {
                    it.setLength(0L)
                }
                it.seek(offset)
                it.write(data)
            }
            Unit
        }

    override suspend fun setPosixMode(
        ref: YFileRef,
        mode: Int,
    ): Outcome<Unit> =
        ioOutcome("local_chmod_failed") {
            val target = requireExisting(ref)
            Os.chmod(target.absolutePath, mode)
            Unit
        }

    override suspend fun createSymbolicLink(
        parent: YFileRef,
        name: String,
        target: String,
    ): Outcome<YFileNode> =
        ioOutcome("local_symlink_failed") {
            val parentFile = requireDirectory(parent)
            val link = target(
                parent = parentFile,
                name = name,
                replace = false,
            )
            Files.createSymbolicLink(
                link.toPath(),
                File(target).toPath(),
            )
            node(link)
        }

    override suspend fun readSymbolicLink(
        ref: YFileRef,
    ): Outcome<String> =
        ioOutcome("local_readlink_failed") {
            val file = requireExisting(ref)
            require(
                Files.isSymbolicLink(file.toPath()),
            ) {
                "Item is not a symbolic link"
            }
            Files.readSymbolicLink(file.toPath())
                .toString()
        }

    private fun recursiveSearch(
        root: File,
        needle: String,
        showHidden: Boolean,
        maxResults: Int,
    ): List<YFileNode> {
        val queue = ArrayDeque<File>()
        val result = mutableListOf<YFileNode>()
        queue.add(root)

        while (
            queue.isNotEmpty() &&
            result.size < maxResults
        ) {
            val directory = queue.removeFirst()
            for (child in directory.listFiles().orEmpty()) {
                if (!showHidden && child.isHidden) {
                    continue
                }
                if (
                    child.name.lowercase()
                        .contains(needle)
                ) {
                    result += node(child)
                    if (result.size >= maxResults) {
                        break
                    }
                }
                if (
                    child.isDirectory &&
                    !Files.isSymbolicLink(
                        child.toPath(),
                    )
                ) {
                    queue.addLast(child)
                }
            }
        }

        return result
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

    private fun checkedFile(
        ref: YFileRef,
    ): Result<File> =
        runCatching {
            require(
                ref.providerId == PROVIDER_ID,
            ) {
                "Provider mismatch"
            }
            val file = File(ref.path)
                .absoluteFile
                .normalize()
            require(isInsideRoot(file)) {
                "Path is outside provider root"
            }
            file
        }

    private fun requireExisting(
        ref: YFileRef,
    ): File {
        val file = checkedFile(ref).getOrThrow()
        require(
            file.exists() ||
                Files.isSymbolicLink(file.toPath()),
        ) {
            "Item does not exist"
        }
        return file
    }

    private fun requireDirectory(
        ref: YFileRef,
    ): File {
        val file = requireExisting(ref)
        require(
            file.isDirectory &&
                !Files.isSymbolicLink(
                    file.toPath(),
                ),
        ) {
            "Item is not a directory"
        }
        return file
    }

    private fun target(
        parent: File,
        name: String,
        replace: Boolean,
    ): File {
        val cleanName = sanitizeName(name)
        require(cleanName.isNotEmpty()) {
            "Name is empty"
        }
        val candidate = File(
            parent,
            cleanName,
        ).absoluteFile.normalize()
        require(isInsideRoot(candidate)) {
            "Target is outside provider root"
        }
        if (candidate.exists() && !replace) {
            error("Target already exists")
        }
        if (candidate.exists() && replace) {
            if (candidate.isDirectory) {
                candidate.deleteRecursively()
            } else {
                candidate.delete()
            }
        }
        return candidate
    }

    private fun preventSelfTransfer(
        source: File,
        destination: File,
    ) {
        if (
            !source.isDirectory ||
            Files.isSymbolicLink(
                source.toPath(),
            )
        ) {
            return
        }
        val sourcePath =
            source.toPath().normalize()
        val destinationPath =
            destination.toPath().normalize()
        require(
            destinationPath != sourcePath &&
                !destinationPath.startsWith(
                    sourcePath,
                ),
        ) {
            "Directory cannot be transferred into itself"
        }
    }

    private fun copyTree(
        source: File,
        target: File,
    ) {
        if (
            Files.isSymbolicLink(
                source.toPath(),
            )
        ) {
            target.parentFile?.mkdirs()
            Files.createSymbolicLink(
                target.toPath(),
                Files.readSymbolicLink(
                    source.toPath(),
                ),
            )
            return
        }

        if (source.isDirectory) {
            require(
                target.mkdirs() ||
                    target.isDirectory,
            ) {
                "Unable to create target directory"
            }
            for (
                child in
                    source.listFiles().orEmpty()
            ) {
                copyTree(
                    child,
                    File(target, child.name),
                )
            }
            return
        }

        target.parentFile?.mkdirs()
        Files.copy(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.COPY_ATTRIBUTES,
        )
    }

    private fun sanitizeName(
        raw: String,
    ): String =
        raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')

    private fun isInsideRoot(
        file: File,
    ): Boolean {
        val rootPath =
            rootFile.toPath().normalize()
        val filePath =
            file.toPath().normalize()
        return filePath == rootPath ||
            filePath.startsWith(rootPath)
    }

    private fun ref(
        file: File,
    ): YFileRef =
        YFileRef(
            providerId = PROVIDER_ID,
            path = file.absolutePath,
        )

    private fun node(
        file: File,
    ): YFileNode {
        val type = when {
            Files.isSymbolicLink(
                file.toPath(),
            ) ->
                YFileType.SymbolicLink
            file.isDirectory ->
                YFileType.Directory
            file.isFile ->
                YFileType.File
            else ->
                YFileType.Other
        }

        return YFileNode(
            ref = ref(file),
            name = file.name.ifBlank {
                file.absolutePath
            },
            type = type,
            sizeBytes = if (file.isFile) {
                file.length()
            } else {
                null
            },
            modifiedAtMillis =
                file.lastModified()
                    .takeIf { it > 0L },
            hidden = file.isHidden,
            readable = file.canRead(),
            writable = file.canWrite(),
            executable = file.canExecute(),
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
                    message = error.message
                        ?: LOCAL_OPERATION_MESSAGE,
                    cause = error,
                    retryable = true,
                )
            }
        }

    companion object {
        const val PROVIDER_ID = "local"
        private const val LOCAL_OPERATION_MESSAGE =
            "Local file operation failed"
    }
}
