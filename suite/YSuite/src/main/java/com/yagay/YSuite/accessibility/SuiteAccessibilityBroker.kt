package com.yagay.YSuite.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteLog
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Extensible dispatcher for YSuite's single AccessibilityService.
 *
 * A feature that needs shared accessibility adds an `accessibilityBridgeClassName` to FeatureSpec.
 * The bridge stays inside that feature and remains standalone-buildable.
 *
 * Preferred bridge signatures receive the live AccessibilityService, so future modules can reuse
 * the same grant for gestures, global actions, windows and screenshots:
 *
 *   onServiceConnected(AccessibilityService)
 *   onServiceDisconnected(AccessibilityService)
 *   onAccessibilityEvent(AccessibilityService, AccessibilityEvent)
 *
 * Context-based signatures are also accepted for lightweight consumers such as YNotify:
 *
 *   onServiceConnected(Context)
 *   onServiceDisconnected(Context)
 *   onAccessibilityEvent(Context, AccessibilityEvent)
 */
object SuiteAccessibilityBroker {
    private data class BoundMethod(
        val method: Method,
        val usesService: Boolean,
    )

    private data class Consumer(
        val featureId: String,
        val className: String,
        val connect: BoundMethod?,
        val disconnect: BoundMethod?,
        val event: BoundMethod?,
    )

    private val consumers = ConcurrentHashMap<String, Consumer>()
    @Volatile private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            FeatureRegistry.included().forEach { feature ->
                val bridge = feature.accessibilityBridgeClassName ?: return@forEach
                runCatching { loadConsumer(feature.id, bridge) }
                    .onSuccess { consumer ->
                        consumers[feature.id] = consumer
                        SuiteLog.i(
                            context,
                            "suite",
                            "accessibility consumer registered feature=${feature.id} class=$bridge",
                        )
                    }
                    .onFailure { error ->
                        SuiteLog.e(
                            context,
                            "suite",
                            "accessibility consumer registration failed feature=${feature.id} class=$bridge",
                            error,
                        )
                    }
            }
            initialized = true
        }
    }

    fun onServiceConnected(service: AccessibilityService) {
        initialize(service)
        forEachEnabled(service) { consumer -> invoke(consumer.connect, service) }
    }

    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent) {
        initialize(service)
        forEachEnabled(service) { consumer -> invoke(consumer.event, service, event) }
    }

    fun onServiceDisconnected(service: AccessibilityService) {
        initialize(service)
        consumers.values.forEach { consumer -> invoke(consumer.disconnect, service) }
    }

    fun consumerCount(): Int = consumers.size

    fun registeredFeatureIds(): Set<String> = consumers.keys.toSortedSet()

    private fun forEachEnabled(context: Context, block: (Consumer) -> Unit) {
        val store = FeatureStateStore(context)
        val byId = FeatureRegistry.included().associateBy { it.id }
        consumers.values.forEach { consumer ->
            val feature = byId[consumer.featureId] ?: return@forEach
            if (!store.isEnabled(feature)) return@forEach
            runCatching { block(consumer) }
                .onFailure { error ->
                    SuiteLog.e(
                        context,
                        consumer.featureId,
                        "shared accessibility consumer failed",
                        error,
                    )
                }
        }
    }

    private fun loadConsumer(featureId: String, className: String): Consumer {
        val cls = Class.forName(className)
        return Consumer(
            featureId = featureId,
            className = className,
            connect = preferredMethod(
                cls,
                "onServiceConnected",
                arrayOf(AccessibilityService::class.java),
                arrayOf(Context::class.java),
            ),
            disconnect = preferredMethod(
                cls,
                "onServiceDisconnected",
                arrayOf(AccessibilityService::class.java),
                arrayOf(Context::class.java),
            ),
            event = preferredMethod(
                cls,
                "onAccessibilityEvent",
                arrayOf(AccessibilityService::class.java, AccessibilityEvent::class.java),
                arrayOf(Context::class.java, AccessibilityEvent::class.java),
            ),
        )
    }

    private fun preferredMethod(
        cls: Class<*>,
        name: String,
        serviceParams: Array<Class<*>>,
        contextParams: Array<Class<*>>,
    ): BoundMethod? {
        staticMethod(cls, name, *serviceParams)?.let { return BoundMethod(it, true) }
        staticMethod(cls, name, *contextParams)?.let { return BoundMethod(it, false) }
        return null
    }

    private fun staticMethod(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        val method = runCatching { cls.getMethod(name, *params) }.getOrNull() ?: return null
        if (!Modifier.isStatic(method.modifiers)) {
            throw IllegalArgumentException("$name must be public static on ${cls.name}")
        }
        return method
    }

    private fun invoke(bound: BoundMethod?, service: AccessibilityService, vararg tail: Any) {
        if (bound == null) return
        val first: Any = if (bound.usesService) service else service.applicationContext
        bound.method.invoke(null, first, *tail)
    }
}
