package com.yagay.yfiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object YFilesHookScopeAdvisor {
    enum class TargetKind { PICKER, CALLER }

    data class Target(
        val packageName: String,
        val label: String,
        val installed: Boolean,
        val kind: TargetKind,
    ) {
        val displayName: String
            get() = if (label.isBlank() || label == packageName) packageName else "$label ($packageName)"
    }

    private val knownPickerPackages = listOf(
        "com.android.documentsui",
        "com.google.android.documentsui",
    )

    private val commonCallerPackages = listOf(
        "com.android.settings",
        "com.android.chrome",
        "com.google.android.apps.chrome",
        "com.google.android.gm",
        "org.mozilla.firefox",
        "org.telegram.messenger",
        "com.whatsapp",
    )

    @Suppress("DEPRECATION")
    fun recommendedTargets(context: Context): List<Target> {
        val pm = context.packageManager
        val kinds = linkedMapOf<String, TargetKind>()
        knownPickerPackages.forEach { kinds[it] = TargetKind.PICKER }

        pickerIntents().forEach { intent ->
            runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
                .getOrDefault(emptyList())
                .forEach { info ->
                    info.activityInfo?.packageName?.takeIf(String::isNotBlank)?.let {
                        kinds[it] = TargetKind.PICKER
                    }
                }
        }

        commonCallerPackages.forEach { packageName ->
            if (packageName !in kinds && isInstalled(pm, packageName)) {
                kinds[packageName] = TargetKind.CALLER
            }
        }

        return kinds.map { (packageName, kind) ->
            val app = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            Target(
                packageName = packageName,
                label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault("") }.orEmpty(),
                installed = app != null,
                kind = kind,
            )
        }.sortedWith(
            compareBy<Target> { it.kind.ordinal }
                .thenByDescending(Target::installed)
                .thenBy { it.displayName.lowercase() },
        )
    }

    private fun pickerIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        },
    )

    @Suppress("DEPRECATION")
    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
}
