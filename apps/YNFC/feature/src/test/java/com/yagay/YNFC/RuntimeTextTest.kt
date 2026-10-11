package com.yagay.YNFC

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeTextTest {
    @Test fun missingInjectionExplainsScopeAndRestart() {
        val status = RuntimeStatus(currentPid = 10114, hookBuild = 0)
        val diagnosis = RuntimeText.hookDiagnosis(status, 40)
        assertTrue(diagnosis.contains("NO_INJECTION"))
        assertTrue(diagnosis.contains("com.android.nfc"))
    }

    @Test fun staleNfcProcessIsNotCalledInstalled() {
        val status = RuntimeStatus(currentPid = 10114, scopePid = 10000, hookPid = 10000)
        assertTrue(RuntimeText.hookDiagnosis(status, 40).contains("STALE_PROCESS"))
    }

    @Test fun reportedHookMismatchIsDistinctFromMissingInjection() {
        val status = RuntimeStatus(
            currentPid = 10114, scopePid = 10114, hookPid = 10114,
            scopeOk = true, hookInstalled = true, hookBuild = 39
        )
        assertTrue(RuntimeText.hookDiagnosis(status, 40).contains("HOOK_BUILD_MISMATCH"))
    }

    @Test fun boundedLinesKeepsNewestEntries() {
        assertEquals(listOf("3", "4"), RuntimeText.boundedLines("1\n2\n3\n4", 2))
    }

    @Test fun updateWindowProducesExactIncomingContent() {
        val target = mutableListOf("same", "old", "tail")
        RuntimeText.updateWindow(target, listOf("same", "new", "extra", "tail"))
        assertEquals(listOf("same", "new", "extra", "tail"), target)
    }

    @Test fun summaryIncludesSemanticProof() {
        val summary = RuntimeText.statusSummary(
            RuntimeStatus(operationState = "IDLE", effectiveState = "ACTIVE", rfAccepted = true),
            expectedHookBuild = 39,
            readModeEnabled = false
        )
        assertTrue(summary.contains("effective=ACTIVE"))
        assertTrue(summary.contains("expectedHook=39"))
    }
}
