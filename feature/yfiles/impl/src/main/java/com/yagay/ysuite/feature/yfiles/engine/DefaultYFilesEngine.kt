package com.yagay.ysuite.feature.yfiles.engine

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileBatchResult
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileConflictStrategy
import com.yagay.ysuite.feature.yfiles.api.YFileFailure
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileOperationProgress
import com.yagay.ysuite.feature.yfiles.api.YFileProgressListener
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine

class DefaultYFilesEngine(
    private val registry: YFileProviderRegistry,
) : YFilesEngine {
    override val providers:
        List<YFileProviderDescriptor>
        get() = registry.descriptors

    override fun root(
        providerId: String,
    ): Outcome<YFileRef> =
        withProvider(providerId) {
            Outcome.Success(it.root())
        }

    override fun parent(
        ref: YFileRef,
    ): Outcome<YFileRef?> =
        withProvider(ref) {
            Outcome.Success(it.parent(ref))
        }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        withProviderSuspend(directory) {
            it.list(directory, query)
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        withProviderSuspend(ref) {
            it.stat(ref)
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(parent) {
            it.createDirectory(parent, name)
        }

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(parent) {
            it.createFile(parent, name)
        }

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(ref) {
            it.rename(ref, newName)
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        withProviderSuspend(ref) {
            it.delete(ref)
        }

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        withProviderSuspend(ref) {
            it.read(ref, offset, maxBytes)
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        withProviderSuspend(ref) {
            it.write(
                ref = ref,
                offset = offset,
                data = data,
                truncate = truncate,
            )
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy,
        onProgress: YFileProgressListener?,
    ): Outcome<YFileNode> {
        val sourceProvider = providerOrFailure(source)
        if (sourceProvider is Outcome.Failure) {
            return sourceProvider
        }
        val destinationProvider =
            providerOrFailure(destinationDirectory)
        if (destinationProvider is Outcome.Failure) {
            return destinationProvider
        }

        sourceProvider as Outcome.Success
        destinationProvider as Outcome.Success

        val sourceNode = when (
            val result = sourceProvider.value.stat(source)
        ) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }

        val target = when (
            val result = resolveTarget(
                destinationProvider.value,
                destinationDirectory,
                sourceNode.name,
                strategy,
            )
        ) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }

        if (target.existing?.ref == source) {
            return Outcome.Success(sourceNode)
        }

        if (strategy == YFileConflictStrategy.Replace &&
            target.existing != null
        ) {
            return replaceByStaging(
                sourceProvider = sourceProvider.value,
                destinationProvider = destinationProvider.value,
                sourceNode = sourceNode,
                destinationDirectory = destinationDirectory,
                targetName = target.name,
                existing = target.existing,
                move = false,
                onProgress = onProgress,
            )
        }

        if (
            source.providerId ==
                destinationDirectory.providerId
        ) {
            val direct = sourceProvider.value.copy(
                source = source,
                destinationDirectory = destinationDirectory,
                targetName = target.name,
                replace = false,
            )
            if (direct is Outcome.Success) {
                return direct
            }
        }

        return streamCopy(
            sourceProvider = sourceProvider.value,
            destinationProvider = destinationProvider.value,
            sourceNode = sourceNode,
            destinationDirectory = destinationDirectory,
            targetName = target.name,
            onProgress = onProgress,
        )
    }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy,
        onProgress: YFileProgressListener?,
    ): Outcome<YFileNode> {
        val sourceProvider = providerOrFailure(source)
        if (sourceProvider is Outcome.Failure) {
            return sourceProvider
        }
        val destinationProvider =
            providerOrFailure(destinationDirectory)
        if (destinationProvider is Outcome.Failure) {
            return destinationProvider
        }

        sourceProvider as Outcome.Success
        destinationProvider as Outcome.Success

        val sourceNode = when (
            val result = sourceProvider.value.stat(source)
        ) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }

        val target = when (
            val result = resolveTarget(
                destinationProvider.value,
                destinationDirectory,
                sourceNode.name,
                strategy,
            )
        ) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }

        if (target.existing?.ref == source) {
            return Outcome.Success(sourceNode)
        }

        if (strategy == YFileConflictStrategy.Replace &&
            target.existing != null
        ) {
            return replaceByStaging(
                sourceProvider = sourceProvider.value,
                destinationProvider = destinationProvider.value,
                sourceNode = sourceNode,
                destinationDirectory = destinationDirectory,
                targetName = target.name,
                existing = target.existing,
                move = true,
                onProgress = onProgress,
            )
        }

        if (
            source.providerId ==
                destinationDirectory.providerId
        ) {
            val direct = sourceProvider.value.move(
                source = source,
                destinationDirectory = destinationDirectory,
                targetName = target.name,
                replace = false,
            )
            if (direct is Outcome.Success) {
                return direct
            }
        }

        val copied = streamCopy(
            sourceProvider = sourceProvider.value,
            destinationProvider = destinationProvider.value,
            sourceNode = sourceNode,
            destinationDirectory = destinationDirectory,
            targetName = target.name,
            onProgress = onProgress,
        )
        if (copied is Outcome.Failure) {
            return copied
        }

        return when (
            val deleted = sourceProvider.value.delete(source)
        ) {
            is Outcome.Success -> copied
            is Outcome.Failure -> {
                copied as Outcome.Success
                destinationProvider.value.delete(copied.value.ref)
                deleted
            }
        }
    }

    override suspend fun copyBatch(
        sources: List<YFileRef>,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy,
        onProgress: YFileProgressListener?,
    ): YFileBatchResult =
        runBatch(
            sources = sources,
            operation = { source ->
                copy(
                    source = source,
                    destinationDirectory =
                        destinationDirectory,
                    strategy = strategy,
                    onProgress = onProgress,
                )
            },
        )

    override suspend fun moveBatch(
        sources: List<YFileRef>,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy,
        onProgress: YFileProgressListener?,
    ): YFileBatchResult =
        runBatch(
            sources = sources,
            operation = { source ->
                move(
                    source = source,
                    destinationDirectory =
                        destinationDirectory,
                    strategy = strategy,
                    onProgress = onProgress,
                )
            },
        )

    override suspend fun deleteBatch(
        refs: List<YFileRef>,
    ): YFileBatchResult =
        runBatch(
            sources = refs,
            operation = { source ->
                when (val result = delete(source)) {
                    is Outcome.Success ->
                        Outcome.Success(source)
                    is Outcome.Failure ->
                        result
                }
            },
        )

    override suspend fun setPosixMode(
        ref: YFileRef,
        mode: Int,
    ): Outcome<Unit> =
        withProviderSuspend(ref) {
            it.setPosixMode(ref, mode)
        }

    override suspend fun createSymbolicLink(
        parent: YFileRef,
        name: String,
        target: String,
    ): Outcome<YFileNode> =
        withProviderSuspend(parent) {
            it.createSymbolicLink(
                parent = parent,
                name = name,
                target = target,
            )
        }

    override suspend fun readSymbolicLink(
        ref: YFileRef,
    ): Outcome<String> =
        withProviderSuspend(ref) {
            it.readSymbolicLink(ref)
        }

    private suspend fun streamCopy(
        sourceProvider: YFileProvider,
        destinationProvider: YFileProvider,
        sourceNode: YFileNode,
        destinationDirectory: YFileRef,
        targetName: String,
        onProgress: YFileProgressListener?,
    ): Outcome<YFileNode> {
        return when (sourceNode.type) {
            YFileType.Directory -> {
                val created = destinationProvider
                    .createDirectory(
                        destinationDirectory,
                        targetName,
                    )
                if (created is Outcome.Failure) {
                    return created
                }
                created as Outcome.Success

                val children = sourceProvider.list(
                    sourceNode.ref,
                    YFileQuery(
                        showHidden = true,
                        maxResults = Int.MAX_VALUE,
                    ),
                )
                if (children is Outcome.Failure) {
                    destinationProvider.delete(
                        created.value.ref,
                    )
                    return children
                }
                children as Outcome.Success

                for (child in children.value) {
                    val copied = streamCopy(
                        sourceProvider =
                            sourceProvider,
                        destinationProvider =
                            destinationProvider,
                        sourceNode = child,
                        destinationDirectory =
                            created.value.ref,
                        targetName = child.name,
                        onProgress = onProgress,
                    )
                    if (copied is Outcome.Failure) {
                        destinationProvider.delete(
                            created.value.ref,
                        )
                        return copied
                    }
                }
                created
            }

            YFileType.File,
            YFileType.Other -> {
                val created = destinationProvider
                    .createFile(
                        destinationDirectory,
                        targetName,
                    )
                if (created is Outcome.Failure) {
                    return created
                }
                created as Outcome.Success

                var offset = 0L
                var firstWrite = true
                while (true) {
                    val chunk = sourceProvider.read(
                        sourceNode.ref,
                        offset,
                        TRANSFER_CHUNK_BYTES,
                    )
                    if (chunk is Outcome.Failure) {
                        destinationProvider.delete(
                            created.value.ref,
                        )
                        return chunk
                    }
                    chunk as Outcome.Success

                    if (chunk.value.data.isNotEmpty()) {
                        val write =
                            destinationProvider.write(
                                ref = created.value.ref,
                                offset = offset,
                                data = chunk.value.data,
                                truncate = firstWrite,
                            )
                        if (write is Outcome.Failure) {
                            destinationProvider.delete(
                                created.value.ref,
                            )
                            return write
                        }
                        firstWrite = false
                        offset += chunk.value.data.size
                        onProgress?.invoke(
                            YFileOperationProgress(
                                currentName =
                                    sourceNode.name,
                                completedBytes = offset,
                                totalBytes =
                                    sourceNode.sizeBytes,
                                completedItems = 0,
                                totalItems = 1,
                            ),
                        )
                    }

                    if (chunk.value.eof) {
                        break
                    }
                }
                created
            }

            YFileType.SymbolicLink -> {
                val linkTarget =
                    sourceProvider.readSymbolicLink(
                        sourceNode.ref,
                    )
                if (linkTarget is Outcome.Success) {
                    destinationProvider
                        .createSymbolicLink(
                            parent =
                                destinationDirectory,
                            name = targetName,
                            target =
                                linkTarget.value,
                        )
                } else {
                    Outcome.Failure(
                        code = "symlink_transfer_unsupported",
                        message =
                            SYMLINK_TRANSFER_MESSAGE,
                    )
                }
            }
        }
    }

    private data class ResolvedTarget(
        val name: String,
        val existing: YFileNode?,
    )

    private suspend fun resolveTarget(
        provider: YFileProvider,
        destination: YFileRef,
        requestedName: String,
        strategy: YFileConflictStrategy,
    ): Outcome<ResolvedTarget> {
        val listing = provider.list(
            destination,
            YFileQuery(
                showHidden = true,
                maxResults = Int.MAX_VALUE,
            ),
        )
        if (listing is Outcome.Failure) {
            return listing
        }
        listing as Outcome.Success

        val names = listing.value.associateBy { it.name }
        val existing = names[requestedName]
        if (existing == null) {
            return Outcome.Success(
                ResolvedTarget(requestedName, null),
            )
        }

        return when (strategy) {
            YFileConflictStrategy.Skip ->
                Outcome.Failure(
                    code = "conflict_skipped",
                    message = CONFLICT_SKIPPED_MESSAGE,
                )
            YFileConflictStrategy.Replace ->
                Outcome.Success(
                    ResolvedTarget(requestedName, existing),
                )
            YFileConflictStrategy.Rename -> {
                val dot = requestedName.lastIndexOf('.')
                val hasExtension =
                    dot > 0 &&
                        dot < requestedName.lastIndex
                val base =
                    if (hasExtension) {
                        requestedName.substring(0, dot)
                    } else {
                        requestedName
                    }
                val extension =
                    if (hasExtension) {
                        requestedName.substring(dot)
                    } else {
                        ""
                    }

                var index = 1
                var candidate: String
                do {
                    candidate =
                        base + " (" + index + ")" + extension
                    index += 1
                } while (candidate in names)

                Outcome.Success(
                    ResolvedTarget(candidate, null),
                )
            }
        }
    }

    private suspend fun replaceByStaging(
        sourceProvider: YFileProvider,
        destinationProvider: YFileProvider,
        sourceNode: YFileNode,
        destinationDirectory: YFileRef,
        targetName: String,
        existing: YFileNode,
        move: Boolean,
        onProgress: YFileProgressListener?,
    ): Outcome<YFileNode> {
        val token = System.nanoTime().toString(16)
        val tempName = ".ysuite-new-" + token + "-" + targetName
        val backupName = ".ysuite-old-" + token + "-" + targetName

        val staged =
            if (
                sourceNode.ref.providerId ==
                    destinationDirectory.providerId
            ) {
                when (
                    val direct = sourceProvider.copy(
                        source = sourceNode.ref,
                        destinationDirectory = destinationDirectory,
                        targetName = tempName,
                        replace = false,
                    )
                ) {
                    is Outcome.Success -> direct
                    is Outcome.Failure ->
                        streamCopy(
                            sourceProvider = sourceProvider,
                            destinationProvider = destinationProvider,
                            sourceNode = sourceNode,
                            destinationDirectory = destinationDirectory,
                            targetName = tempName,
                            onProgress = onProgress,
                        )
                }
            } else {
                streamCopy(
                    sourceProvider = sourceProvider,
                    destinationProvider = destinationProvider,
                    sourceNode = sourceNode,
                    destinationDirectory = destinationDirectory,
                    targetName = tempName,
                    onProgress = onProgress,
                )
            }

        if (staged is Outcome.Failure) {
            return staged
        }
        staged as Outcome.Success

        val backup = destinationProvider.rename(
            existing.ref,
            backupName,
        )
        if (backup is Outcome.Failure) {
            destinationProvider.delete(staged.value.ref)
            return backup
        }
        backup as Outcome.Success

        val promoted = destinationProvider.rename(
            staged.value.ref,
            targetName,
        )
        if (promoted is Outcome.Failure) {
            destinationProvider.rename(
                backup.value.ref,
                targetName,
            )
            destinationProvider.delete(staged.value.ref)
            return promoted
        }
        promoted as Outcome.Success

        if (move) {
            val deletedSource =
                sourceProvider.delete(sourceNode.ref)
            if (deletedSource is Outcome.Failure) {
                destinationProvider.delete(
                    promoted.value.ref,
                )
                destinationProvider.rename(
                    backup.value.ref,
                    targetName,
                )
                return deletedSource
            }
        }

        destinationProvider.delete(backup.value.ref)
        return promoted
    }

    private suspend fun <T> runBatch(
        sources: List<YFileRef>,
        operation:
            suspend (YFileRef) -> Outcome<T>,
    ): YFileBatchResult {
        var succeeded = 0
        var skipped = 0
        val failures =
            mutableListOf<YFileFailure>()

        for (source in sources.distinct()) {
            when (val result = operation(source)) {
                is Outcome.Success ->
                    succeeded += 1
                is Outcome.Failure -> {
                    if (
                        result.error.code ==
                            "conflict_skipped"
                    ) {
                        skipped += 1
                    } else {
                        failures += YFileFailure(
                            ref = source,
                            code = result.error.code,
                            message = result.message,
                        )
                    }
                }
            }
        }

        return YFileBatchResult(
            succeeded = succeeded,
            skipped = skipped,
            failures = failures,
        )
    }

    private fun providerOrFailure(
        ref: YFileRef,
    ): Outcome<YFileProvider> =
        registry.provider(ref)

    private inline fun <T> withProvider(
        providerId: String,
        block: (YFileProvider) -> Outcome<T>,
    ): Outcome<T> =
        when (
            val provider =
                registry.provider(providerId)
        ) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    private inline fun <T> withProvider(
        ref: YFileRef,
        block: (YFileProvider) -> Outcome<T>,
    ): Outcome<T> =
        when (val provider = registry.provider(ref)) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    private suspend inline fun <T>
        withProviderSuspend(
            ref: YFileRef,
            crossinline block:
                suspend (YFileProvider) -> Outcome<T>,
        ): Outcome<T> =
        when (val provider = registry.provider(ref)) {
            is Outcome.Success ->
                block(provider.value)
            is Outcome.Failure ->
                provider
        }

    companion object {
        private const val TRANSFER_CHUNK_BYTES =
            64 * 1024
        private const val CONFLICT_SKIPPED_MESSAGE =
            "Destination already exists"
        private const val SYMLINK_TRANSFER_MESSAGE =
            "Destination cannot create symbolic links"
    }
}
