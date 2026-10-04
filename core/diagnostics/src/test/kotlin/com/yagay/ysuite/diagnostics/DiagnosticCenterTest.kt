package com.yagay.ysuite.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticCenterTest {
    @Test
    fun centerTagsFindingsWithRegisteredOwner() = runBlocking {
        val center = DiagnosticCenter()

        center.register(
            owner = "feature-a",
            checks = listOf(
                DiagnosticCheck {
                    DiagnosticFinding(
                        id = "ready",
                        status = DiagnosticStatus.Pass,
                        summary = "ready",
                    )
                },
            ),
        )

        val report = center.runAll()

        assertEquals(1, report.findings.size)
        assertEquals("feature-a", report.findings.single().owner)
    }
}
