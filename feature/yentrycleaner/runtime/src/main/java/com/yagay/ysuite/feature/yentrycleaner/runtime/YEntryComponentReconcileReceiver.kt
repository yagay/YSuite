package com.yagay.ysuite.feature.yentrycleaner.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class YEntryComponentReconcileReceiver :
    BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val reason =
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED ->
                    "boot_completed"
                Intent.ACTION_USER_UNLOCKED ->
                    "user_unlocked"
                Intent.ACTION_MY_PACKAGE_REPLACED ->
                    "manager_replaced"
                Intent.ACTION_PACKAGE_ADDED ->
                    "package_added"
                Intent.ACTION_PACKAGE_REPLACED ->
                    "package_replaced"
                Intent.ACTION_PACKAGE_REMOVED ->
                    return
                else -> return
            }
        YEntryComponentReconcileJobService
            .schedule(
                context,
                reason,
            )
    }
}
