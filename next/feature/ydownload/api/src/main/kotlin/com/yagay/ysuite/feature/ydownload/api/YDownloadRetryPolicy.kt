package com.yagay.ysuite.feature.ydownload.api

/** Bounded retries for the private downloader, never for DownloadManager. */
object YDownloadRetryPolicy {
    fun canRetry(autoRetry: Boolean, maxRetries: Int, retryCount: Int): Boolean =
        autoRetry && retryCount.coerceAtLeast(0) < maxRetries.coerceIn(0, 5)

    fun backoffMillis(nextAttempt: Int): Long =
        (500L shl (nextAttempt - 1).coerceIn(0, 4)).coerceAtMost(5_000L)
}
