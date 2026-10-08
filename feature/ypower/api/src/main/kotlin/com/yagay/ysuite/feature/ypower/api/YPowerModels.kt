package com.yagay.ysuite.feature.ypower.api

data class YPowerAppSummary(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val enabled: Boolean,
    val recommended: Boolean,
)

data class YPowerProfile(
    val packageName: String,
    val enabled: Boolean = false,
    val dozeWhitelist: Boolean = true,
    val backgroundOps: Boolean = true,
    val standbyActive: Boolean = true,
    val backgroundData: Boolean = true,
    val autoGrantDangerous: Boolean = false,
    val simulateSystemApp: Boolean = false,
    val simulatePermissions: Boolean = false,
    val simulatedPermissions: List<String> = emptyList(),
    val tracePackageScan: Boolean = false,
    val traceFiles: Boolean = false,
    val traceCommands: Boolean = false,
    val traceProperties: Boolean = false,
    val tracePermissions: Boolean = false,
    val traceDebugger: Boolean = false,
    val traceExceptions: Boolean = false,
    val traceSecurityApis: Boolean = false,
    val traceNative: Boolean = false,
    val traceSyscalls: Boolean = false,
    val traceStacks: Boolean = true,
    val diagnosticSessionId: String = "",
) {
    val anyHookFeature: Boolean
        get() =
            simulateSystemApp ||
                simulatePermissions ||
                tracePackageScan ||
                traceFiles ||
                traceCommands ||
                traceProperties ||
                tracePermissions ||
                traceDebugger ||
                traceExceptions ||
                traceSecurityApis ||
                traceNative ||
                traceSyscalls
}

enum class YPowerFindingStatus {
    Pass,
    Detected,
    Warning,
    Failure,
}

data class YPowerFinding(
    val id: String,
    val status: YPowerFindingStatus,
    val summary: String,
    val detail: String,
    val recommendation: String? = null,
)

data class YPowerApplyResult(
    val applied: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val success: Boolean
        get() = errors.isEmpty()
}
