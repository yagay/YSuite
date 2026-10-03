package com.yagay.ydownload

import android.content.Context
import android.content.Intent
import android.net.Uri

object YDownloadHookScopeAdvisor {
    data class Candidate(
        val packageName: String,
        val label: String,
    ) {
        val displayName: String
            get() = if (label.isBlank() || label == packageName) packageName else "$label ($packageName)"
    }

    private val excluded = setOf(
        "android",
        "com.android.systemui",
        "com.android.providers.downloads",
        "com.google.android.gms",
        "com.android.vending",
        "com.yagay.ydownload",
        "com.yagay.YSuite",
    )

    /**
     * Browsers are useful candidates because they commonly delegate downloads to DownloadManager.
     * This is guidance only: apps using their own OkHttp/Cronet/native downloader do not benefit
     * from the DownloadManager request patch.
     */
    @Suppress("DEPRECATION")
    fun browserCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return runCatching {
            pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .asSequence()
                .mapNotNull { info ->
                    val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (packageName in excluded) return@mapNotNull null
                    Candidate(
                        packageName = packageName,
                        label = runCatching { info.loadLabel(pm)?.toString().orEmpty() }.getOrDefault(""),
                    )
                }
                .distinctBy(Candidate::packageName)
                .sortedBy { it.label.lowercase().ifBlank { it.packageName.lowercase() } }
                .toList()
        }.getOrDefault(emptyList())
    }
}
