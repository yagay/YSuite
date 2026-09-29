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
 * Hook/plugin code may target this component explicitly. YSuite then dispatches the intent to the
 * enabled plugin's logical BroadcastReceiver implementation inside the same process. Plugin-level
 * authentication (for example YNotify HMAC/nonces) remains authoritative.
 */
class SuiteBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val event = intent ?: return
        val app = context.applicationContext
        val states = FeatureStateStore(app)
        var matched = false

        FeatureRegistry.included().forEach { feature ->
            if (!states.isEnabled(feature) || feature.ipcReceiverClassNames.isEmpty()) return@forEach
            feature.ipcReceiverClassNames.forEach { className ->
                runCatching {
                    val receiverClass = Class.forName(className)
                    val receiver = receiverClass.getDeclaredConstructor().newInstance()
                    require(receiver is BroadcastReceiver) { "$className must extend BroadcastReceiver" }
                    receiver.onReceive(app, event)
                    matched = true
                }.onFailure { error ->
                    SuiteLog.e(
                        app,
                        feature.id,
                        "IPC plugin dispatch failed; class=$className action=${event.action}",
                        error,
                    )
                }
            }
        }

        if (!matched) {
            SuiteLog.i(
                app,
                SuiteContract.HOST_MODULE_ID,
                "IPC event had no enabled plugin handler; action=${event.action}",
            )
        }
    }
}
