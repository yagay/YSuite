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
    val hidden: Boolean = false,
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
    Observe,
}

data class YFileProviderDescriptor(
    val id: String,
    val kind: YFileProviderKind,
    val capabilities: Set<YFileCapability>,
)

data class YFileQuery(
    val text: String = "",
    val recursive: Boolean = false,
    val showHidden: Boolean = false,
    val maxResults: Int = 500,
)
