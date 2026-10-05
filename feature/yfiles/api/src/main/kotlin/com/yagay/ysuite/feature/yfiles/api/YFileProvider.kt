package com.yagay.ysuite.feature.yfiles.api

import com.yagay.ysuite.common.Outcome

interface YFileProvider {
    val descriptor: YFileProviderDescriptor

    fun root(): YFileRef

    fun parent(ref: YFileRef): YFileRef?

    suspend fun list(
        directory: YFileRef,
        query: YFileQuery = YFileQuery(),
    ): Outcome<List<YFileNode>>

    suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode>

    suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode>

    suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode>

    suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode>

    suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit>

    suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode>

    suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode>

    suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk>

    suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean = false,
    ): Outcome<Unit>

    suspend fun setPosixMode(
        ref: YFileRef,
        mode: Int,
    ): Outcome<Unit> =
        unsupported("posix_mode")

    suspend fun createSymbolicLink(
        parent: YFileRef,
        name: String,
        target: String,
    ): Outcome<YFileNode> =
        unsupported("symbolic_link")

    suspend fun readSymbolicLink(
        ref: YFileRef,
    ): Outcome<String> =
        unsupported("read_symbolic_link")

    private fun <T> unsupported(
        capability: String,
    ): Outcome<T> =
        Outcome.Failure(
            code = "unsupported_" + capability,
            message = "Provider capability is unavailable",
        )
}


interface YFileProviderCatalog {
    val descriptors: List<YFileProviderDescriptor>

    fun descriptor(
        providerId: String,
    ): Outcome<YFileProviderDescriptor>

    fun capabilityMatrix(
        providerId: String,
    ): Outcome<YFileProviderCapabilityMatrix>
}
