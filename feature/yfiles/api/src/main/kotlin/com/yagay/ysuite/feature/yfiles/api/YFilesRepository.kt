package com.yagay.ysuite.feature.yfiles.api

import com.yagay.ysuite.common.Outcome

interface YFilesRepository {
    fun initialPath(): String

    suspend fun list(query: YFileQuery): Outcome<List<YFileEntry>>

    suspend fun properties(
        entry: YFileEntry,
    ): Outcome<YFileProperties>

    suspend fun createDirectory(
        parentPath: String,
        name: String,
    ): Outcome<YFileEntry>

    suspend fun createFile(
        parentPath: String,
        name: String,
    ): Outcome<YFileEntry>

    suspend fun rename(
        entry: YFileEntry,
        newName: String,
    ): Outcome<YFileEntry>

    suspend fun transfer(
        entries: List<YFileEntry>,
        destinationPath: String,
        mode: YFileTransferMode,
    ): Outcome<YFileBatchResult>

    suspend fun moveToTrash(
        entries: List<YFileEntry>,
    ): Outcome<YFileBatchResult>

    suspend fun listTrash(): Outcome<List<YTrashEntry>>

    suspend fun restoreTrash(
        ids: Set<String>,
    ): Outcome<YFileBatchResult>

    suspend fun emptyTrash(): Outcome<Int>

    suspend fun resolve(
        paths: Collection<String>,
    ): Outcome<List<YFileEntry>>

    fun parent(path: String): String?
}

interface YFilesPlacesRepository {
    fun snapshot(): YFilesPlacesSnapshot

    fun toggleFavorite(path: String): YFilesPlacesSnapshot

    fun rememberRecent(path: String): YFilesPlacesSnapshot
}
