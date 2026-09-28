package com.yagay.suite.core

import android.content.Context
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.lang.reflect.Modifier
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns the single libxposed app-side service listener allowed in the YSuite process and fans
 * service lifecycle events back out to the independently buildable feature runtimes.
 *
 * Feature runtimes may still call registerListener() while they are being initialized because
 * their standalone APKs need that behaviour. YSuite captures each listener immediately after a
 * feature initializer returns and then reclaims final ownership of XposedServiceHelper. This also
 * covers features enabled later from the host UI.
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
        // Some reusable feature initializers intentionally return their host Context instead of
        // their listener singleton (currently YFloat). Capture the listener that the feature just
        // registered before another feature or the broker replaces it.
        currentFrameworkListener()?.let(::attach)
    }

    @Synchronized
    fun takeOwnership(context: Context) {
        appContext = context.applicationContext
        val current = currentFrameworkListener()
        if (current === this) return
        current?.let(::attach)
        XposedServiceHelper.registerListener(this)
        SuiteLog.i(
            context,
            SuiteContract.HOST_MODULE_ID,
            "shared LSPosed broker active; featureListeners=${listeners.size}",
        )
    }

    fun listenerCount(): Int = listeners.size

    override fun onServiceBind(service: XposedService) {
        currentService = service
        listeners.forEach { listener ->
            runCatching { listener.onServiceBind(service) }
                .onFailure { failure -> logFailure("bind", listener, failure) }
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (currentService === service) currentService = null
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

    private fun currentFrameworkListener(): XposedServiceHelper.OnServiceListener? =
        runCatching {
            XposedServiceHelper::class.java.declaredFields
                .asSequence()
                .filter { Modifier.isStatic(it.modifiers) }
                .filter { XposedServiceHelper.OnServiceListener::class.java.isAssignableFrom(it.type) }
                .mapNotNull { field ->
                    field.isAccessible = true
                    field.get(null) as? XposedServiceHelper.OnServiceListener
                }
                .firstOrNull()
        }.getOrNull()

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
