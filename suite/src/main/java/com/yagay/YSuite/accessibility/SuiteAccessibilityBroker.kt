package com.yagay.YSuite.accessibility

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
 * The bridge stays inside that feature and remains standalone-buildable. It only needs optional
 * public static methods with these signatures:
 *
 *   onServiceConnected(Context)
 *   onServiceDisconnected(Context)
 *   onAccessibilityEvent(Context, AccessibilityEvent)
 *
 * No feature needs to register another Android AccessibilityService in YSuite.
 */
object SuiteAccessibilityBroker {
    private data class Consumer(
        val featureId: String,
        val className: String,
        val connect: Method?,
        val disconnect: Method?,
        val event: Method?,
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

    fun onServiceConnected(context: Context) {
        initialize(context)
        forEachEnabled(context) { consumer -> invoke(consumer.connect, context) }
    }

    fun onAccessibilityEvent(context: Context, event: AccessibilityEvent) {
        initialize(context)
        forEachEnabled(context) { consumer -> invoke(consumer.event, context, event) }
    }

    fun onServiceDisconnected(context: Context) {
        initialize(context)
        consumers.values.forEach { consumer ->
            invoke(consumer.disconnect, context)
        }
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
            connect = staticMethod(cls, "onServiceConnected", Context::class.java),
            disconnect = staticMethod(cls, "onServiceDisconnected", Context::class.java),
            event = staticMethod(
                cls,
                "onAccessibilityEvent",
                Context::class.java,
                AccessibilityEvent::class.java,
            ),
        )
    }

    private fun staticMethod(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        val method = runCatching { cls.getMethod(name, *params) }.getOrNull() ?: return null
        if (!Modifier.isStatic(method.modifiers)) {
            throw IllegalArgumentException("$name must be public static on ${cls.name}")
        }
        return method
    }

    private fun invoke(method: Method?, vararg args: Any) {
        if (method == null) return
        method.invoke(null, *args)
    }
}
