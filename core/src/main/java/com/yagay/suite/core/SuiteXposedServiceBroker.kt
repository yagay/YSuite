package com.yagay.suite.core

import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns the single libxposed app-side service listener used by the combined YSuite process and fans
 * framework lifecycle events back out to independently buildable feature runtimes.
 *
 * Feature runtimes keep their standalone registerListener() calls. Their YSuite initializer returns
 * the listener instance to [capture], then the host immediately re-registers this broker as the
 * process owner. No dependency on libxposed private fields is required.
 */
object SuiteXposedServiceBroker : XposedServiceHelper.OnServiceListener {
    private val listeners = CopyOnWriteArraySet<XposedServiceHelper.OnServiceListener>()

    @Volatile
    private var currentService: XposedService? = null

    @Volatile
    private var appContext: Context? = null

    fun capture(context: Context, runtime: Any?) {
        appContext = context.applicationContext
        if (runtime is XposedServiceHelper.OnServiceListener && runtime !== this) {
            attach(runtime)
        }
    }

    /**
     * Always re-register. A feature enabled at runtime may have just replaced the process-global
     * listener with its standalone listener, so the host has to reclaim ownership every time.
     */
    @Synchronized
    fun takeOwnership(context: Context) {
        appContext = context.applicationContext
        XposedServiceHelper.registerListener(this)
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "shared LSPosed broker active; featureListeners=${listeners.size}",
        )
    }

    fun listenerCount(): Int = listeners.size

    /** Host-level framework status; unlike filesystem probes this reflects the real API service. */
    fun isConnected(): Boolean = currentService != null

    fun apiVersion(): Int = runCatching { currentService?.apiVersion ?: 0 }.getOrDefault(0)

    fun statusLabel(): String {
        val service = currentService ?: return "未连接"
        return runCatching {
            val name = service.frameworkName.ifBlank { "LSPosed" }
            val version = service.frameworkVersion
            buildString {
                append(name)
                if (version.isNotBlank()) append(' ').append(version)
                append(" · API ").append(service.apiVersion)
            }
        }.getOrElse { "已连接 · API ${apiVersion()}" }
    }

    /** Export a stable textual snapshot without exposing the process-global service object. */
    fun diagnosticSnapshot(): String {
        val service = currentService
        return buildString {
            appendLine("connected=${service != null}")
            appendLine("listenerCount=${listeners.size}")
            if (service == null) {
                appendLine("status=LSPosed service not connected")
                return@buildString
            }
            runCatching {
                appendLine("frameworkName=${service.frameworkName}")
                appendLine("frameworkVersion=${service.frameworkVersion}")
                appendLine("apiVersion=${service.apiVersion}")
                val scope = service.scope.toList().sorted()
                appendLine("scopeCount=${scope.size}")
                scope.forEach { appendLine("scope=$it") }
                if (service.apiVersion >= 102) {
                    val targets = service.runningTargets.toList()
                    appendLine("runningTargetCount=${targets.size}")
                    targets.forEach { target ->
                        appendLine(
                            "target=${target.processName}\tstate=${target.state.name}\tloadedVersionCode=${target.loadedVersionCode}",
                        )
                    }
                }
            }.onFailure {
                appendLine("snapshotError=${it.javaClass.name}: ${it.message}")
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        // registerListener(this) may replay the same cached framework service after a runtime
        // feature enable. The newly attached feature has already received a targeted replay from
        // attach(), so avoid re-running every feature's bind side effects.
        if (currentService === service) return
        currentService = service
        listeners.forEach { listener ->
            runCatching { listener.onServiceBind(service) }
                .onFailure { failure -> logFailure("bind", listener, failure) }
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (currentService !== service) return
        currentService = null
        listeners.forEach { listener ->
            runCatching { listener.onServiceDied(service) }
                .onFailure { failure -> logFailure("died", listener, failure) }
        }
    }

    private fun attach(listener: XposedServiceHelper.OnServiceListener) {
        if (listener === this || !listeners.add(listener)) return
        currentService?.let { service ->
            runCatching { listener.onServiceBind(service) }
                .onFailure { failure -> logFailure("replay", listener, failure) }
        }
    }

    private fun logFailure(
        event: String,
        listener: XposedServiceHelper.OnServiceListener,
        failure: Throwable,
    ) {
        val context = appContext ?: return
        SuiteLog.e(
            context,
            SuiteContract.HOST_MODULE_ID,
            "LSPosed broker $event dispatch failed; listener=${listener.javaClass.name}",
            failure,
        )
    }
}
