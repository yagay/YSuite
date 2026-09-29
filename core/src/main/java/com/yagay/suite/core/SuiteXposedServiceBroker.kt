package com.yagay.suite.core

import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Sole app-side libxposed service listener for the combined YSuite process.
 *
 * Standalone feature APKs may still register their own listener. Embedded plugins instead call the
 * reflection-friendly [attachFromPlugin] entry and receive framework lifecycle through this broker.
 */
object SuiteXposedServiceBroker : XposedServiceHelper.OnServiceListener {
    private val listeners = CopyOnWriteArraySet<XposedServiceHelper.OnServiceListener>()
    private val pluginListeners = ConcurrentHashMap<String, XposedServiceHelper.OnServiceListener>()

    @Volatile
    private var currentService: XposedService? = null

    @Volatile
    private var appContext: Context? = null

    /** Reflection-friendly plugin registration. No plugin calls registerListener() in YSuite mode. */
    @JvmStatic
    fun attachFromPlugin(pluginId: String, listener: Any): Boolean {
        val typed = listener as? XposedServiceHelper.OnServiceListener ?: return false
        if (typed === this) return false
        attach(pluginId.ifBlank { typed.javaClass.name }, typed)
        return true
    }

    /** YSuite registers exactly one framework service listener, before plugin initialization. */
    @Synchronized
    fun takeOwnership(context: Context) {
        appContext = context.applicationContext
        XposedServiceHelper.registerListener(this)
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "sole LSPosed service broker active; pluginListeners=${pluginListeners.size}",
        )
    }

    /** Host-owned dynamic scope request used by embedded plugins. */
    @JvmStatic
    fun requestScopeFromPlugin(pluginId: String, packages: Array<String>): Boolean {
        val service = currentService ?: return false
        val request = packages
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (request.isEmpty()) return true
        val context = appContext
        return runCatching {
            service.requestScope(request, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    context?.let {
                        SuiteLog.i(
                            it,
                            pluginId.ifBlank { SuiteContract.HOST_MODULE_ID },
                            "scope approved by YSuite; packages=${approved.joinToString(",")}",
                        )
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    context?.let {
                        SuiteLog.e(
                            it,
                            pluginId.ifBlank { SuiteContract.HOST_MODULE_ID },
                            "scope request failed in YSuite; packages=${request.joinToString(",")}; message=$message",
                        )
                    }
                }
            })
            true
        }.getOrElse { error ->
            context?.let {
                SuiteLog.e(
                    it,
                    pluginId.ifBlank { SuiteContract.HOST_MODULE_ID },
                    "scope request exception; packages=${request.joinToString(",")}",
                    error,
                )
            }
            false
        }
    }

    /**
     * Removes scope only through the host. Callers should normally disable a plugin capability
     * instead of removing shared scope; the host checks current scope and applies the operation once.
     */
    @JvmStatic
    fun removeScopeFromPlugin(pluginId: String, packages: Array<String>): Boolean {
        val service = currentService ?: return false
        val request = packages
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        if (request.isEmpty()) return true
        return runCatching {
            service.removeScope(request)
            appContext?.let {
                SuiteLog.i(
                    it,
                    pluginId.ifBlank { SuiteContract.HOST_MODULE_ID },
                    "scope removed by YSuite; packages=${request.joinToString(",")}",
                )
            }
            true
        }.getOrElse { error ->
            appContext?.let {
                SuiteLog.e(
                    it,
                    pluginId.ifBlank { SuiteContract.HOST_MODULE_ID },
                    "scope remove exception; packages=${request.joinToString(",")}",
                    error,
                )
            }
            false
        }
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
            appendLine("pluginListenerCount=${pluginListeners.size}")
            pluginListeners.keys.sorted().forEach { appendLine("pluginListener=$it") }
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

    private fun attach(pluginId: String, listener: XposedServiceHelper.OnServiceListener) {
        if (listener === this) return
        val key = pluginId.ifBlank { listener.javaClass.name }
        val previous = pluginListeners.putIfAbsent(key, listener)
        if (previous != null && previous !== listener) {
            listeners.remove(previous)
            pluginListeners[key] = listener
        }
        if (!listeners.add(listener) && previous === listener) return
        currentService?.let { service ->
            runCatching { listener.onServiceBind(service) }
                .onFailure { failure -> logFailure("replay", listener, failure) }
        }
        appContext?.let {
            SuiteLog.i(it, key, "LSPosed listener attached to YSuite broker")
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
