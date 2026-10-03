package com.yagay.yfiles

import android.content.Context

object YFilesHookScopeAdvisor {
    data class Target(
        val packageName: String,
        val label: String,
        val installed: Boolean,
    ) {
        val displayName: String
            get() = if (label.isBlank() || label == packageName) packageName else "$label ($packageName)"
    }

    private val documentsUiPackages = listOf(
        "com.android.documentsui",
        "com.google.android.documentsui",
    )

    @Suppress("DEPRECATION")
    fun documentsUiTargets(context: Context): List<Target> {
        val pm = context.packageManager
        return documentsUiPackages.map { packageName ->
            val app = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            Target(
                packageName = packageName,
                label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault("") }.orEmpty(),
                installed = app != null,
            )
        }
    }
}
