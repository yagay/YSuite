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

    @Test
    fun replaceUpdatesOwnerChecksAndSummary() =
        runBlocking {
            val center = DiagnosticCenter()
            center.register(
                owner = "platform",
                checks =
                    listOf(
                        DiagnosticCheck {
                            DiagnosticFinding(
                                id = "old",
                                status =
                                    DiagnosticStatus.Pass,
                                summary = "old",
                            )
                        },
                    ),
            )
            center.replace(
                owner = "platform",
                checks =
                    listOf(
                        DiagnosticCheck {
                            DiagnosticFinding(
                                id = "warning",
                                status =
                                    DiagnosticStatus.Warning,
                                summary = "warning",
                            )
                        },
                        DiagnosticCheck {
                            DiagnosticFinding(
                                id = "failure",
                                status =
                                    DiagnosticStatus.Failure,
                                summary = "failure",
                            )
                        },
                    ),
            )

            val report = center.runAll()

            assertEquals(
                2,
                report.findings.size,
            )
            assertEquals(
                1,
                report.summary.warnings,
            )
            assertEquals(
                1,
                report.summary.failures,
            )
        }
}
