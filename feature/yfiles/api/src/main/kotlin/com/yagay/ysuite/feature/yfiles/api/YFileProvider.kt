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
    ): Outcome<YFileNode>

    suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
    ): Outcome<YFileNode>
}
