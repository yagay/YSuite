package com.yagay.YSuite.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteLog

/** The only BOOT_COMPLETED receiver registered by the combined YSuite host. */
class SuiteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        val states = FeatureStateStore(app)

        FeatureRegistry.included().forEach { feature ->
            val className = feature.bootReceiverClassName ?: return@forEach
            if (!states.isEnabled(feature)) return@forEach

            runCatching {
                val receiverClass = Class.forName(className)
                val receiver = receiverClass.getDeclaredConstructor().newInstance()
                require(receiver is BroadcastReceiver) {
                    "$className must extend BroadcastReceiver"
                }
                receiver.onReceive(app, intent)
            }.onSuccess {
                SuiteLog.i(app, feature.id, "boot event dispatched by YSuite")
            }.onFailure { error ->
                SuiteLog.e(
                    app,
                    SuiteContract.HOST_MODULE_ID,
                    "boot plugin failed; feature=${feature.id} class=$className",
                    error,
                )
            }
        }
    }
}
