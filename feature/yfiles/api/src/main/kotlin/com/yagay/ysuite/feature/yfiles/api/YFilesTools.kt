package com.yagay.ysuite.feature.yfiles.api

data class YFileHash(
    val path: String,
    val algorithm: String,
    val hex: String,
)

data class YDuplicateGroup(
    val sizeBytes: Long,
    val hash: String,
    val entries: List<YFileEntry>,
)

data class YDirectoryAnalysis(
    val path: String,
    val fileCount: Int,
    val directoryCount: Int,
    val totalBytes: Long,
    val largestFiles: List<YFileEntry>,
    val truncated: Boolean,
)

data class YTextDocument(
    val path: String,
    val text: String,
    val truncated: Boolean,
)

data class YHexPreview(
    val path: String,
    val byteCount: Int,
    val text: String,
    val truncated: Boolean,
)

data class YBatchRenameRule(
    val prefix: String = "",
    val suffix: String = "",
    val find: String = "",
    val replace: String = "",
)

data class YBatchRenameItem(
    val path: String,
    val originalName: String,
    val newName: String,
)

data class YArchiveResult(
    val archive: YFileEntry,
    val addedEntries: Int,
)

interface YFilesToolsRepository {
    suspend fun createZip(
        entries: List<YFileEntry>,
        destinationPath: String,
        archiveName: String,
    ): com.yagay.ysuite.common.Outcome<YArchiveResult>

    suspend fun extractZip(
        archive: YFileEntry,
        destinationPath: String,
    ): com.yagay.ysuite.common.Outcome<YFileBatchResult>

    suspend fun sha256(
        entry: YFileEntry,
    ): com.yagay.ysuite.common.Outcome<YFileHash>

    suspend fun findDuplicates(
        rootPath: String,
        maxFiles: Int = 5_000,
    ): com.yagay.ysuite.common.Outcome<List<YDuplicateGroup>>

    suspend fun analyzeDirectory(
        rootPath: String,
        maxFiles: Int = 20_000,
    ): com.yagay.ysuite.common.Outcome<YDirectoryAnalysis>

    suspend fun readText(
        entry: YFileEntry,
        maxChars: Int = 200_000,
    ): com.yagay.ysuite.common.Outcome<YTextDocument>

    suspend fun writeText(
        entry: YFileEntry,
        text: String,
    ): com.yagay.ysuite.common.Outcome<YFileEntry>

    suspend fun readHex(
        entry: YFileEntry,
        maxBytes: Int = 2_048,
    ): com.yagay.ysuite.common.Outcome<YHexPreview>

    suspend fun previewBatchRename(
        entries: List<YFileEntry>,
        rule: YBatchRenameRule,
    ): com.yagay.ysuite.common.Outcome<List<YBatchRenameItem>>

    suspend fun applyBatchRename(
        items: List<YBatchRenameItem>,
    ): com.yagay.ysuite.common.Outcome<YFileBatchResult>
}
