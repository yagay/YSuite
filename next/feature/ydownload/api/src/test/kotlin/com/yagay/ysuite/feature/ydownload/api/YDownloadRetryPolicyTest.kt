package com.yagay.ysuite.feature.ydownload.api

import org.junit.Assert.*
import org.junit.Test

class YDownloadRetryPolicyTest {
    @Test fun retryBudgetAndDisable() {
        assertFalse(YDownloadRetryPolicy.canRetry(false, 5, 0))
        assertTrue(YDownloadRetryPolicy.canRetry(true, 2, 0))
        assertTrue(YDownloadRetryPolicy.canRetry(true, 2, 1))
        assertFalse(YDownloadRetryPolicy.canRetry(true, 2, 2))
        assertFalse(YDownloadRetryPolicy.canRetry(true, 0, 0))
    }
    @Test fun exponentialDelaysHaveACeiling() {
        assertEquals(500L, YDownloadRetryPolicy.backoffMillis(1))
        assertEquals(1000L, YDownloadRetryPolicy.backoffMillis(2))
        assertEquals(2000L, YDownloadRetryPolicy.backoffMillis(3))
        assertEquals(5000L, YDownloadRetryPolicy.backoffMillis(6))
    }
}
