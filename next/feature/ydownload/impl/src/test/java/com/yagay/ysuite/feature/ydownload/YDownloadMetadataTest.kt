package com.yagay.ysuite.feature.ydownload

import org.junit.Assert.assertEquals
import org.junit.Test

class YDownloadMetadataTest {
    @Test
    fun sanitizeFileNameRemovesReservedCharacters() {
        assertEquals(
            "a_b_c_.zip",
            YDownloadMetadataFetcher.sanitizeFileName(
                "a:b/c?.zip",
            ),
        )
    }
}
