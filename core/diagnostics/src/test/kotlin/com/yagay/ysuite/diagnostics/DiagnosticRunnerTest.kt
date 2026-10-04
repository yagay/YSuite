package com.yagay.ysuite.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRunnerTest {
    @Test
    fun runnerKeepsFailuresAsUnknownFindings() = runBlocking {
        val runner = DiagnosticRunner(
            listOf(
                DiagnosticCheck {
                    DiagnosticFinding(
                        id = "ok",
                        status = DiagnosticStatus.Pass,
                        summary = "ok",
                    )
                },
                DiagnosticCheck {
                    error("boom")
                },
            ),
        )

        val report = runner.runAll()

        assertEquals(2, report.findings.size)
        assertEquals(DiagnosticStatus.Pass, report.findings[0].status)
        assertEquals(DiagnosticStatus.Unknown, report.findings[1].status)
        assertTrue(report.findings[1].summary.contains("boom"))
    }
}
