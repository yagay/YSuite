package com.yagay.ydiag.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yagay.ydiag.R
import com.yagay.ydiag.YDiagRuntime
import com.yagay.ydiag.data.Preferences
import com.yagay.ydiag.data.SessionStore
import com.yagay.ydiag.data.SessionWriter
import com.yagay.ydiag.diagnostics.IssueDetector
import com.yagay.ydiag.model.Severity
import com.yagay.ydiag.model.TimelineEvent
import com.yagay.ydiag.root.ProcessIdentity
import com.yagay.ydiag.root.ProcessTracker
import com.yagay.ydiag.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { Preferences(this) }
    private val sessionStore by lazy { SessionStore(this) }
    private val runtime by lazy { YDiagRuntime.get(this) }

    @Volatile private var targets: Set<String> = emptySet()
    @Volatile private var options: Set<String> = emptySet()
    @Volatile private var processes: Map<Int, ProcessIdentity> = emptyMap()

    private var rootAvailable = false
    private var session: SessionWriter? = null
    private var logcatProcess: Process? = null
    private var logJob: Job? = null
    private var processJob: Job? = null
    private var perfetto: PerfettoController? = null
    private val eventCount = AtomicLong()
    private val errorCount = AtomicLong()
    private val warningCount = AtomicLong()
    private val recentEvents = ArrayDeque<TimelineEvent>()
    private val recentIssues = ArrayDeque<com.yagay.ydiag.model.Issue>()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
        rootAvailable = RootShell.isAvailable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC -> sync(
                intent.getStringArrayListExtra(EXTRA_PACKAGES)?.toSet().orEmpty(),
                intent.getStringArrayListExtra(EXTRA_OPTIONS)?.toSet().orEmpty(),
            )
            ACTION_MARK -> markProblem()
            ACTION_STOP -> stopMonitoring()
            else -> sync(preferences.selectedPackages, preferences.enabledOptions)
        }
        return START_STICKY
    }

    private fun sync(newTargets: Set<String>, newOptions: Set<String>) {
        if (newTargets.isEmpty()) {
            stopMonitoring()
            return
        }
        val previousOptions = options
        targets = newTargets
        options = newOptions
        preferences.selectedPackages = newTargets
        preferences.enabledOptions = newOptions

        if (session == null) {
            sessionStore.prune()
            session = sessionStore.start(
                packages = targets,
                options = options,
                rootAvailable = rootAvailable,
                lsposedConnected = runtime.moduleState.value.connected,
                maxBytes = preferences.maxSessionMb.toLong() * 1024L * 1024L,
            )
            eventCount.set(0)
            errorCount.set(0)
            warningCount.set(0)
            recentEvents.clear()
            recentIssues.clear()
            startForeground(NOTIFICATION_ID, notification())
        } else {
            session?.updateTargets(targets, options)
        }

        addSystemEvent(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_7e66b12714fc), targets.joinToString())
        startProcessTracker()
        if (logJob == null || bufferSignature(previousOptions) != bufferSignature(options)) {
            restartLogcat()
        }
        syncPerfetto(previousOptions)
        runtime.syncDeepTracking(targets, options)
        publish(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_0e9808dbeec6))
    }

    private fun restartLogcat() {
        logJob?.cancel()
        runCatching { logcatProcess?.destroy() }
        val buffers = buildList {
            if ("logcat" in options) { add("main"); add("system") }
            if ("crash" in options) add("crash")
            if ("events" in options) add("events")
            if (isEmpty()) add("main")
        }.distinct()
        val args = buffers.joinToString(" ") { "-b $it" }
        logJob = scope.launch {
            try {
                val process = if (rootAvailable) {
                    RootShell.start("logcat -v epoch $args")
                } else {
                    ProcessBuilder("logcat", "-v", "epoch", "-b", "main").redirectErrorStream(true).start()
                }
                logcatProcess = process
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (!isActive) return@forEach
                        consumeLine(line)
                    }
                }
            } catch (failure: Throwable) {
                addSystemIssue(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_0527e270df25), failure.toString())
            }
        }
    }

    private fun startProcessTracker() {
        if (processJob?.isActive == true) return
        processJob = scope.launch {
            while (isActive) {
                if (rootAvailable) {
                    val snapshot = ProcessTracker.snapshot(targets)
                    processes = snapshot
                    session?.appendProcessSnapshot(snapshot.values.joinToString("\n") {
                        "${it.pid}\t${it.uid}\t${it.name}\t${it.packageName}"
                    })
                    publish(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_0e9808dbeec6))
                }
                delay(2500)
            }
        }
    }

    private fun syncPerfetto(previousOptions: Set<String>) {
        if (!rootAvailable || session == null) return
        val wasEnabled = "perfetto" in previousOptions
        val enabled = "perfetto" in options
        if (enabled && !wasEnabled && perfetto == null) {
            val controller = PerfettoController(requireNotNull(session).directory, applicationInfo.uid)
            if (controller.start()) {
                perfetto = controller
                addSystemEvent(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_d7d211f60f7b), com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_035d88bc1f6b))
            } else {
                addSystemIssue(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_024581fdef17), com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_b869274a1104))
            }
        } else if (!enabled && wasEnabled) {
            collectPerfetto(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_f1e0591e5bc4))
        }
    }

    private fun collectPerfetto(reason: String) {
        val controller = perfetto ?: return
        perfetto = null
        val file = controller.stopAndCollect()
        if (file != null) {
            addSystemEvent(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_526c9a84edb4), "$reason · ${file.name} · ${file.length()} bytes")
        } else {
            addSystemIssue(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_4a568b621425), reason)
        }
    }

    private fun consumeLine(line: String) {
        val parsed = LogParser.parse(line)
        val (relevant, packageName) = LogParser.relevant(parsed, targets, processes)
        if (!relevant) return

        val processName = parsed.pid?.let { processes[it]?.name }
        val event = LogParser.event(parsed, packageName, processName)
        session?.appendRaw(line)
        session?.appendTimeline(event)
        rememberEvent(event)

        val detection = IssueDetector.detect(line, parsed.timestamp, event.id)
        if (detection != null) {
            session?.appendIssue(detection.issue)
            rememberIssue(detection.issue)
            publish(detection.issue.title)
        }

        val count = eventCount.incrementAndGet()
        if (count % 20L == 0L) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            if (detection == null) publish(event.title)
        }
    }

    private fun markProblem() {
        val now = System.currentTimeMillis()
        session?.markProblem(now)
        val event = TimelineEvent(
            id = "MARK-$now",
            timestamp = now,
            source = "YDIAG",
            severity = Severity.WARNING.name,
            category = "problem_marker",
            title = com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_530aaa21e71d),
            detail = com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_aea161857cf0),
        )
        session?.appendTimeline(event)
        rememberEvent(event)
        if ("perfetto" in options) collectPerfetto(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_547dc83b27e6))
        publish(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_f2d617104aa8))
    }

    private fun addSystemEvent(title: String, detail: String) {
        val now = System.currentTimeMillis()
        val event = TimelineEvent(
            id = "SYS-$now",
            timestamp = now,
            source = "YDIAG",
            severity = Severity.INFO.name,
            category = "monitor",
            title = title,
            detail = detail,
        )
        session?.appendTimeline(event)
        rememberEvent(event)
    }

    private fun addSystemIssue(title: String, detail: String) {
        val now = System.currentTimeMillis()
        val issue = com.yagay.ydiag.model.Issue(
            id = "YDIAG-$now",
            timestamp = now,
            severity = Severity.ERROR.name,
            category = "collector",
            title = title,
            detail = detail,
        )
        session?.appendIssue(issue)
        rememberIssue(issue)
        publish(title)
    }

    private fun rememberEvent(event: TimelineEvent) {
        synchronized(recentEvents) {
            recentEvents.addFirst(event)
            while (recentEvents.size > 120) recentEvents.removeLast()
        }
    }

    private fun rememberIssue(issue: com.yagay.ydiag.model.Issue) {
        if (issue.severity == Severity.WARNING.name) warningCount.incrementAndGet()
        else errorCount.incrementAndGet()
        synchronized(recentIssues) {
            recentIssues.addFirst(issue)
            while (recentIssues.size > 80) recentIssues.removeLast()
        }
    }

    private fun publish(message: String) {
        val events = synchronized(recentEvents) { recentEvents.toList() }
        val issues = synchronized(recentIssues) { recentIssues.toList() }
        _state.value = MonitorState(
            running = session != null,
            rootAvailable = rootAvailable,
            sessionId = session?.directory?.name,
            targetPackages = targets,
            processCount = processes.size,
            eventCount = eventCount.get(),
            errorCount = errorCount.get(),
            warningCount = warningCount.get(),
            lastMessage = message,
            recentEvents = events,
            recentIssues = issues,
        )
    }

    private fun prepareExportInternal() {
        if ("perfetto" in options) collectPerfetto(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_8c8d3191fffc))
        session?.flush()
    }

    private fun stopMonitoring() {
        collectPerfetto(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_0ec8abf4b4a8))
        runtime.syncDeepTracking(emptySet(), options)
        targets = emptySet()
        preferences.selectedPackages = emptySet()
        logJob?.cancel(); logJob = null
        processJob?.cancel(); processJob = null
        runCatching { logcatProcess?.destroy() }
        logcatProcess = null
        session?.close()
        session = null
        processes = emptyMap()
        publish(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_590414cd2064))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_ydiag)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text, targets.size, eventCount.get()))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun bufferSignature(value: Set<String>): String =
        listOf("logcat","crash","events").filter { it in value }.joinToString(",")

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        runCatching { collectPerfetto(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_1b885f2b7ece)) }
        runCatching { session?.close() }
        runCatching { logcatProcess?.destroy() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SYNC = "com.yagay.ydiag.SYNC"
        const val ACTION_MARK = "com.yagay.ydiag.MARK"
        const val ACTION_STOP = "com.yagay.ydiag.STOP"
        const val EXTRA_PACKAGES = "packages"
        const val EXTRA_OPTIONS = "options"
        private const val CHANNEL_ID = "ydiag_monitor"
        private const val NOTIFICATION_ID = 7301

        @Volatile private var activeInstance: MonitorService? = null
        private val _state = MutableStateFlow(MonitorState())
        val state: StateFlow<MonitorState> = _state

        fun prepareForExport() {
            activeInstance?.prepareExportInternal()
        }
    }
}
