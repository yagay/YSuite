package com.yagay.ysuite.feature.ydownload.api

import org.junit.Assert.assertEquals
import org.junit.Test

class YDownloadFilterTest {
    @Test
    fun queueTabOnlyContainsQueuedPendingItems() {
        val items = listOf(
            item("a", YDownloadState.Pending, queued = true),
            item("b", YDownloadState.Pending, queued = false),
            item("c", YDownloadState.Downloading, queued = false),
        )

        assertEquals(
            listOf("a"),
            items.forTab(YDownloadTab.Queue).map { it.id },
        )
    }

    @Test
    fun searchMatchesNameAndUrlIgnoringCase() {
        val items = listOf(
            item(
                id = "a",
                state = YDownloadState.Completed,
                name = "Ubuntu.iso",
                url = "https://example.com/ubuntu.iso",
            ),
            item(
                id = "b",
                state = YDownloadState.Completed,
                name = "notes.txt",
                url = "https://mirror.example.com/file",
            ),
        )

        assertEquals(
            listOf("a"),
            items.forTab(
                tab = YDownloadTab.All,
                query = "UBUNTU",
            ).map { it.id },
        )
    }

    private fun item(
        id: String,
        state: YDownloadState,
        queued: Boolean = false,
        name: String = id,
        url: String = "https://example.com/$id",
    ) = YDownloadItem(
        id = id,
        url = url,
        fileName = name,
        outputUri = null,
        mimeType = "",
        totalBytes = 100,
        downloadedBytes = 0,
        state = state,
        speedBytesPerSecond = 0,
        etaSeconds = 0,
        addedAtMillis = 0,
        completedAtMillis = null,
        errorMessage = null,
        supportsRanges = true,
        queued = queued,
        referer = null,
        userAgent = null,
        cookies = null,
        username = null,
        password = null,
    )
}
