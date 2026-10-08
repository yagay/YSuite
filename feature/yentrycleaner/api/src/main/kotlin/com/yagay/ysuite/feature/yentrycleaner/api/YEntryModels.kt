package com.yagay.ysuite.feature.yentrycleaner.api

enum class YEntrySurface {
    ShareText,
    ShareImage,
    ShareMultiple,
    ProcessText,
    Open,
    Browser,
    Tile,
    Shortcut,
    Widget,
    Historical,
}

enum class YEntryCandidateState {
    Current,
    Restricted,
    Historical,
}

data class YEntryCandidate(
    val id: String,
    val surface: YEntrySurface,
    val qualifier: String,
    val packageName: String,
    val className: String,
    val label: String,
    val system: Boolean,
    val hidden: Boolean,
    val locked: Boolean,
    val priority: Int?,
    val state: YEntryCandidateState = YEntryCandidateState.Current,
    val rootEnabled: Boolean? = null,
    val rootBlocked: Boolean = false,
)
