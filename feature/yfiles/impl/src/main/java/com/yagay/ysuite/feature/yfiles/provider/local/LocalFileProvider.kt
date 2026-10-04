package com.yagay.ysuite.feature.yfiles.provider.local

import android.os.Environment
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileType
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFileProvider(
    rootPath: String =
        Environment.getExternalStorageDirectory().absolutePath,
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
        if (!isInsideRoot(parent)) {
            return null
        }
        return ref(parent)
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        ioOutcome("local_list_failed") {
            val root = requireDirectory(directory)
            val text = query.text
                .trim()
                .lowercase()

            val nodes =
                if (
                    query.recursive &&
                    text.isNotEmpty()
                ) {
                    recursiveSearch(
                        root = root,
                        text = text,
                        showHidden = query.showHidden,
                        maxResults = query.maxResults,
                    )
                } else {
                    root.listFiles()
                        .orEmpty()
                        .asSequence()
                        .filter {
                            query.showHidden || !it.isHidden
                        }
                        .filter {
                            text.isEmpty() ||
                                it.name.lowercase()
                                    .contains(text)
                        }
                        .map(::node)
                        .toList()
                }

            nodes.sortedWith(
                compareByDescending<YFileNode> {
                    it.type == YFileType.Directory
                }.thenBy {
                    it.name.lowercase()
                },
            )
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
            val target = target(parent, newName)
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
                    !Files.isSymbolicLink(source.toPath())
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
    ): Outcome<YFileNode> =
        ioOutcome("local_copy_failed") {
            val sourceFile = requireExisting(source)
            val destination =
                requireDirectory(destinationDirectory)
            preventSelfTransfer(
                sourceFile,
                destination,
            )
            val target = target(
                destination,
                sourceFile.name,
            )
            copyTree(sourceFile, target)
            node(target)
        }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
    ): Outcome<YFileNode> =
        ioOutcome("local_move_failed") {
            val sourceFile = requireExisting(source)
            val destination =
                requireDirectory(destinationDirectory)
            preventSelfTransfer(
                sourceFile,
                destination,
            )
            val target = target(
                destination,
                sourceFile.name,
            )

            runCatching {
                Files.move(
                    sourceFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.recoverCatching {
                Files.move(
                    sourceFile.toPath(),
                    target.toPath(),
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

    private fun recursiveSearch(
        root: File,
        text: String,
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

                if (child.name.lowercase().contains(text)) {
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

    private fun checkedFile(
        ref: YFileRef,
    ): Result<File> =
        runCatching {
            require(ref.providerId == PROVIDER_ID) {
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
        require(file.exists()) {
            "Item does not exist"
        }
        return file
    }

    private fun requireDirectory(
        ref: YFileRef,
    ): File {
        val file = requireExisting(ref)
        require(file.isDirectory) {
            "Not a directory"
        }
        return file
    }

    private fun target(
        parent: File,
        name: String,
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
        require(!candidate.exists()) {
            "Target already exists"
        }
        return candidate
    }

    private fun preventSelfTransfer(
        source: File,
        destination: File,
    ) {
        if (!source.isDirectory) {
            return
        }
        val sourcePath = source.toPath().normalize()
        val destinationPath =
            destination.toPath().normalize()
        require(
            destinationPath != sourcePath &&
                !destinationPath.startsWith(sourcePath),
        ) {
            "Directory cannot be transferred into itself"
        }
    }

    private fun copyTree(
        source: File,
        target: File,
    ) {
        if (Files.isSymbolicLink(source.toPath())) {
            target.parentFile?.mkdirs()
            Files.createSymbolicLink(
                target.toPath(),
                Files.readSymbolicLink(source.toPath()),
            )
            return
        }

        if (source.isDirectory) {
            require(
                target.mkdirs() || target.isDirectory,
            ) {
                "Unable to create target directory"
            }
            for (child in source.listFiles().orEmpty()) {
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

    private fun sanitizeName(raw: String): String =
        raw.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')

    private fun isInsideRoot(file: File): Boolean {
        val rootPath = rootFile.toPath().normalize()
        val filePath = file.toPath().normalize()
        return filePath == rootPath ||
            filePath.startsWith(rootPath)
    }

    private fun ref(file: File): YFileRef =
        YFileRef(
            providerId = PROVIDER_ID,
            path = file.absolutePath,
        )

    private fun node(file: File): YFileNode {
        val type = when {
            Files.isSymbolicLink(file.toPath()) ->
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
            modifiedAtMillis = file.lastModified()
                .takeIf { it > 0L },
            hidden = file.isHidden,
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
                        ?: "Local file operation failed",
                    retryable = true,
                    cause = error,
                )
            }
        }

    companion object {
        const val PROVIDER_ID = "local"
    }
}
