package com.yagay.ysuite.feature.yfiles.api

import com.yagay.ysuite.common.Outcome

interface YFilesRepository {
    fun initialPath(): String

    suspend fun list(query: YFileQuery): Outcome<List<YFileEntry>>

    suspend fun properties(
        entry: YFileEntry,
    ): Outcome<YFileProperties>

    fun parent(path: String): String?
}
