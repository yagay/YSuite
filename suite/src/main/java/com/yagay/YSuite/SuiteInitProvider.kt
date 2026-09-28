package com.yagay.YSuite

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.yagay.suite.core.FeatureRegistry
import com.yagay.suite.core.FeatureStateStore
import com.yagay.suite.core.RootManager
import com.yagay.suite.core.SuiteLog

class SuiteInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: context ?: return false
        RootManager.configure(appContext)
        val states = FeatureStateStore(appContext)
        FeatureRegistry.included()
            .filter(states::isEnabled)
            .filter { it.runtimeInitializerClassName != null }
            .forEach { feature ->
                runCatching { feature.initialize(appContext) }
                    .onSuccess { SuiteLog.i(appContext, feature.id, "feature initialized") }
                    .onFailure { SuiteLog.e(appContext, feature.id, "feature initialization failed", it) }
            }
        SuiteLog.i(appContext, "suite", "YSuite host initialized; features=${FeatureRegistry.included().size}")
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
