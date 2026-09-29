package com.yagay.YSuite.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.SuiteContract
import com.yagay.suite.core.SuiteHookReloadCoordinator
import com.yagay.suite.core.SuiteLog

/** The only boot/package-replaced receiver registered by the combined YSuite host. */
class SuiteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val states = FeatureStateStore(app)

        if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            SuiteHookReloadCoordinator.requestAfterPackageReplaced(app) {
                pending.finish()
            }
        }

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
                SuiteLog.i(app, feature.id, "system lifecycle event dispatched by YSuite; action=$action")
            }.onFailure { error ->
                SuiteLog.e(
                    app,
                    SuiteContract.HOST_MODULE_ID,
                    "system lifecycle plugin failed; feature=${feature.id} class=$className action=$action",
                    error,
                )
            }
        }
    }
}
