package com.yagay.ysuite.diagnostics

enum class DiagnosticStatus {
    Pass,
    Warning,
    Failure,
    Unknown,
}

data class DiagnosticFinding(
    val id: String,
    val status: DiagnosticStatus,
    val summary: String,
    val details: String? = null,
)

fun interface DiagnosticCheck {
    suspend fun run(): DiagnosticFinding
}

data class DiagnosticReport(
    val findings: List<DiagnosticFinding>,
) {
    val hasFailures: Boolean
        get() = findings.any { it.status == DiagnosticStatus.Failure }
}

class DiagnosticRunner(
    private val checks: List<DiagnosticCheck>,
) {
    suspend fun runAll(): DiagnosticReport =
        DiagnosticReport(
            findings = checks.mapIndexed { index, check ->
                runCatching { check.run() }
                    .getOrElse { error ->
                        DiagnosticFinding(
                            id = "check_$index",
                            status = DiagnosticStatus.Unknown,
                            summary = error.message ?: error::class.java.simpleName,
                            details = error.stackTraceToString(),
                        )
                    }
            },
        )
}
