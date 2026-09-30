package com.yagay.suite.core

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import java.io.File
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Export-time diagnostic snapshot shared by whole-suite and single-feature exports.
 *
 * This deliberately complements, rather than replaces, feature logs and YDiag sessions. The goal
 * is that every exported ZIP contains enough system context to tell the difference between
 * "nothing happened" and "that evidence source was never collected".
 */
object SuiteDiagnostics {
    private const val MAX_COMMAND_CHARS = 6 * 1024 * 1024

    private data class CommandResult(val code: Int, val output: String, val timedOut: Boolean)

    fun collect(context: Context, modules: Set<String>?): File {
        val app = context.applicationContext
        val root = File(app.filesDir, "suite-logs/_diagnostics")
        root.deleteRecursively()
        root.mkdirs()

        val coverage = mutableListOf<String>()
        val selected = modules ?: FeatureRegistry.included().mapTo(linkedSetOf()) { it.id }

        fun write(name: String, block: () -> String) {
            runCatching { File(root, name).writeText(block()) }
                .onSuccess { coverage += "$name\tOK" }
                .onFailure {
                    File(root, name).writeText("collector failed: ${it.javaClass.name}: ${it.message}\n")
                    coverage += "$name\tFAILED\t${it.javaClass.simpleName}"
                }
        }

        write("README-AI.txt") {
            buildString {
                appendLine("YSuite diagnostic package")
                appendLine("Generated at export time: ${Date()}")
                appendLine("Scope: ${if (modules == null) "whole-suite" else selected.joinToString()}")
                appendLine()
                appendLine("Read coverage.txt first. OK means the evidence source was captured; SKIPPED/FAILED means absence of evidence is not evidence of absence.")
                appendLine("Recommended order: coverage.txt -> runtime.txt -> permissions.txt -> accessibility.txt -> lsposed.txt -> root.txt -> logcat-snapshot.txt -> module-*.txt -> feature logs.")
            }
        }
        write("runtime.txt") { runtimeSnapshot(app, selected) }
        write("permissions.txt") { permissionSnapshot(app) }
        write("components.txt") { componentSnapshot(app) }
        write("accessibility.txt") { accessibilitySnapshot(app) }
        write("special-access.txt") { specialAccessSnapshot(app) }
        write("lsposed.txt") { SuiteXposedServiceBroker.diagnosticSnapshot() }

        val rootAvailable = RootManager.isAvailable(app)
        write("root.txt") {
            if (!rootAvailable) {
                "rootAvailable=false\nRoot-only evidence sources were not collected.\n"
            } else {
                val result = runRoot(
                    "echo '=== id ==='; id; " +
                        "echo '=== selinux ==='; getenforce 2>&1; " +
                        "echo '=== kernel ==='; uname -a; " +
                        "echo '=== su ==='; (su -v 2>&1 || true); " +
                        "echo '=== ksud ==='; (ksud -V 2>&1 || ksud --version 2>&1 || true); " +
                        "echo '=== /data/adb ==='; ls -la /data/adb 2>&1; " +
                        "echo '=== modules ==='; ls -la /data/adb/modules 2>&1",
                    15,
                )
                formatResult(result)
            }
        }

        if (rootAvailable) {
            captureRoot(root, coverage, "package-dump.txt", "dumpsys package ${shellQuote(app.packageName)}", 20)
            captureRoot(root, coverage, "appops.txt", "cmd appops get ${shellQuote(app.packageName)} 2>&1", 15)
            captureRoot(
                root,
                coverage,
                "activity-services.txt",
                "dumpsys activity services ${shellQuote(app.packageName)}; echo; dumpsys activity exit-info ${shellQuote(app.packageName)}",
                20,
            )
            captureRoot(
                root,
                coverage,
                "processes.txt",
                "echo '=== target processes ==='; ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -E 'com\\.yagay\\.YSuite|com\\.yagay\\.(YFloat|YNotify|YPower|YMiniGuard|YNFC|YTaskManager|YEntryCleaner)|com\\.yagay\\.ydiag|com\\.yagay\\.yparam' || true; " +
                    "echo; echo '=== activity processes (filtered) ==='; dumpsys activity processes 2>/dev/null | grep -E -i -C 3 'com\\.yagay|YSuite' | head -n 4000",
                20,
            )
            captureRoot(
                root,
                coverage,
                "system-access.txt",
                "echo '=== secure accessibility ==='; settings get secure enabled_accessibility_services; " +
                    "echo '=== secure notification listeners ==='; settings get secure enabled_notification_listeners; " +
                    "echo '=== accessibility dumpsys ==='; dumpsys accessibility 2>&1 | head -n 6000",
                20,
            )
            captureRoot(
                root,
                coverage,
                "power-state.txt",
                "echo '=== deviceidle ==='; dumpsys deviceidle 2>&1 | head -n 5000; " +
                    "echo '=== power ==='; dumpsys power 2>&1 | head -n 5000",
                20,
            )
            captureRoot(
                root,
                coverage,
                "lsposed-files.txt",
                "echo '=== LSPosed log dir ==='; ls -lt /data/adb/lspd/log 2>/dev/null; " +
                    "for f in /data/adb/lspd/log/*.log; do [ -f \"\$f\" ] || continue; echo; echo \"=== \$f ===\"; tail -n 1800 \"\$f\"; done",
                30,
            )
            captureRoot(
                root,
                coverage,
                "crash-history.txt",
                "echo '=== crash buffer ==='; logcat -d -b crash -v threadtime -t 2500 2>&1; " +
                    "echo; echo '=== ANR files matching YSuite ==='; for f in /data/anr/*; do [ -f \"\$f\" ] || continue; grep -qi -E 'com\\.yagay|YSuite' \"\$f\" 2>/dev/null && { echo \"--- \$f ---\"; tail -n 1200 \"\$f\"; }; done; " +
                    "echo; echo '=== tombstones matching YSuite ==='; for f in /data/tombstones/tombstone_*; do [ -f \"\$f\" ] || continue; grep -qi -E 'com\\.yagay|YSuite' \"\$f\" 2>/dev/null && { echo \"--- \$f ---\"; tail -n 1200 \"\$f\"; }; done",
                35,
            )
            captureRoot(
                root,
                coverage,
                "logcat-snapshot.txt",
                "logcat -d -v threadtime -b main -b system -b crash -b events -t 8000 2>&1",
                30,
            )

            selected.forEach { id -> collectModuleRootEvidence(root, coverage, id, app.packageName) }
        } else {
            listOf(
                "package-dump.txt", "appops.txt", "activity-services.txt", "processes.txt",
                "system-access.txt", "power-state.txt", "lsposed-files.txt", "crash-history.txt",
                "logcat-snapshot.txt",
            ).forEach { coverage += "$it\tSKIPPED\troot unavailable" }
            selected.forEach { id -> coverage += "module-$id.txt\tSKIPPED\troot unavailable" }
        }

        File(root, "coverage.txt").writeText(buildString {
            appendLine("YSuite diagnostic coverage")
            appendLine("scope=${if (modules == null) "whole-suite" else selected.joinToString()}")
            appendLine("rootAvailable=$rootAvailable")
            appendLine()
            coverage.forEach { appendLine(it) }
        })
        return root
    }

