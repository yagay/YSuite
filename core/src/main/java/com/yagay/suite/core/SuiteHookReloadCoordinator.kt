package com.yagay.suite.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-owned post-update Hook reload coordinator.
 *
 * After YSuite is replaced, long-lived reload-safe scoped targets can still be running the old
 * module generation. Instead of rebooting the whole phone, YSuite restarts only those package
 * processes. system_server is never killed here; it keeps the old generation until the next
 * natural device reboot.
 */
object SuiteHookReloadCoordinator {
    private const val PREF = "ysuite_hook_reload"
    private const val K_PENDING_AFTER_REPLACE = "pending_after_package_replace_v1"
    private const val CONNECT_WAIT_MS = 5_000L
    private const val CONNECT_POLL_MS = 100L

    private val reloadablePackages = linkedSetOf(
        "com.google.android.googlequicksearchbox",
        "com.android.systemui",
        "com.android.nfc",
        "com.android.intentresolver",
    )

    private val running = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "YSuite-hook-hot-reload").apply { isDaemon = true }
    }

    private data class Target(
        val processName: String,
        val loadedVersionCode: Long,
        val state: String,
    )

    /** Called from MY_PACKAGE_REPLACED. The pending bit survives process death/races. */
    @JvmStatic
    fun requestAfterPackageReplaced(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ): Boolean {
        val app = context.applicationContext
        preferences(app).edit().putBoolean(K_PENDING_AFTER_REPLACE, true).apply()
        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "package replaced; scheduling selective Hook hot reload",
        )
        return resumePending(app, onComplete)
    }

    /** Safe to call again after user unlock / host initialization. */
    @JvmStatic
    fun resumePending(
        context: Context,
        onComplete: (() -> Unit)? = null,
    ): Boolean {
        val app = context.applicationContext
        if (!preferences(app).getBoolean(K_PENDING_AFTER_REPLACE, false)) {
            onComplete?.invoke()
            return false
        }
        if (!running.compareAndSet(false, true)) {
            onComplete?.invoke()
            return false
        }

        io.execute {
            try {
                perform(app)
            } catch (error: Throwable) {
                SuiteLog.e(
                    app,
                    SuiteContract.HOST_MODULE_ID,
                    "selective Hook hot reload failed",
                    error,
                )
            } finally {
                running.set(false)
                runCatching { onComplete?.invoke() }
            }
        }
        return true
    }

    private fun perform(app: Context) {
        val deadline = System.currentTimeMillis() + CONNECT_WAIT_MS
        while (!SuiteXposedServiceBroker.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(CONNECT_POLL_MS)
        }
        if (!SuiteXposedServiceBroker.isConnected()) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "Hook hot reload deferred; LSPosed service is not connected yet",
            )
            return
        }

        val expectedVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
        }.getOrElse { error ->
            SuiteLog.e(
                app,
                SuiteContract.HOST_MODULE_ID,
                "Hook hot reload cannot resolve host version",
                error,
            )
            return
        }

        val targets = parseTargets(SuiteXposedServiceBroker.diagnosticSnapshot())
        if (targets.isEmpty()) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "Hook hot reload found no running LSPosed targets",
            )
            clearPending(app)
            return
        }

        val stale = targets.filter { it.loadedVersionCode != expectedVersion }
        if (stale.isEmpty()) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "all running Hook targets already use host generation v$expectedVersion",
            )
            clearPending(app)
            return
        }

        val stalePackages = stale
            .asSequence()
            .mapNotNull { packageForProcess(it.processName) }
            .filter { it != app.packageName }
            .distinct()
            .toList()

        val immediate = stalePackages.filter { it in reloadablePackages }
        val deferred = stalePackages.filterNot { it in reloadablePackages }
        val systemServerStale = stale.any { it.processName == "system" || it.processName == "android" }

        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "Hook generation changed; expected=v$expectedVersion immediate=${immediate.joinToString(",")} " +
                "deferred=${deferred.joinToString(",")} systemServerDeferred=$systemServerStale",
        )

        var failed = false
        immediate.forEach { packageName ->
            val result = SuiteProcessManager.reloadPackageProcesses(
                context = app,
                pluginId = SuiteContract.HOST_MODULE_ID,
                packageName = packageName,
                timeoutSeconds = 12L,
            )
            if (!result.success) failed = true
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "Hook target hot reload package=$packageName success=${result.success} " +
                    "killed=${result.killedCount} detail=${result.detail}",
            )
        }

        // Ordinary app targets are intentionally not force-killed. They load the new module the next
        // time Android naturally recreates them. system_server is also never killed by an app update.
        if (deferred.isNotEmpty()) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "Hook targets deferred to natural process restart: ${deferred.joinToString(",")}",
            )
        }
        if (systemServerStale) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "system_server keeps the previous Hook generation until the next natural device reboot; " +
                    "YSuite does not force a phone reboot",
            )
        }

        if (!failed) clearPending(app)
    }

    private fun parseTargets(snapshot: String): List<Target> {
        if (snapshot.isBlank()) return emptyList()
        val out = ArrayList<Target>()
        snapshot.lineSequence().forEach { line ->
            if (!line.startsWith("target=")) return@forEach
            val columns = line.split('\t')
            val processName = columns
                .firstOrNull()
                ?.removePrefix("target=")
                ?.trim()
                .orEmpty()
            if (processName.isBlank()) return@forEach
            var state = "UNKNOWN"
            var loadedVersion = -1L
            columns.drop(1).forEach { column ->
                when {
                    column.startsWith("state=") -> state = column.removePrefix("state=").trim()
                    column.startsWith("loadedVersionCode=") -> {
                        loadedVersion = column.removePrefix("loadedVersionCode=").trim().toLongOrNull() ?: -1L
                    }
                }
            }
            if (loadedVersion >= 0L) out += Target(processName, loadedVersion, state)
        }
        return out
    }

    private fun packageForProcess(processName: String): String? {
        val packageName = processName.substringBefore(':').trim()
        if (packageName.length !in 3..255 || !packageName.contains('.')) return null
        if (!packageName.all { it.isLetterOrDigit() || it == '.' || it == '_' }) return null
        return packageName
    }

    private fun clearPending(context: Context) {
        preferences(context).edit().putBoolean(K_PENDING_AFTER_REPLACE, false).apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
