package com.yagay.ysuite.feature.ydiag

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.ydiag.api.YDiagApp
import com.yagay.ysuite.feature.ydiag.api.YDiagEvent
import com.yagay.ysuite.feature.ydiag.api.YDiagSeverity
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.HookConfigCoordinator
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import java.time.Instant
import org.json.JSONObject

internal class YDiagRepository(
    private val context: Context,
    private val root: RootGateway,
    private val hooks: HookGateway,
    private val logger: YSuiteLogger,
) {
    private val packageManager = context.packageManager

    fun apps(): List<YDiagApp> =
        packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .map { info ->
                YDiagApp(
                    packageName = info.packageName,
                    label =
                        runCatching {
                            packageManager
                                .getApplicationLabel(info)
                                .toString()
                        }.getOrDefault(info.packageName),
                    uid = info.uid,
                    system =
                        info.flags and
                            ApplicationInfo.FLAG_SYSTEM !=
                            0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

    suspend fun rootStatus(): CapabilityStatus = root.status()

    suspend fun hookStatus(): CapabilityStatus = hooks.status()

    fun liveSessionActive(): Boolean {
        val path =
            context.getSharedPreferences(
                com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.PREFS,
                Context.MODE_PRIVATE,
            ).getString(
                com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.KEY_CURRENT,
                null,
            )
        val prefs = context.getSharedPreferences(
            com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.PREFS,
            Context.MODE_PRIVATE,
        )
        return prefs.getBoolean(
            com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.KEY_ACTIVE,
            false,
        ) && path?.let { java.io.File(it).isDirectory } == true
    }

    fun startLiveSession(
        packageName: String,
        optionIds: Set<String>,
    ) {
        androidx.core.content.ContextCompat.startForegroundService(
            context,
            android.content.Intent(
                context,
                com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService::class.java,
            ).apply {
                action = com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.ACTION_START
                putExtra(
                    com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.EXTRA_PACKAGE,
                    packageName,
                )
                putStringArrayListExtra(
                    com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.EXTRA_OPTIONS,
                    ArrayList(optionIds),
                )
            },
        )
    }

    fun markProblem(note: String = "PROBLEM") {
        context.startService(
            android.content.Intent(
                context,
                com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService::class.java,
            ).apply {
                action = com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.ACTION_MARK
                putExtra(
                    com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.EXTRA_NOTE,
                    note,
                )
            },
        )
    }

    fun stopLiveSession() {
        context.startService(
            android.content.Intent(
                context,
                com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService::class.java,
            ).apply {
                action = com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.ACTION_STOP
            },
        )
    }

    private suspend fun configureHook(
        packageName: String,
        optionIds: Set<String>,
    ): Outcome<Unit> {
        return when (
            val published = HookConfigCoordinator(hooks).publish(
                group = "ydiag",
                values = mapOf(
                    "targets" to packageName,
                    "options" to optionIds.sorted().joinToString("\n"),
                ),
                scopePackages = setOf(packageName),
            )
        ) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> published
        }
    }

    suspend fun collect(
        packageName: String,
        optionIds: Set<String>,
    ): List<YDiagEvent> {
        val result = mutableListOf<YDiagEvent>()
        val hookOptions =
            optionIds.intersect(
                setOf(
                    "hook_health",
                    "lifecycle",
                    "intent",
                    "method_trace",
                    "stack_trace",
                    "webview",
                    "network",
                    "file_io",
                ),
            )
        val hookSetup = if (hookOptions.isNotEmpty()) {
            configureHook(packageName, optionIds)
        } else {
            Outcome.Success(Unit)
        }
        val hookFailure = hookSetup as? Outcome.Failure
        if (hookFailure != null) {
            result += YDiagEvent(
                id = "hook_setup_" + System.currentTimeMillis(),
                timestampMillis = System.currentTimeMillis(),
                optionId = "hook_health",
                severity = YDiagSeverity.Error,
                title = context.getString(R.string.ydiag_hook_setup_failed),
                detail = hookFailure.error.code + ": " + hookFailure.message,
            )
        }
        optionIds.forEach { optionId ->
            if (hookFailure != null && optionId in hookOptions) return@forEach
            val event =
                when (optionId) {
                    "lsposed_status", "hook_health",
                    "lifecycle", "intent", "method_trace",
                    "stack_trace" ->
                        hookEvent(packageName, optionId)
                    else ->
                        commandEvent(
                            optionId = optionId,
                            command =
                                commandFor(
                                    packageName,
                                    optionId,
                                ),
                        )
                }
            result += event
        }
        return result.sortedByDescending {
            it.timestampMillis
        }
    }

    fun export(
        packageName: String,
        enabledOptions: Set<String>,
        events: List<YDiagEvent>,
    ): String =
        YDiagSessionExporter.export(
            context,
            packageName,
            enabledOptions,
            events,
        )

    @Suppress("unused")
    private fun legacyExport(
        packageName: String,
        enabledOptions: Set<String>,
        events: List<YDiagEvent>,
    ): String {
        val timestamp = System.currentTimeMillis()
        val fileName =
            "YDiag-" +
                packageName.replace('.', '_') +
                "-" +
                timestamp +
                ".jsonl"
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName,
                )
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/x-ndjson",
                )
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/YSuite",
                )
            }
        val uri =
            checkNotNull(
                resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ),
            ) {
                "Unable to create export file"
            }

        val header =
            JSONObject().apply {
                put("type", "session")
                put("package", packageName)
                put("createdAt", timestamp)
                put("options", enabledOptions.joinToString(","))
            }
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.appendLine(header.toString())
            events.forEach { event ->
                writer.appendLine(
                    JSONObject().apply {
                        put("type", "event")
                        put("id", event.id)
                        put("timestamp", event.timestampMillis)
                        put("option", event.optionId)
                        put("severity", event.severity.name)
                        put("title", event.title)
                        put("detail", event.detail)
                    }.toString(),
                )
            }
        } ?: error("Unable to open export file")

        return uri.toString()
    }

    private suspend fun hookEvent(
        packageName: String,
        optionId: String,
    ): YDiagEvent {
        val status =
            runCatching { hooks.status() }
                .getOrDefault(CapabilityStatus.Error)
        val detail =
            if (
                status == CapabilityStatus.Available &&
                optionId != "lsposed_status"
            ) {
                val pkg = shellQuote(packageName)
                when (
                    val outcome =
                        root.execute(
                            RootRequest(
                                command =
                                    "logcat -d -v threadtime -t 1200 2>/dev/null " +
                                        "| grep -F 'YDiag.Hook' | grep -F " +
                                        pkg +
                                        " | tail -n 400",
                                timeoutMillis = 8_000L,
                            ),
                        )
                ) {
                    is Outcome.Success ->
                        outcome.value.stdout.ifBlank {
                            "hook_ready_no_events"
                        }
                    is Outcome.Failure ->
                        "hook_log_unavailable:" +
                            outcome.error.code
                }
            } else {
                "hook_status:" + status.name
            }
        return YDiagEvent(
            id = eventId(optionId),
            timestampMillis = System.currentTimeMillis(),
            optionId = optionId,
            severity =
                if (status == CapabilityStatus.Available) {
                    YDiagSeverity.Info
                } else {
                    YDiagSeverity.Warning
                },
            title = optionId,
            detail = detail,
        )
    }

    private suspend fun commandEvent(
        optionId: String,
        command: String?,
    ): YDiagEvent {
        if (command == null) {
            return YDiagEvent(
                id = eventId(optionId),
                timestampMillis = System.currentTimeMillis(),
                optionId = optionId,
                severity = YDiagSeverity.Warning,
                title = optionId,
                detail = "Collector is not available through the current shared platform API.",
            )
        }
        val output =
            when (
                val outcome =
                    root.execute(
                        RootRequest(
                            command = command,
                            timeoutMillis =
                                timeoutFor(optionId),
                        ),
                    )
            ) {
                is Outcome.Success ->
                    buildString {
                        append(outcome.value.stdout)
                        if (outcome.value.stderr.isNotBlank()) {
                            append("\n[stderr]\n")
                            append(outcome.value.stderr)
                        }
                    }
                is Outcome.Failure -> {
                    logger.error(
                        TAG,
                        "Collector failed: $optionId",
                        outcome.error.cause,
                    )
                    return YDiagEvent(
                        id = eventId(optionId),
                        timestampMillis =
                            System.currentTimeMillis(),
                        optionId = optionId,
                        severity = YDiagSeverity.Error,
                        title = optionId,
                        detail =
                            outcome.error.code +
                                ": " +
                                outcome.error.message,
                    )
                }
            }
        val limited =
            if (output.length > MAX_DETAIL_CHARS) {
                output.takeLast(MAX_DETAIL_CHARS)
            } else {
                output
            }
        return YDiagEvent(
            id = eventId(optionId),
            timestampMillis = System.currentTimeMillis(),
            optionId = optionId,
            severity = severityOf(limited),
            title = optionId,
            detail = limited.ifBlank { "(no output)" },
        )
    }

    private fun commandFor(
        packageName: String,
        optionId: String,
    ): String? {
        val pkg = shellQuote(packageName)
        val pid =
            "$(pidof $pkg 2>/dev/null | awk '{print $1}')"
        return when (optionId) {
            "logcat" ->
                "pid=$pid; if [ -n \"\$pid\" ]; then logcat -d --pid=\$pid -v threadtime -t 500; " +
                    "else logcat -d -v threadtime -t 500 | grep -F $pkg; fi"
            "crash", "exit_info" ->
                "dumpsys activity exit-info $pkg 2>/dev/null | head -n 400"
            "anr" ->
                "ls -lt /data/anr 2>/dev/null | head -n 50"
            "events" ->
                "logcat -d -b events -v threadtime -t 1000 2>/dev/null | " +
                    "grep -E 'am_|wm_|activity|proc|crash' | tail -n 350"
            "process" ->
                "pid=$pid; echo pid=\$pid; ps -A 2>/dev/null | grep -F $pkg; " +
                    "[ -n \"\$pid\" ] && cat /proc/\$pid/status 2>/dev/null || true"
            "webview" ->
                "dumpsys webviewupdate 2>/dev/null | head -n 300"
            "network" ->
                "uid=$(dumpsys package $pkg 2>/dev/null | grep -m1 userId= | cut -d= -f2 | tr -dc '0-9'); " +
                    "echo uid=\$uid; dumpsys netstats detail 2>/dev/null | grep -F \"uid=\$uid\" | head -n 300"
            "file_io" ->
                "pid=$pid; [ -n \"\$pid\" ] && { echo /proc/\$pid/fd; ls -l /proc/\$pid/fd 2>/dev/null | head -n 300; } || true"
            "binder" ->
                "dumpsys binder_calls_stats 2>/dev/null | head -n 400"
            "memory" ->
                "dumpsys meminfo $pkg 2>/dev/null | head -n 400"
            "perfetto" ->
                "trace=/data/local/tmp/ydiag-command-$.perfetto-trace; " +
                    "perfetto -o \$trace -t 5s sched freq idle am wm gfx view binder_driver hal dalvik 2>&1; " +
                    "echo TRACE=\$trace; ls -lh \$trace 2>/dev/null"
            "kernel" ->
                "dmesg 2>/dev/null | tail -n 300"
            "selinux" ->
                "getenforce; dmesg 2>/dev/null | grep -i 'avc:' | tail -n 200"
            "root_module" ->
                "ls -la /data/adb/modules 2>/dev/null; " +
                    "find /data/adb/modules -maxdepth 2 -name module.prop -type f -print -exec cat {} \\; 2>/dev/null | head -n 400"
            "lsposed_log" ->
                "for d in /data/adb/lspd/log /data/adb/modules/zygisk_lsposed/log; do " +
                    "[ -d \"\$d\" ] && { echo \"== \$d ==\"; ls -lt \"\$d\" | head -n 30; }; done"
            "tombstone", "native" ->
                "ls -lt /data/tombstones 2>/dev/null | head -n 80"
            else -> null
        }
    }

    private fun timeoutFor(optionId: String): Long =
        if (optionId in setOf("binder", "perfetto")) {
            15_000L
        } else {
            8_000L
        }

    private fun severityOf(value: String): YDiagSeverity {
        val lower = value.lowercase()
        return when {
            "fatal exception" in lower ||
                "fatal signal" in lower ->
                YDiagSeverity.Fatal
            " exception" in lower ||
                "error" in lower ||
                " anr " in lower ->
                YDiagSeverity.Error
            "warn" in lower ||
                "avc:" in lower ->
                YDiagSeverity.Warning
            else -> YDiagSeverity.Info
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun eventId(optionId: String): String =
        optionId +
            "-" +
            Instant.now().toEpochMilli() +
            "-" +
            System.nanoTime()

    companion object {
        private const val TAG = "YSuite/YDiag"
        private const val MAX_DETAIL_CHARS = 16_000
    }
}
