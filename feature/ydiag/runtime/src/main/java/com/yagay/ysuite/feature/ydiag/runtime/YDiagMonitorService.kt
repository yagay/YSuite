package com.yagay.ysuite.feature.ydiag.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class YDiagMonitorService : Service() {
    private val executor = Executors.newScheduledThreadPool(2)
    private var sampler: ScheduledFuture<*>? = null
    private var logcatProcess: Process? = null
    private var sessionDir: File? = null
    private var targetPackage: String = ""
    private var enabledOptions: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "YDiag monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent)
            ACTION_MARK -> markProblem(intent.getStringExtra(EXTRA_NOTE).orEmpty())
            ACTION_STOP -> stopSession()
        }
        return START_NOT_STICKY
    }

    private fun startSession(intent: Intent) {
        if (sessionDir != null) return
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        enabledOptions = intent.getStringArrayListExtra(EXTRA_OPTIONS)?.toSet().orEmpty()
        if (targetPackage.isBlank()) {
            stopSelf()
            return
        }
        val sessionId = System.currentTimeMillis().toString() + "-" + targetPackage.replace('.', '_')
        val dir = File(filesDir, "ydiag/sessions/" + sessionId)
        dir.mkdirs()
        sessionDir = dir
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT, dir.absolutePath)
            .putString(KEY_PACKAGE, targetPackage)
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .apply()
        File(dir, "session.properties").writeText(
            buildString {
                appendLine("package=" + targetPackage)
                appendLine("started=" + System.currentTimeMillis())
                appendLine("options=" + enabledOptions.sorted().joinToString(","))
            },
        )
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("YDiag")
                .setContentText("Monitoring " + targetPackage)
                .setOngoing(true)
                .build(),
        )
        startLogcat(dir)
        sampler = executor.scheduleAtFixedRate(
            { collectSnapshot(dir) },
            0L,
            10L,
            TimeUnit.SECONDS,
        )
        if ("perfetto" in enabledOptions) {
            executor.execute { capturePerfetto(dir) }
        }
        markProblem("SESSION_START")
    }

    private fun startLogcat(dir: File) {
        executor.execute {
            val output = File(dir, "logcat.txt")
            runCatching {
                val process = ProcessBuilder("su", "-c", "logcat -v threadtime")
                    .redirectErrorStream(true)
                    .start()
                logcatProcess = process
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    output.bufferedWriter().use { writer ->
                        lines.forEach { line ->
                            writer.appendLine(line)
                            if (output.length() > 8L * 1024L * 1024L) {
                                writer.flush()
                                rotate(output)
                            }
                        }
                    }
                }
            }.onFailure {
                File(dir, "monitor-errors.txt").appendText("logcat: " + it + "\n")
            }
        }
    }

    private fun rotate(file: File) {
        val old = File(file.parentFile, "logcat.1.txt")
        runCatching { old.delete() }
        runCatching { file.renameTo(old) }
    }

    private fun collectSnapshot(dir: File) {
        val now = System.currentTimeMillis()
        val safe = "'" + targetPackage.replace("'", "'\\''") + "'"
        val command =
            "echo '=== " + now + " ==='; " +
                "pidof " + safe + " 2>/dev/null; " +
                "ps -A 2>/dev/null | grep -F " + safe + " | head -n 20; " +
                "dumpsys meminfo " + safe + " 2>/dev/null | head -n 90"
        val result = shellText(command, 8_000L)
        File(dir, "snapshots.txt").appendText(result + "\n")
    }

    private fun capturePerfetto(dir: File) {
        val remote = "/data/local/tmp/ydiag-" + System.currentTimeMillis() + ".perfetto-trace"
        val command =
            "perfetto -o " + remote +
                " -t 10s sched freq idle am wm gfx view binder_driver hal dalvik 2>&1"
        File(dir, "perfetto.txt").writeText(shellText(command, 20_000L))
        runCatching {
            val process = ProcessBuilder(
                "su",
                "-c",
                "cat " + remote + "; rm -f " + remote,
            ).redirectErrorStream(false).start()
            File(dir, "trace.perfetto-trace").outputStream().use { output ->
                process.inputStream.copyTo(output)
            }
            process.waitFor(10, TimeUnit.SECONDS)
        }.onFailure {
            File(dir, "perfetto.txt").appendText("\ncopy: " + it)
        }
    }

    private fun markProblem(note: String) {
        val dir = sessionDir ?: return
        val safe = note.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .take(500)
        val line =
            "{\"time\":" + System.currentTimeMillis() +
                ",\"type\":\"marker\",\"note\":\"" + safe + "\"}\n"
        File(dir, "timeline.jsonl").appendText(line)
    }

    private fun stopSession() {
        val dir = sessionDir
        sampler?.cancel(true)
        sampler = null
        runCatching { logcatProcess?.destroy() }
        logcatProcess = null
        if (dir != null) {
            collectFinalEvidence(dir)
            File(dir, "session.properties").appendText(
                "ended=" + System.currentTimeMillis() + "\n",
            )
        }
        sessionDir = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun collectFinalEvidence(dir: File) {
        val safe = "'" + targetPackage.replace("'", "'\\''") + "'"
        val commands = linkedMapOf(
            "exit-info.txt" to
                "dumpsys activity exit-info " + safe + " 2>/dev/null | head -n 600",
            "anr.txt" to
                "ls -lt /data/anr 2>/dev/null | head -n 100",
            "tombstones.txt" to
                "ls -lt /data/tombstones 2>/dev/null | head -n 120",
            "selinux.txt" to
                "dmesg 2>/dev/null | grep -i 'avc:' | tail -n 300",
            "kernel.txt" to
                "dmesg 2>/dev/null | tail -n 400",
        )
        commands.forEach { (name, command) ->
            File(dir, name).writeText(shellText(command, 12_000L))
        }
    }

    private fun shellText(command: String, timeoutMs: Long): String =
        runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText().take(1_000_000)
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (process.isAlive) process.destroyForcibly()
            text
        }.getOrElse { "error: " + it }

    override fun onDestroy() {
        sampler?.cancel(true)
        runCatching { logcatProcess?.destroy() }
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.yagay.ysuite.ydiag.START"
        const val ACTION_STOP = "com.yagay.ysuite.ydiag.STOP"
        const val ACTION_MARK = "com.yagay.ysuite.ydiag.MARK"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_OPTIONS = "options"
        const val EXTRA_NOTE = "note"
        const val PREFS = "ydiag_session"
        const val KEY_CURRENT = "current_dir"
        const val KEY_PACKAGE = "package"
        const val KEY_STARTED = "started"
        private const val CHANNEL = "ydiag_monitor"
        private const val NOTIFICATION_ID = 7391
    }
}
