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
    val owner: String = "core",
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

class DiagnosticCenter {
    private val checksByOwner = linkedMapOf<String, MutableList<DiagnosticCheck>>()

    @Synchronized
    fun register(
        owner: String,
        checks: List<DiagnosticCheck>,
    ) {
        require(owner.isNotBlank()) { "owner cannot be blank" }
        checksByOwner.getOrPut(owner) { mutableListOf() }.addAll(checks)
    }

    @Synchronized
    fun clear(owner: String) {
        checksByOwner.remove(owner)
    }

    suspend fun runAll(): DiagnosticReport {
        val snapshot = synchronized(this) {
            checksByOwner.flatMap { (owner, checks) ->
                checks.map { check ->
                    DiagnosticCheck {
                        check.run().copy(owner = owner)
                    }
                }
            }
        }
        return DiagnosticRunner(snapshot).runAll()
    }

    suspend fun run(owner: String): DiagnosticReport {
        val snapshot = synchronized(this) {
            checksByOwner[owner]
                ?.map { check ->
                    DiagnosticCheck {
                        check.run().copy(owner = owner)
                    }
                }
                .orEmpty()
        }
        return DiagnosticRunner(snapshot).runAll()
    }
}
