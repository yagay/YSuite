package com.yagay.ysuite.feature.ydiag.api

import org.junit.Assert.*
import org.junit.Test

class YDiagSignalDetectorTest {
    @Test fun missingOriginalCatalogEntriesAreRestored() {
        assertTrue(YDiagCatalog.options.any { it.id == "events" })
        for (id in listOf("webview", "freeze")) {
            val preset = YDiagCatalog.presets.first { it.id == id }
            assertTrue(YDiagCatalog.options.map { it.id }.containsAll(preset.options))
        }
    }

    @Test fun diagnosticSignalsRemainDistinct() {
        val cases = mapOf(
            "FATAL EXCEPTION" to "crash",
            "SIGSEGV" to "native-segv",
            "SIGABRT" to "native-abrt",
            "ANR in sample.app" to "anr",
            "OutOfMemoryError" to "memory",
            "TransactionTooLargeException" to "binder-size",
            "DeadObjectException" to "binder-dead",
            "SecurityException" to "permission",
            "avc: denied" to "selinux",
            "renderer process gone" to "webview",
            "NoSuchMethodError" to "hook-method",
            "ClassNotFoundException" to "hook-class",
            "YDiag.Hook hook_failed" to "hook",
        )
        cases.forEach { (line, expected) ->
            assertEquals(expected, YDiagSignalDetector.detect(line)?.category)
        }
        assertNull(YDiagSignalDetector.detect("normal log"))
    }

    @Test fun repeatedEventsAreDeduplicated() {
        assertEquals(
            listOf("anr", "crash"),
            YDiagSignalDetector.find(
                sequenceOf("ANR in a", "ANR in b", "FATAL EXCEPTION"),
            ).map { it.category },
        )
    }
}
