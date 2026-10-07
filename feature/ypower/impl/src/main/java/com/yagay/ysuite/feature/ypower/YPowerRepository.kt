package com.yagay.ysuite.feature.ypower

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.ypower.api.YPowerAppSummary
import com.yagay.ysuite.feature.ypower.api.YPowerApplyResult
import com.yagay.ysuite.feature.ypower.api.YPowerFinding
import com.yagay.ysuite.feature.ypower.api.YPowerFindingStatus
import com.yagay.ysuite.feature.ypower.api.YPowerProfile
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import org.json.JSONObject

internal class YPowerRepository(
    private val context: Context,
    private val root: RootGateway,
    private val hooks: HookGateway,
    private val logger: YSuiteLogger,
) {
    private val prefs =
        context.getSharedPreferences(
            "ysuite_ypower_profiles",
            Context.MODE_PRIVATE,
        )
    private val packageManager = context.packageManager

    fun apps(): List<YPowerAppSummary> =
        packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .map { info ->
                val profile = load(info.packageName)
                YPowerAppSummary(
                    packageName = info.packageName,
                    label =
                        runCatching {
                            packageManager
                                .getApplicationLabel(info)
                                .toString()
                        }.getOrDefault(info.packageName),
                    system =
                        info.flags and
                            ApplicationInfo.FLAG_SYSTEM !=
                            0,
                    enabled = profile.enabled,
                    recommended =
                        info.packageName in RECOMMENDED_PACKAGES,
                )
            }
            .sortedWith(
                compareByDescending<YPowerAppSummary> { it.enabled }
                    .thenByDescending { it.recommended }
                    .thenBy { it.label.lowercase() },
            )
            .toList()

    fun load(packageName: String): YPowerProfile {
        val raw = prefs.getString(packageName, null)
        if (raw.isNullOrBlank()) {
            return YPowerProfile(packageName)
        }
        return runCatching {
            val value = JSONObject(raw)
            YPowerProfile(
                packageName = packageName,
                enabled = value.optBoolean("enabled", false),
                dozeWhitelist =
                    value.optBoolean("dozeWhitelist", true),
                backgroundOps =
                    value.optBoolean("backgroundOps", true),
                standbyActive =
                    value.optBoolean("standbyActive", true),
                backgroundData =
                    value.optBoolean("backgroundData", true),
                autoGrantDangerous =
                    value.optBoolean(
                        "autoGrantDangerous",
                        false,
                    ),
                simulateSystemApp =
                    value.optBoolean(
                        "simulateSystemApp",
                        false,
                    ),
                simulatePermissions =
                    value.optBoolean(
                        "simulatePermissions",
                        false,
                    ),
                tracePackageScan =
                    value.optBoolean(
                        "tracePackageScan",
                        false,
                    ),
                traceFiles =
                    value.optBoolean("traceFiles", false),
                traceCommands =
                    value.optBoolean(
                        "traceCommands",
                        false,
                    ),
                traceProperties =
                    value.optBoolean(
                        "traceProperties",
                        false,
                    ),
                tracePermissions =
                    value.optBoolean(
                        "tracePermissions",
                        false,
                    ),
                traceDebugger =
                    value.optBoolean(
                        "traceDebugger",
                        false,
                    ),
                traceExceptions =
                    value.optBoolean(
                        "traceExceptions",
                        false,
                    ),
                traceSecurityApis =
                    value.optBoolean(
                        "traceSecurityApis",
                        false,
                    ),
                traceNative =
                    value.optBoolean("traceNative", false),
                traceSyscalls =
                    value.optBoolean(
                        "traceSyscalls",
                        false,
                    ),
                traceStacks =
                    value.optBoolean("traceStacks", true),
            )
        }.getOrDefault(YPowerProfile(packageName))
    }

    fun save(profile: YPowerProfile) {
        val raw =
            JSONObject().apply {
                put("enabled", profile.enabled)
                put(
                    "dozeWhitelist",
                    profile.dozeWhitelist,
                )
                put("backgroundOps", profile.backgroundOps)
                put("standbyActive", profile.standbyActive)
                put("backgroundData", profile.backgroundData)
                put(
                    "autoGrantDangerous",
                    profile.autoGrantDangerous,
                )
                put(
                    "simulateSystemApp",
                    profile.simulateSystemApp,
                )
                put(
                    "simulatePermissions",
                    profile.simulatePermissions,
                )
                put(
                    "tracePackageScan",
                    profile.tracePackageScan,
                )
                put("traceFiles", profile.traceFiles)
                put("traceCommands", profile.traceCommands)
                put(
                    "traceProperties",
                    profile.traceProperties,
                )
                put(
                    "tracePermissions",
                    profile.tracePermissions,
                )
                put("traceDebugger", profile.traceDebugger)
                put(
                    "traceExceptions",
                    profile.traceExceptions,
                )
                put(
                    "traceSecurityApis",
                    profile.traceSecurityApis,
                )
                put("traceNative", profile.traceNative)
                put("traceSyscalls", profile.traceSyscalls)
                put("traceStacks", profile.traceStacks)
            }.toString()
        check(
            prefs.edit()
                .putString(profile.packageName, raw)
                .commit(),
        )
    }

    suspend fun rootStatus(): CapabilityStatus = root.status()

    suspend fun hookStatus(): CapabilityStatus = hooks.status()

    suspend fun apply(profile: YPowerProfile): YPowerApplyResult {
        if (root.status() != CapabilityStatus.Available) {
            return YPowerApplyResult(
                errors = listOf("root_unavailable"),
            )
        }

        val applied = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val pkg = shellQuote(profile.packageName)
        val uid =
            runCatching {
                packageManager
                    .getApplicationInfo(
                        profile.packageName,
                        0,
                    ).uid
            }.getOrElse {
                return YPowerApplyResult(
                    errors = listOf("package_missing"),
                )
            }

        suspend fun run(
            id: String,
            command: String,
        ) {
            when (
                val outcome =
                    root.execute(
                        RootRequest(
                            command = command,
                            timeoutMillis = 8_000L,
                        ),
                    )
            ) {
                is Outcome.Success ->
                    if (outcome.value.exitCode == 0) {
                        applied += id
                    } else {
                        notes +=
                            id +
                                ": " +
                                outcome.value.stderr
                                    .ifBlank {
                                        outcome.value.stdout
                                    }
                    }
                is Outcome.Failure -> {
                    errors +=
                        id +
                            ": " +
                            outcome.error.code
                    logger.error(
                        TAG,
                        "Root enhancement failed: $id",
                        outcome.error.cause,
                    )
                }
            }
        }

        run(
            "doze",
            if (profile.dozeWhitelist) {
                "cmd deviceidle whitelist +$pkg"
            } else {
                "cmd deviceidle whitelist -$pkg"
            },
        )
        if (profile.backgroundOps) {
            run(
                "background_ops",
                "cmd appops set $pkg RUN_IN_BACKGROUND allow; " +
                    "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; " +
                    "cmd appops set $pkg START_FOREGROUND allow",
            )
        } else {
            run(
                "background_ops_reset",
                "cmd appops set $pkg RUN_IN_BACKGROUND default; " +
                    "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default",
            )
        }
        if (profile.standbyActive) {
            run(
                "standby_active",
                "am set-inactive $pkg false; " +
                    "am set-standby-bucket $pkg active",
            )
        }
        if (profile.backgroundData) {
            run(
                "background_data",
                "cmd netpolicy add " +
                    "restrict-background-whitelist $uid",
            )
        }

        if (profile.autoGrantDangerous) {
            dangerousPermissions(profile.packageName)
                .forEach { permission ->
                    run(
                        "grant:$permission",
                        "pm grant --user current $pkg " +
                            shellQuote(permission),
                    )
                }
        }

        if (profile.enabled && profile.anyHookFeature) {
            when (
                val reload =
                    hooks.reload(
                        setOf(profile.packageName),
                    )
            ) {
                is Outcome.Success ->
                    applied += "hook_scope_reload"
                is Outcome.Failure ->
                    notes +=
                        "hook_scope_reload:" +
                            reload.error.code
            }
        }

        return YPowerApplyResult(
            applied = applied,
            notes = notes,
            errors = errors,
        )
    }

    suspend fun diagnose(
        packageName: String,
    ): List<YPowerFinding> {
        val findings = mutableListOf<YPowerFinding>()
        val rootStatus =
            runCatching { root.status() }
                .getOrDefault(CapabilityStatus.Error)
        val hookStatus =
            runCatching { hooks.status() }
                .getOrDefault(CapabilityStatus.Error)

        findings +=
            YPowerFinding(
                id = "root",
                status =
                    if (
                        rootStatus ==
                        CapabilityStatus.Available
                    ) {
                        YPowerFindingStatus.Pass
                    } else {
                        YPowerFindingStatus.Warning
                    },
                summary = rootStatus.name,
                detail = "root",
                recommendation =
                    if (
                        rootStatus ==
                        CapabilityStatus.Available
                    ) {
                        null
                    } else {
                        "grant_root"
                    },
            )
        findings +=
            YPowerFinding(
                id = "hook",
                status =
                    if (
                        hookStatus ==
                        CapabilityStatus.Available
                    ) {
                        YPowerFindingStatus.Pass
                    } else {
                        YPowerFindingStatus.Warning
                    },
                summary = hookStatus.name,
                detail = "hook",
                recommendation =
                    if (
                        hookStatus ==
                        CapabilityStatus.Available
                    ) {
                        null
                    } else {
                        "enable_hooks"
                    },
            )

        val exitInfo =
            runCatching {
                context.getSystemService(
                    ActivityManager::class.java,
                )?.getHistoricalProcessExitReasons(
                    packageName,
                    0,
                    10,
                ).orEmpty()
            }.getOrDefault(emptyList())
        exitInfo.firstOrNull()?.let { info ->
            val abnormal =
                info.reason in
                    setOf(
                        ApplicationExitInfo.REASON_CRASH,
                        ApplicationExitInfo.REASON_CRASH_NATIVE,
                        ApplicationExitInfo.REASON_ANR,
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                    )
            findings +=
                YPowerFinding(
                    id = "exit",
                    status =
                        if (abnormal) {
                            YPowerFindingStatus.Failure
                        } else {
                            YPowerFindingStatus.Detected
                        },
                    summary =
                        "reason=" +
                            info.reason +
                            " status=" +
                            info.status,
                    detail =
                        "pid=" +
                            info.pid +
                            " timestamp=" +
                            info.timestamp +
                            " description=" +
                            info.description.orEmpty(),
                    recommendation =
                        if (abnormal) {
                            "inspect_runtime_trace"
                        } else {
                            null
                        },
                )
        }

        if (rootStatus == CapabilityStatus.Available) {
            val trace =
                when (
                    val outcome =
                        root.execute(
                            RootRequest(
                                command =
                                    "logcat -d -v epoch -t 6000 " +
                                        "| grep -F YPowerTrace " +
                                        "| grep -F " +
                                        shellQuote(packageName) +
                                        " | tail -n 300",
                                timeoutMillis = 10_000L,
                            ),
                        )
                ) {
                    is Outcome.Success ->
                        outcome.value.stdout
                    is Outcome.Failure -> ""
                }
            if (trace.isNotBlank()) {
                findings +=
                    YPowerFinding(
                        id = "runtime_trace",
                        status =
                            YPowerFindingStatus.Detected,
                        summary =
                            trace.lineSequence()
                                .count()
                                .toString(),
                        detail =
                            trace.takeLast(
                                MAX_DIAGNOSTIC_CHARS,
                            ),
                        recommendation =
                            "correlate_latest_exit",
                    )
            }
        }

        return findings
    }

    private fun dangerousPermissions(
        packageName: String,
    ): List<String> =
        runCatching {
            val info =
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_PERMISSIONS,
                )
            info.requestedPermissions
                ?.mapNotNull { permission ->
                    val permissionInfo =
                        runCatching {
                            packageManager
                                .getPermissionInfo(
                                    permission,
                                    0,
                                )
                        }.getOrNull()
                            ?: return@mapNotNull null
                    val base =
                        permissionInfo.protectionLevel and
                            PermissionInfo
                                .PROTECTION_MASK_BASE
                    permission.takeIf {
                        base ==
                            PermissionInfo
                                .PROTECTION_DANGEROUS
                    }
                }.orEmpty()
        }.getOrDefault(emptyList())

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    companion object {
        private const val TAG = "YSuite/YPower"
        private const val MAX_DIAGNOSTIC_CHARS =
            16_000
        val RECOMMENDED_PACKAGES =
            setOf(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.phoenix.read",
                "com.phoenix.read.oversea.gp",
            )
    }
}
