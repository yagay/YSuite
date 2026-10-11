package com.yagay.ydiag.data

import android.content.Context
import com.yagay.suite.api.InstalledAppCatalog
import com.yagay.yui.YView
import com.yagay.ydiag.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val iconSize = YView.listIconSize(context).coerceIn(24, 96)
        InstalledAppCatalog.list(context, 0).asSequence()
            .filter { it.packageName != context.packageName }
            .map { entry ->
                InstalledApp(
                    packageName = entry.packageName,
                    label = entry.label,
                    uid = entry.uid,
                    system = entry.system,
                    enabled = entry.enabled,
                    icon = entry.iconBitmap(context, iconSize),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
