package com.yagay.ysuite.feature.yfiles.api

data class YFileRef(
    val providerId: String,
    val path: String,
)

enum class YFileType {
    File,
    Directory,
    SymbolicLink,
    Other,
}

data class YFileNode(
    val ref: YFileRef,
    val name: String,
    val type: YFileType,
    val sizeBytes: Long? = null,
    val modifiedAtMillis: Long? = null,
    val mimeType: String? = null,
    val hidden: Boolean = false,
    val readable: Boolean = true,
    val writable: Boolean = false,
    val executable: Boolean = false,
    val posixMode: Int? = null,
)

enum class YFileProviderKind {
    Local,
    Document,
    Root,
    Archive,
    Remote,
}

enum class YFileCapability {
    Browse,
    Search,
    Read,
    Write,
    Create,
    Rename,
    Delete,
    Copy,
    Move,
    PosixMode,
    SymbolicLink,
    ArchiveMount,
}

data class YFileProviderDescriptor(
    val id: String,
    val kind: YFileProviderKind,
    val capabilities: Set<YFileCapability>,
)

enum class YFileSort {
    Name,
    Modified,
    Size,
    Type,
}

data class YFileQuery(
    val text: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val maxResults: Int = 2_000,
)

enum class YFileConflictStrategy {
    Rename,
    Replace,
    Skip,
}

data class YFileChunk(
    val data: ByteArray,
    val eof: Boolean,
)

data class YFileOperationProgress(
    val currentName: String,
    val completedBytes: Long,
    val totalBytes: Long?,
    val completedItems: Int,
    val totalItems: Int,
)

data class YFileFailure(
    val ref: YFileRef?,
    val code: String,
    val message: String,
)

data class YFileBatchResult(
    val succeeded: Int,
    val skipped: Int,
    val failures: List<YFileFailure>,
) {
    val failed: Int
        get() = failures.size
}

data class YFileClipboard(
    val refs: List<YFileRef>,
    val move: Boolean,
)

data class YFileLocation(
    val ref: YFileRef,
    val label: String,
)
