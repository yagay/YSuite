package com.yagay.ydownload

import org.junit.Assert.assertEquals
import org.junit.Test

class QdmHostChromeTest {
    private val tasks = listOf(
        DownloadItem(1L, "https://example.com/one.zip", "one.zip", state = DownloadState.RUNNING),
        DownloadItem(2L, "https://example.com/two.pdf", "two.pdf", state = DownloadState.PAUSED),
        DownloadItem(3L, "https://example.com/three.mp4", "three.mp4", state = DownloadState.COMPLETED),
        DownloadItem(4L, "https://example.com/four.zip", "four.zip", state = DownloadState.FAILED),
        DownloadItem(5L, "https://example.com/five", "five", state = DownloadState.CANCELLED),
    )

    @Test
    fun activeIncludesRunningAndPaused() {
        assertEquals(
            listOf(1L, 2L),
            filterDownloadItems(tasks, DownloadListFilter.ACTIVE, "").map { it.id },
        )
    }

    @Test
    fun failedIncludesCancelledButNotCompleted() {
        assertEquals(
            listOf(4L, 5L),
            filterDownloadItems(tasks, DownloadListFilter.FAILED, "").map { it.id },
        )
    }

    @Test
    fun searchMatchesNameOrUrlIgnoringCase() {
        assertEquals(
            listOf(1L, 4L),
            filterDownloadItems(tasks, DownloadListFilter.ALL, "ZIP").map { it.id },
        )
        assertEquals(
            listOf(3L),
            filterDownloadItems(tasks, DownloadListFilter.COMPLETED, "three").map { it.id },
        )
    }
}
