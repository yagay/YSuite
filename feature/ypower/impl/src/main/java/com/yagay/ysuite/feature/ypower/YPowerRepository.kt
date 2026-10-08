package com.yagay.ysuite.feature.ypower

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
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
import org.json.JSONArray
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
                diagnosticSessionId =
                    value.optString(
                        "diagnosticSessionId",
                        "",
                    ),
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
                put(
                    "diagnosticSessionId",
                    profile.diagnosticSessionId,
                )
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
        val rootAvailable =
            root.status() ==
                CapabilityStatus.Available

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
                        errors +=
                            id +
                                ": exit=" +
                                outcome.value.exitCode +
                                " " +
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

        if (rootAvailable) {
            run(
                "doze",
                if (profile.enabled && profile.dozeWhitelist) {
                    "cmd deviceidle whitelist +$pkg"
                } else {
                    "cmd deviceidle whitelist -$pkg"
                },
            )
            if (profile.enabled && profile.backgroundOps) {
                run(
                    "background_ops",
                    "cmd appops set $pkg RUN_IN_BACKGROUND allow && " +
                        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow && " +
                        "cmd appops set $pkg START_FOREGROUND allow",
                )
            } else {
                run(
                    "background_ops_reset",
                    "cmd appops set $pkg RUN_IN_BACKGROUND default && " +
                        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND default",
                )
            }
            if (profile.enabled && profile.standbyActive) {
                run(
                    "standby_active",
                    "am set-inactive $pkg false && " +
                        "am set-standby-bucket $pkg active",
                )
            }
            if (profile.enabled && profile.backgroundData) {
                run(
                    "background_data",
                    "cmd netpolicy add " +
                        "restrict-background-whitelist $uid",
                )
            } else {
                run(
                    "background_data_reset",
                    "cmd netpolicy remove " +
                        "restrict-background-whitelist $uid",
                )
            }
    
            if (profile.enabled && profile.autoGrantDangerous) {
                dangerousPermissions(profile.packageName)
                    .forEach { permission ->
                        run(
                            "grant:$permission",
                            "pm grant --user current $pkg " +
                                shellQuote(permission),
                        )
                    }
            }
    
    
        } else {
            notes += "root_unavailable"
        }

        val hookConfig =
            hooks.writeConfig(
                "ypower",
                "profile:" + profile.packageName,
                hookProfileJson(profile),
            )
        var hookConfigReady = false
        when (hookConfig) {
            is Outcome.Success -> {
                applied += "hook_profile_sync"
                hookConfigReady = true
            }
            is Outcome.Failure -> {
                val failureCode =
                    "hook_profile_sync:" +
                        hookConfig.error.code
                if (
                    profile.enabled &&
                    profile.anyHookFeature
                ) {
                    errors += failureCode
                } else {
                    notes += failureCode
                }
            }
        }

        var hookScopeReady =
            !(
                profile.enabled &&
                    profile.anyHookFeature
                )
        if (
            hookConfigReady &&
            profile.enabled &&
            profile.anyHookFeature
        ) {
            when (
                val reload =
                    hooks.reload(
                        setOf(profile.packageName),
                    )
            ) {
                is Outcome.Success -> {
                    applied += "hook_scope_reload"
                    hookScopeReady = true
                }
                is Outcome.Failure ->
                    errors +=
                        "hook_scope_reload:" +
                            reload.error.code
            }
        }

        if (
            hookConfigReady &&
            hookScopeReady
        ) {
            if (rootAvailable) {
                run(
                    "hook_target_restart",
                    "am force-stop " +
                        pkg +
                        "",
                )
            } else if (
                profile.enabled &&
                profile.anyHookFeature
            ) {
                notes +=
                    "hook_target_restart_required"
            }
        }

        return YPowerApplyResult(
            applied = applied,
            notes = notes,
            errors = errors,
        )
    }

    fun diagnosticSessionState(
        packageName: String,
    ): YPowerDiagnosticSessionState {
        val sessionPrefs =
            context.getSharedPreferences(
                DIAGNOSTIC_PREFS,
                Context.MODE_PRIVATE,
            )
        return YPowerDiagnosticSessionState(
            active =
                sessionPrefs.getBoolean(
                    packageName + ":active",
                    false,
                ),
            sessionId =
                sessionPrefs.getString(
                    packageName + ":session",
                    "",
                ).orEmpty(),
            startedAt =
                sessionPrefs.getLong(
                    packageName + ":started",
                    0L,
                ),
            level =
                runCatching {
                    YPowerDiagnosticLevel.valueOf(
                        sessionPrefs.getString(
                            packageName + ":level",
                            YPowerDiagnosticLevel.Standard.name,
                        ).orEmpty(),
                    )
                }.getOrDefault(
                    YPowerDiagnosticLevel.Standard,
                ),
        )
    }

    suspend fun startDiagnosticSession(
        packageName: String,
        level: YPowerDiagnosticLevel,
    ): YPowerDiagnosticSessionState {
        if (
            root.status() !=
            CapabilityStatus.Available
        ) {
            error("root_unavailable")
        }

        val existing =
            diagnosticSessionState(packageName)
        if (existing.active) return existing

        val original = load(packageName)
        val sessionId =
            packageName +
                "-" +
                java.lang.Long.toHexString(
                    System.currentTimeMillis(),
                )
        val tracing =
            original.copy(
                enabled = true,
                tracePackageScan = true,
                traceFiles = true,
                traceCommands = true,
                traceProperties = true,
                tracePermissions = true,
                traceDebugger = true,
                traceExceptions = true,
                traceSecurityApis = true,
                traceNative =
                    level ==
                        YPowerDiagnosticLevel.Deep,
                traceSyscalls =
                    level ==
                        YPowerDiagnosticLevel.Deep,
                traceStacks =
                    level !=
                        YPowerDiagnosticLevel.Quick,
                diagnosticSessionId = sessionId,
            )

        when (
            val sync =
                hooks.writeConfig(
                    "ypower",
                    "profile:" + packageName,
                    hookProfileJson(tracing),
                )
        ) {
            is Outcome.Failure ->
                error(
                    "hook_profile_sync:" +
                        sync.error.code,
                )
            is Outcome.Success -> Unit
        }
        try {
        when (
            val scope =
                hooks.reload(
                    setOf(packageName),
                )
        ) {
            is Outcome.Failure ->
                error(
                    "hook_scope_reload:" +
                        scope.error.code,
                )
            is Outcome.Success -> Unit
        }

        when (
            val restart =
                root.execute(
                    RootRequest(
                        command =
                            "am force-stop " +
                                shellQuote(
                                    packageName,
                                ) +
                                "",
                        timeoutMillis =
                            8_000L,
                    ),
                )
        ) {
            is Outcome.Failure ->
                error(
                    "target_restart:" +
                        restart.error.code,
                )
            is Outcome.Success ->
                if (
                    restart.value.exitCode !=
                    0
                ) {
                    error(
                        "target_restart_exit:" +
                            restart.value.exitCode,
                    )
                }
        }

        val now = System.currentTimeMillis()
        context.getSharedPreferences(
            DIAGNOSTIC_PREFS,
            Context.MODE_PRIVATE,
        ).edit()
            .putBoolean(
                packageName + ":active",
                true,
            )
            .putString(
                packageName + ":session",
                sessionId,
            )
            .putLong(
                packageName + ":started",
                now,
            )
            .putLong(
                packageName + ":ended",
                0L,
            )
            .putString(
                packageName + ":level",
                level.name,
            )
            .commit().also {
                check(it) { "diagnostic_state_save_failed" }
            }
        } catch (failure: Throwable) {
            val restore = runCatching {
                restoreDiagnosticProfile(packageName)
            }.getOrNull()
            if (restore !is Outcome.Success) {
                error("diagnostic_start_failed_and_profile_restore_failed: " +
                    (failure.message ?: failure.javaClass.simpleName))
            }
            throw failure
        }

        return YPowerDiagnosticSessionState(
            active = true,
            sessionId = sessionId,
            startedAt = now,
            level = level,
        )
    }

    fun launchDiagnosticTarget(
        packageName: String,
    ): Boolean {
        val launch =
            packageManager
                .getLaunchIntentForPackage(
                    packageName,
                )
                ?: return false
        launch.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK,
        )
        context.startActivity(launch)
        return true
    }

    suspend fun finishDiagnosticSession(
        packageName: String,
    ): YPowerDiagnosticResult {
        val state =
            diagnosticSessionState(packageName)
        val endedAt = System.currentTimeMillis()
        val sessionPrefs =
            context.getSharedPreferences(
                DIAGNOSTIC_PREFS,
                Context.MODE_PRIVATE,
            )
        val trace =
            if (
                state.sessionId.isNotBlank() &&
                root.status() ==
                CapabilityStatus.Available
            ) {
                when (
                    val outcome =
                        root.execute(
                            RootRequest(
                                command =
                                    "logcat -d -v epoch -t 20000 2>/dev/null " +
                                        "| grep -F YPowerTrace " +
                                        "| grep -F " +
                                        shellQuote(state.sessionId) +
                                        " | tail -n 2500",
                                timeoutMillis = 15_000L,
                            ),
                        )
                ) {
                    is Outcome.Success ->
                        outcome.value.stdout
                    is Outcome.Failure -> ""
                }
            } else {
                ""
            }

        val records =
            parseDiagnosticTrace(
                trace,
                state.sessionId,
            )
        val exits =
            runCatching {
                context.getSystemService(
                    ActivityManager::class.java,
                )?.getHistoricalProcessExitReasons(
                    packageName,
                    0,
                    20,
                ).orEmpty()
                    .filter {
                        it.timestamp >=
                            state.startedAt -
                            2_000L &&
                            it.timestamp <=
                            endedAt +
                            5_000L
                    }
            }.getOrDefault(emptyList())

        val findings =
            correlateDiagnostic(
                records,
                exits,
            )

        if (
            root.status() ==
            CapabilityStatus.Available
        ) {
            root.execute(
                RootRequest(
                    command =
                        "am force-stop " +
                            shellQuote(packageName) +
                            "",
                    timeoutMillis = 8_000L,
                ),
            )
        }

        when (val restored = restoreDiagnosticProfile(packageName)) {
            is Outcome.Failure -> error(
                "diagnostic_profile_restore_failed:" + restored.error.code,
            )
            is Outcome.Success -> Unit
        }
        check(
            sessionPrefs.edit()
                .putBoolean(packageName + ":active", false)
                .putLong(packageName + ":ended", endedAt)
                .commit(),
        ) { "diagnostic_state_save_failed" }

        val reportUri =
            exportDiagnosticReport(
                packageName = packageName,
                state = state,
                endedAt = endedAt,
                records = records,
                findings = findings,
                exitInfo = exits,
            )
        return YPowerDiagnosticResult(
            findings = findings,
            reportUri = reportUri,
        )
    }

    private suspend fun restoreDiagnosticProfile(
        packageName: String,
    ): Outcome<Unit> {
        val original = load(packageName)
        val written = hooks.writeConfig(
            "ypower",
            "profile:" + packageName,
            hookProfileJson(original.copy(diagnosticSessionId = "")),
        )
        if (written is Outcome.Failure) return written
        return hooks.reload(setOf(packageName))
    }

    private fun parseDiagnosticTrace(
        raw: String,
        sessionId: String,
    ): List<YPowerTraceRecord> =
        raw.lineSequence()
            .mapNotNull { line ->
                val start = line.indexOf('{')
                if (start < 0) {
                    return@mapNotNull null
                }
                runCatching {
                    val value =
                        JSONObject(
                            line.substring(start),
                        )
                    if (
                        value.optString(
                            "sessionId",
                            sessionId,
                        ) !=
                        sessionId
                    ) {
                        return@runCatching null
                    }
                    YPowerTraceRecord(
                        timestamp =
                            value.optLong(
                                "ts",
                                0L,
                            ),
                        type =
                            value.optString(
                                "type",
                                "unknown",
                            ),
                        ruleId =
                            value.optString(
                                "ruleId",
                                "",
                            ),
                        detail =
                            value.optString(
                                "detail",
                                value.optString(
                                    "input",
                                    "",
                                ),
                            ),
                        result =
                            value.optString(
                                "result",
                                "",
                            ),
                        matched =
                            value.optBoolean(
                                "matched",
                                false,
                            ),
                        source =
                            value.optString(
                                "source",
                                "",
                            ),
                        pid =
                            value.optInt(
                                "pid",
                                -1,
                            ),
                        tid =
                            value.optInt(
                                "tid",
                                -1,
                            ),
                        stack =
                            value.optString(
                                "stack",
                                "",
                            ),
                    )
                }.getOrNull()
            }
            .filterNotNull()
            .toList()

    private fun correlateDiagnostic(
        records: List<YPowerTraceRecord>,
        exits: List<ApplicationExitInfo>,
    ): List<YPowerFinding> {
        val result =
            mutableListOf<YPowerFinding>()
        val exitRecords =
            records.filter {
                it.type == "native_exit" ||
                    it.type == "exception"
            }
        val exitInfo =
            exits.maxByOrNull {
                it.timestamp
            }
        val exitTime =
            listOfNotNull(
                exitRecords.maxOfOrNull {
                    it.timestamp
                }?.takeIf { it > 0L },
                exitInfo?.timestamp,
            ).maxOrNull() ?: 0L
        val exitRecord =
            exitRecords.minByOrNull {
                if (exitTime <= 0L) {
                    Long.MAX_VALUE
                } else {
                    kotlin.math.abs(
                        exitTime -
                            it.timestamp,
                    )
                }
            }

        exitInfo?.let { info ->
            val abnormal =
                info.reason in
                    setOf(
                        ApplicationExitInfo
                            .REASON_CRASH,
                        ApplicationExitInfo
                            .REASON_CRASH_NATIVE,
                        ApplicationExitInfo
                            .REASON_ANR,
                        ApplicationExitInfo
                            .REASON_EXCESSIVE_RESOURCE_USAGE,
                    )
            result +=
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
                            " " +
                            info.description.orEmpty(),
                )
        }

        val candidates =
            records
                .filter {
                    it !in exitRecords &&
                        it.type != "module"
                }
                .groupBy {
                    it.ruleId
                        .takeIf(String::isNotBlank)
                        ?: it.type
                }
                .map {
                    (id, group) ->
                    val representative =
                        if (exitTime > 0L) {
                            group.minByOrNull {
                                kotlin.math.abs(
                                    exitTime -
                                        it.timestamp,
                                )
                            } ?: group.last()
                        } else {
                            group.last()
                        }
                    val delta =
                        if (
                            exitTime > 0L &&
                            representative.timestamp >
                            0L
                        ) {
                            kotlin.math.abs(
                                exitTime -
                                    representative
                                        .timestamp,
                            )
                        } else {
                            Long.MAX_VALUE
                        }
                    var score =
                        when {
                            delta <= 100L -> 35
                            delta <= 500L -> 30
                            delta <= 1_500L -> 22
                            delta <= 5_000L -> 14
                            delta <= 15_000L -> 6
                            else -> 0
                        }
                    if (representative.matched) {
                        score += 25
                    }
                    if (
                        exitInfo != null &&
                        representative.pid ==
                        exitInfo.pid
                    ) {
                        score += 5
                    }
                    if (
                        exitRecord != null &&
                        representative.tid >= 0 &&
                        representative.tid ==
                        exitRecord.tid
                    ) {
                        score += 10
                    }
                    val shared =
                        sharedFrames(
                            representative.stack,
                            exitRecord?.stack.orEmpty(),
                        )
                    score +=
                        kotlin.math.min(
                            20,
                            shared * 5,
                        )
                    if (group.size >= 3) {
                        score += 10
                    } else if (group.size >= 2) {
                        score += 7
                    }
                    YPowerScoredTrace(
                        id = id,
                        score =
                            score.coerceAtMost(
                                100,
                            ),
                        count = group.size,
                        record = representative,
                    )
                }
                .sortedByDescending {
                    it.score
                }

        val primary =
            candidates.firstOrNull {
                it.score >= 55
            }
        val secondary =
            candidates.drop(1)
                .firstOrNull {
                    it.score >= 65 &&
                        primary != null &&
                        primary.score -
                        it.score <= 15
                }

        candidates
            .take(40)
            .forEach { item ->
                val rank =
                    when (item) {
                        primary -> "primary"
                        secondary -> "secondary"
                        else -> "observed"
                    }
                result +=
                    YPowerFinding(
                        id = item.id,
                        status =
                            YPowerFindingStatus.Detected,
                        summary =
                            rank +
                                " score=" +
                                item.score +
                                " count=" +
                                item.count,
                        detail =
                            item.record.type +
                                " " +
                                item.record.detail +
                                " result=" +
                                item.record.result +
                                " source=" +
                                item.record.source,
                        recommendation =
                            when (item) {
                                primary ->
                                    "primary_runtime_attribution"
                                secondary ->
                                    "secondary_runtime_attribution"
                                else -> null
                            },
                    )
            }

        if (
            exitTime > 0L &&
            primary == null
        ) {
            result +=
                YPowerFinding(
                    id = "attribution",
                    status =
                        YPowerFindingStatus.Warning,
                    summary =
                        "No sufficiently correlated cause",
                    detail =
                        "An exit was observed, but no runtime detection reached the attribution threshold.",
                )
        }
        return result
    }

    private fun sharedFrames(
        first: String,
        second: String,
    ): Int {
        if (
            first.isBlank() ||
            second.isBlank()
        ) return 0
        fun normalized(
            value: String,
        ): Set<String> =
            value.split(" <- ")
                .map {
                    it.substringBeforeLast(
                        ":",
                        it,
                    ).trim()
                }
                .filter {
                    it.isNotBlank() &&
                        !it.startsWith("java.") &&
                        !it.startsWith("android.") &&
                        !it.startsWith("kotlin.") &&
                        !it.startsWith("dalvik.")
                }.toSet()
        return normalized(first)
            .intersect(normalized(second))
            .size
    }

    private fun exportDiagnosticReport(
        packageName: String,
        state: YPowerDiagnosticSessionState,
        endedAt: Long,
        records: List<YPowerTraceRecord>,
        findings: List<YPowerFinding>,
        exitInfo: List<ApplicationExitInfo>,
    ): String {
        val root =
            JSONObject().apply {
                put("format", "YSuite.YPower.v2")
                put("package", packageName)
                put("sessionId", state.sessionId)
                put("level", state.level.name)
                put("startedAt", state.startedAt)
                put("endedAt", endedAt)
                put(
                    "findings",
                    JSONArray().apply {
                        findings.forEach {
                            finding ->
                            put(
                                JSONObject().apply {
                                    put("id", finding.id)
                                    put(
                                        "status",
                                        finding.status.name,
                                    )
                                    put(
                                        "summary",
                                        finding.summary,
                                    )
                                    put(
                                        "detail",
                                        finding.detail,
                                    )
                                    put(
                                        "recommendation",
                                        finding.recommendation,
                                    )
                                },
                            )
                        }
                    },
                )
                put(
                    "trace",
                    JSONArray().apply {
                        records.forEach {
                            record ->
                            put(
                                JSONObject().apply {
                                    put(
                                        "ts",
                                        record.timestamp,
                                    )
                                    put(
                                        "type",
                                        record.type,
                                    )
                                    put(
                                        "ruleId",
                                        record.ruleId,
                                    )
                                    put(
                                        "detail",
                                        record.detail,
                                    )
                                    put(
                                        "result",
                                        record.result,
                                    )
                                    put(
                                        "matched",
                                        record.matched,
                                    )
                                    put(
                                        "source",
                                        record.source,
                                    )
                                    put(
                                        "pid",
                                        record.pid,
                                    )
                                    put(
                                        "tid",
                                        record.tid,
                                    )
                                    put(
                                        "stack",
                                        record.stack,
                                    )
                                },
                            )
                        }
                    },
                )
                put(
                    "processExits",
                    JSONArray().apply {
                        exitInfo.forEach {
                            info ->
                            put(
                                JSONObject().apply {
                                    put(
                                        "timestamp",
                                        info.timestamp,
                                    )
                                    put(
                                        "reason",
                                        info.reason,
                                    )
                                    put(
                                        "status",
                                        info.status,
                                    )
                                    put(
                                        "pid",
                                        info.pid,
                                    )
                                    put(
                                        "description",
                                        info.description,
                                    )
                                },
                            )
                        }
                    },
                )
            }
        val resolver =
            context.contentResolver
        val uri =
            checkNotNull(
                resolver.insert(
                    MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(
                            MediaStore.MediaColumns
                                .DISPLAY_NAME,
                            "YPower-" +
                                packageName.replace(
                                    Regex(
                                        "[^A-Za-z0-9._-]",
                                    ),
                                    "_",
                                ) +
                                "-" +
                                System.currentTimeMillis() +
                                ".json",
                        )
                        put(
                            MediaStore.MediaColumns
                                .MIME_TYPE,
                            "application/json",
                        )
                        put(
                            MediaStore.MediaColumns
                                .RELATIVE_PATH,
                            Environment
                                .DIRECTORY_DOWNLOADS +
                                "/YSuite/YPower",
                        )
                    },
                ),
            )
        resolver.openOutputStream(uri)
            ?.bufferedWriter()
            ?.use {
                it.write(root.toString(2))
            }
            ?: error(
                "Unable to export YPower report",
            )
        return uri.toString()
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

    private fun hookProfileJson(
        profile: YPowerProfile,
    ): String =
        JSONObject().apply {
            put("enabled", profile.enabled)
            put("simulateSystemApp", profile.simulateSystemApp)
            put("simulatePermissions", profile.simulatePermissions)
            put("tracePackageScan", profile.tracePackageScan)
            put("traceFiles", profile.traceFiles)
            put("traceCommands", profile.traceCommands)
            put("traceProperties", profile.traceProperties)
            put("tracePermissions", profile.tracePermissions)
            put("traceDebugger", profile.traceDebugger)
            put("traceExceptions", profile.traceExceptions)
            put("traceSecurityApis", profile.traceSecurityApis)
            put("traceNative", profile.traceNative)
            put("traceSyscalls", profile.traceSyscalls)
            put("traceStacks", profile.traceStacks)
            put(
                "diagnosticSessionId",
                profile.diagnosticSessionId,
            )
        }.toString()

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
        private const val DIAGNOSTIC_PREFS =
            "ysuite_ypower_diagnostic_session"
        val RECOMMENDED_PACKAGES =
            setOf(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.phoenix.read",
                "com.phoenix.read.oversea.gp",
            )
    }
}


enum class YPowerDiagnosticLevel {
    Quick,
    Standard,
    Deep,
}

internal data class YPowerDiagnosticSessionState(
    val active: Boolean,
    val sessionId: String,
    val startedAt: Long,
    val level: YPowerDiagnosticLevel,
)

internal data class YPowerDiagnosticResult(
    val findings: List<YPowerFinding>,
    val reportUri: String,
)

private data class YPowerTraceRecord(
    val timestamp: Long,
    val type: String,
    val ruleId: String,
    val detail: String,
    val result: String,
    val matched: Boolean,
    val source: String,
    val pid: Int,
    val tid: Int,
    val stack: String,
)

private data class YPowerScoredTrace(
    val id: String,
    val score: Int,
    val count: Int,
    val record: YPowerTraceRecord,
)
