package com.yagay.YSuite.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteLog
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Extensible dispatcher for YSuite's single NotificationListenerService.
 *
 * A feature opts in by declaring `notificationListenerBridgeClassName` in FeatureSpec. The bridge
 * remains inside the feature and is loaded reflectively, so standalone builds do not depend on
 * YSuite. Future modules therefore reuse the same Android notification-listener grant.
 */
object SuiteNotificationListenerBroker {
    private data class Consumer(
        val featureId: String,
        val className: String,
        val connected: Method?,
        val disconnected: Method?,
        val posted: Method?,
        val removed: Method?,
        val ranking: Method?,
    )

    private val consumers = ConcurrentHashMap<String, Consumer>()
    @Volatile private var initialized = false

    fun initialize(host: NotificationListenerService) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            FeatureRegistry.included().forEach { feature ->
                val bridge = feature.notificationListenerBridgeClassName ?: return@forEach
                runCatching { loadConsumer(feature.id, bridge) }
                    .onSuccess { consumer ->
                        consumers[feature.id] = consumer
                        SuiteLog.i(
                            host,
                            "suite",
                            "notification consumer registered feature=${feature.id} class=$bridge",
                        )
                    }
                    .onFailure { error ->
                        SuiteLog.e(
                            host,
                            "suite",
                            "notification consumer registration failed feature=${feature.id} class=$bridge",
                            error,
                        )
                    }
            }
            initialized = true
        }
    }

    fun onListenerConnected(host: NotificationListenerService) {
        initialize(host)
        forEachEnabled(host) { invoke(it.connected, host) }
    }

    fun onListenerDisconnected(host: NotificationListenerService) {
        initialize(host)
        consumers.values.forEach { consumer -> invoke(consumer.disconnected, host) }
    }

    fun onNotificationPosted(
        host: NotificationListenerService,
        sbn: StatusBarNotification?,
        rankingMap: NotificationListenerService.RankingMap?,
    ) {
        initialize(host)
        forEachEnabled(host) { invoke(it.posted, host, sbn, rankingMap) }
    }

    fun onNotificationRemoved(
        host: NotificationListenerService,
        sbn: StatusBarNotification?,
        rankingMap: NotificationListenerService.RankingMap?,
        reason: Int,
    ) {
        initialize(host)
        forEachEnabled(host) { invoke(it.removed, host, sbn, rankingMap, reason) }
    }

    fun onNotificationRankingUpdate(
        host: NotificationListenerService,
        rankingMap: NotificationListenerService.RankingMap?,
    ) {
        initialize(host)
        forEachEnabled(host) { invoke(it.ranking, host, rankingMap) }
    }

    fun consumerCount(): Int = consumers.size

    fun registeredFeatureIds(): Set<String> = consumers.keys.toSortedSet()

    private fun forEachEnabled(
        host: NotificationListenerService,
        block: (Consumer) -> Unit,
    ) {
        val store = FeatureStateStore(host)
        val byId = FeatureRegistry.included().associateBy { it.id }
        consumers.values.forEach { consumer ->
            val feature = byId[consumer.featureId] ?: return@forEach
            if (!store.isEnabled(feature)) return@forEach
            runCatching { block(consumer) }
                .onFailure { error ->
                    SuiteLog.e(
                        host,
                        consumer.featureId,
                        "shared notification listener consumer failed",
                        error,
                    )
                }
        }
    }

    private fun loadConsumer(featureId: String, className: String): Consumer {
        val cls = Class.forName(className)
        val service = NotificationListenerService::class.java
        val sbn = StatusBarNotification::class.java
        val rankingMap = NotificationListenerService.RankingMap::class.java
        return Consumer(
            featureId = featureId,
            className = className,
            connected = staticMethod(cls, "onListenerConnected", service),
            disconnected = staticMethod(cls, "onListenerDisconnected", service),
            posted = staticMethod(cls, "onNotificationPosted", service, sbn, rankingMap),
            removed = staticMethod(
                cls,
                "onNotificationRemoved",
                service,
                sbn,
                rankingMap,
                Int::class.javaPrimitiveType!!,
            ),
            ranking = staticMethod(cls, "onNotificationRankingUpdate", service, rankingMap),
        )
    }

    private fun staticMethod(cls: Class<*>, name: String, vararg params: Class<*>): Method? {
        val method = runCatching { cls.getMethod(name, *params) }.getOrNull() ?: return null
        if (!Modifier.isStatic(method.modifiers)) {
            throw IllegalArgumentException("$name must be public static on ${cls.name}")
        }
        return method
    }

    private fun invoke(method: Method?, vararg args: Any?) {
        if (method == null) return
        method.invoke(null, *args)
    }
}
