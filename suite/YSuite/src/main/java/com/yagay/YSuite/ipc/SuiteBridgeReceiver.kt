package com.yagay.YSuite.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteLog

/**
 * The only exported broadcast IPC receiver owned by the combined YSuite host.
 *
 * Hook/plugin code may target this component explicitly. Exact action ownership comes from
 * config/features.toml, so an incoming event reaches only its declared logical receiver instead of
 * being fanned out across every enabled feature. Plugin-level authentication (for example YNotify
 * HMAC/nonces and YFloat session tokens) remains authoritative inside that receiver.
 */
class SuiteBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val event = intent ?: return
        val app = context.applicationContext
        val action = event.action?.takeIf(String::isNotBlank)
        if (action == null) {
            SuiteLog.i(app, SuiteContract.HOST_MODULE_ID, "IPC event rejected without action")
            return
        }

        val route = FeatureRegistry.included()
            .asSequence()
            .mapNotNull { feature -> feature.ipcRoutes[action]?.let { feature to it } }
            .firstOrNull()

        if (route == null) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "IPC event had no catalog route; action=$action",
            )
            return
        }

        val (feature, className) = route
        if (!FeatureStateStore(app).isEnabled(feature)) {
            SuiteLog.i(app, feature.id, "IPC event ignored while feature disabled; action=$action")
            return
        }

        runCatching {
            val receiverClass = Class.forName(className)
            val receiver = receiverClass.getDeclaredConstructor().newInstance()
            require(receiver is BroadcastReceiver) { "$className must extend BroadcastReceiver" }
            receiver.onReceive(app, event)
        }.onFailure { error ->
            SuiteLog.e(
                app,
                feature.id,
                "IPC plugin dispatch failed; class=$className action=$action",
                error,
            )
        }
    }
}
