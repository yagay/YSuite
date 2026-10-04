package com.yagay.ysuite.feature.yfiles.api

import com.yagay.ysuite.common.Outcome

typealias YFileProgressListener =
    (YFileOperationProgress) -> Unit

interface YFilesEngine {
    val providers: List<YFileProviderDescriptor>

    fun root(providerId: String): Outcome<YFileRef>

    fun parent(ref: YFileRef): Outcome<YFileRef?>

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

    suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy =
            YFileConflictStrategy.Rename,
        onProgress: YFileProgressListener? = null,
    ): Outcome<YFileNode>

    suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy =
            YFileConflictStrategy.Rename,
        onProgress: YFileProgressListener? = null,
    ): Outcome<YFileNode>

    suspend fun copyBatch(
        sources: List<YFileRef>,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy =
            YFileConflictStrategy.Rename,
        onProgress: YFileProgressListener? = null,
    ): YFileBatchResult

    suspend fun moveBatch(
        sources: List<YFileRef>,
        destinationDirectory: YFileRef,
        strategy: YFileConflictStrategy =
            YFileConflictStrategy.Rename,
        onProgress: YFileProgressListener? = null,
    ): YFileBatchResult

    suspend fun deleteBatch(
        refs: List<YFileRef>,
    ): YFileBatchResult

    suspend fun setPosixMode(
        ref: YFileRef,
        mode: Int,
    ): Outcome<Unit>

    suspend fun createSymbolicLink(
        parent: YFileRef,
        name: String,
        target: String,
    ): Outcome<YFileNode>

    suspend fun readSymbolicLink(
        ref: YFileRef,
    ): Outcome<String>
}
