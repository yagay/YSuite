package com.yagay.ysuite.feature.yentrycleaner.api

import org.junit.Assert.assertEquals
import org.junit.Test

class YEntryIntentRoutingTest {
    @Test
    fun multipleShareDoesNotLeakIntoSingleShare() {
        assertEquals(
            YEntrySurface.ShareMultiple,
            YEntryIntentRouting.surface(YEntryIntentRouting.SEND_MULTIPLE, "image/png", null),
        )
        assertEquals(
            YEntrySurface.ShareImage,
            YEntryIntentRouting.surface(YEntryIntentRouting.SEND, "image/png", null),
        )
        assertEquals(
            "*",
            YEntryIntentRouting.qualifier(YEntrySurface.ShareMultiple, "image/png", null),
        )
    }

    @Test
    fun processTextIsIndependentOfShare() {
        assertEquals(
            YEntrySurface.ProcessText,
            YEntryIntentRouting.surface(YEntryIntentRouting.PROCESS_TEXT, "text/plain", null),
        )
        assertEquals(
            "text/plain",
            YEntryIntentRouting.qualifier(YEntrySurface.ProcessText, "text/plain; charset=UTF-8", null),
        )
    }

    @Test
    fun webFileAndUnrelatedSchemesAreSeparated() {
        assertEquals(
            YEntrySurface.Browser,
            YEntryIntentRouting.surface(YEntryIntentRouting.VIEW, "text/html", "https"),
        )
        assertEquals(
            YEntrySurface.Open,
            YEntryIntentRouting.surface(YEntryIntentRouting.VIEW, "application/pdf", "https"),
        )
        assertEquals(
            YEntrySurface.Open,
            YEntryIntentRouting.surface(YEntryIntentRouting.VIEW, "application/pdf", "content"),
        )
        assertEquals(
            null,
            YEntryIntentRouting.surface(YEntryIntentRouting.VIEW, null, "tel"),
        )
        assertEquals(
            "example.org",
            YEntryIntentRouting.qualifier(YEntrySurface.Browser, null, "www.Example.org"),
        )
    }
}