    private fun runtimeSnapshot(context: Context, selected: Set<String>): String {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("package=${context.packageName}")
            appendLine("versionName=${info.versionName}")
            appendLine("versionCode=${info.longVersionCode}")
            appendLine("pid=${Process.myPid()}")
            appendLine("uid=${Process.myUid()}")
            appendLine("processUptimeMs=${SystemClock.elapsedRealtime()}")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("brand=${Build.BRAND}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("release=${Build.VERSION.RELEASE}")
            appendLine("securityPatch=${Build.VERSION.SECURITY_PATCH}")
            appendLine("fingerprint=${Build.FINGERPRINT}")
            appendLine("selectedModules=${selected.joinToString()}")
            appendLine("activeFeature=${SuiteCrashTracker.activeFeature(context) ?: "none"}")
            appendLine("filesDir=${context.filesDir.absolutePath}")
            appendLine("cacheDir=${context.cacheDir.absolutePath}")
        }
    }

    @Suppress("DEPRECATION")
    private fun permissionSnapshot(context: Context): String {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty()
        return buildString {
            permissions.sorted().forEach { permission ->
                val granted = pm.checkPermission(permission, context.packageName) == PackageManager.PERMISSION_GRANTED
                appendLine("${if (granted) "GRANTED" else "DENIED"}\t$permission")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun componentSnapshot(context: Context): String {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        fun status(component: ComponentInfo): String {
            val name = ComponentName(component.packageName, component.name)
            val state = runCatching { context.packageManager.getComponentEnabledSetting(name) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            return "${component.javaClass.simpleName}\t${name.flattenToString()}\tmanifestEnabled=${component.enabled}\texported=${component.exported}\tpmState=$state"
        }
        return buildString {
            info.activities.orEmpty().sortedBy { it.name }.forEach { appendLine(status(it)) }
            info.services.orEmpty().sortedBy { it.name }.forEach { appendLine(status(it)) }
            info.receivers.orEmpty().sortedBy { it.name }.forEach { appendLine(status(it)) }
            info.providers.orEmpty().sortedBy { it.name }.forEach { appendLine(status(it)) }
        }
    }

    private fun accessibilitySnapshot(context: Context): String {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val services = runCatching {
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
        }.getOrDefault(emptyList())
        val secure = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        }.getOrDefault("")
        return buildString {
            appendLine("managerEnabled=${manager?.isEnabled}")
            appendLine("secureRaw=$secure")
            appendLine("enabledServices:")
            services.forEach { service ->
                val resolve = service.resolveInfo?.serviceInfo
                appendLine("id=${service.id}\tpkg=${resolve?.packageName}\tclass=${resolve?.name}\tfeedback=${service.feedbackType}\tflags=${service.flags}")
            }
        }
    }

    private fun specialAccessSnapshot(context: Context): String {
        val pm = context.packageManager
        val power = context.getSystemService(PowerManager::class.java)
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val usageMode = runCatching {
            appOps?.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
                ?: AppOpsManager.MODE_ERRORED
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val listeners = runCatching {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        }.getOrDefault("")
        return buildString {
            appendLine("overlay=${Settings.canDrawOverlays(context)}")
            appendLine("postNotifications=$notificationGranted")
            appendLine("usageStatsAppOp=$usageMode")
            appendLine("ignoreBatteryOptimizations=${power?.isIgnoringBatteryOptimizations(context.packageName)}")
            appendLine("notificationListeners=$listeners")
            appendLine("canRequestPackageInstalls=${runCatching { pm.canRequestPackageInstalls() }.getOrDefault(false)}")
        }
    }

    private fun collectModuleRootEvidence(root: File, coverage: MutableList<String>, id: String, hostPackage: String) {
        val command = when (id.lowercase(Locale.ROOT)) {
            "yfloat" ->
                "echo '=== accessibility ==='; dumpsys accessibility 2>&1 | head -n 7000; " +
                    "echo '=== window ==='; dumpsys window windows 2>&1 | grep -E -i -C 5 'com\\.yagay|accessibility|overlay' | head -n 5000; " +
                    "echo '=== appops overlay ==='; cmd appops get ${shellQuote(hostPackage)} SYSTEM_ALERT_WINDOW 2>&1"
            "ynotify" ->
                "echo '=== notification listener setting ==='; settings get secure enabled_notification_listeners; " +
                    "echo '=== notification manager filtered ==='; dumpsys notification 2>&1 | grep -E -i -C 4 'com\\.yagay|YNotify|listener' | head -n 6000"
            "ypower" ->
                "echo '=== deviceidle ==='; dumpsys deviceidle 2>&1 | head -n 5000; " +
                    "echo '=== activity processes ==='; dumpsys activity processes 2>&1 | head -n 9000; " +
                    "echo '=== meminfo host ==='; dumpsys meminfo ${shellQuote(hostPackage)} 2>&1"
            "yminiguard" ->
                "echo '=== activities ==='; dumpsys activity activities 2>&1 | grep -E -i -C 5 'com\\.yagay|PiP|picture.?in.?picture|freeform' | head -n 7000; " +
                    "echo '=== windows ==='; dumpsys window windows 2>&1 | grep -E -i -C 5 'com\\.yagay|freeform|pip' | head -n 7000; " +
                    "echo '=== media session ==='; dumpsys media_session 2>&1 | head -n 6000"
            "ynfc" ->
                "echo '=== nfc ==='; dumpsys nfc 2>&1 | head -n 10000; " +
                    "echo '=== nfc processes ==='; ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -E -i 'nfc|com\\.yagay' || true"
            "ytaskmanager" ->
                "echo '=== activity processes ==='; dumpsys activity processes 2>&1 | head -n 10000; " +
                    "echo '=== procstats ==='; dumpsys procstats --hours 3 2>&1 | head -n 10000; " +
                    "echo '=== meminfo ==='; dumpsys meminfo 2>&1 | head -n 10000"
            "yparam" ->
                "echo '=== display ==='; wm size 2>&1; wm density 2>&1; " +
                    "echo '=== locale ==='; getprop persist.sys.locale; getprop ro.product.locale; " +
                    "echo '=== appops ==='; cmd appops get ${shellQuote(hostPackage)} 2>&1"
            "yentrycleaner" ->
                "echo '=== package host ==='; dumpsys package ${shellQuote(hostPackage)} 2>&1 | head -n 10000; " +
                    "echo '=== resolver settings ==='; dumpsys package 2>&1 | grep -E -i -C 3 'preferred|resolver|domain verification|com\\.yagay' | head -n 8000"
            "ydiag" ->
                "echo '=== exit info ==='; dumpsys activity exit-info ${shellQuote(hostPackage)} 2>&1; " +
                    "echo '=== /data/anr ==='; ls -lt /data/anr 2>&1 | head -n 500; " +
                    "echo '=== /data/tombstones ==='; ls -lt /data/tombstones 2>&1 | head -n 500"
            else -> "echo 'No module-specific collector registered for $id'"
        }
        captureRoot(root, coverage, "module-$id.txt", command, 25)
    }

    private fun captureRoot(
        root: File,
        coverage: MutableList<String>,
        name: String,
        command: String,
        timeoutSeconds: Long,
    ) {
        val result = runCatching { runRoot(command, timeoutSeconds) }.getOrElse {
            File(root, name).writeText("collector failed: ${it.javaClass.name}: ${it.message}\n")
            coverage += "$name\tFAILED\t${it.javaClass.simpleName}"
            return
        }
        File(root, name).writeText(formatResult(result))
        coverage += if (result.code == 0 && !result.timedOut) "$name\tOK"
        else "$name\tFAILED\tcode=${result.code} timeout=${result.timedOut}"
    }

    private fun runRoot(command: String, timeoutSeconds: Long): CommandResult {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < MAX_COMMAND_CHARS) output.appendLine(line)
                    }
                }
            }
        }, "YSuite-diag-reader").apply { isDaemon = true; start() }
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        reader.join(1500)
        if (output.length >= MAX_COMMAND_CHARS) output.appendLine("\n[output truncated at $MAX_COMMAND_CHARS chars]")
        return CommandResult(
            code = if (completed) process.exitValue() else -1,
            output = output.toString(),
            timedOut = !completed,
        )
    }

    private fun formatResult(result: CommandResult): String = buildString {
        appendLine("exitCode=${result.code}")
        appendLine("timedOut=${result.timedOut}")
        appendLine("--- output ---")
        append(result.output)
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
