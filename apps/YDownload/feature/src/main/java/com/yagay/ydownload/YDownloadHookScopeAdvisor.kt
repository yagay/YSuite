package com.yagay.ydownload

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object YDownloadHookScopeAdvisor {
    enum class Signal { DOWNLOAD_RECEIVER, BROWSER, COMMON_CALLER }

    data class Candidate(
        val packageName: String,
        val label: String,
        val signal: Signal,
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

    private val commonCallerPackages = listOf(
        "com.google.android.apps.nbu.files",
        "com.sec.android.app.myfiles",
        "com.mi.android.globalFileexplorer",
        "com.mi.android.fileexplorer",
        "org.telegram.messenger",
        "com.whatsapp",
        "com.discord",
        "com.reddit.frontpage",
    )

    @Suppress("DEPRECATION")
    fun recommendedCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val signals = linkedMapOf<String, Signal>()

        runCatching {
            pm.queryBroadcastReceivers(
                Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrDefault(emptyList()).forEach { info ->
            info.activityInfo?.packageName
                ?.takeIf { it.isNotBlank() && it !in excluded }
                ?.let { signals[it] = Signal.DOWNLOAD_RECEIVER }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching { pm.queryIntentActivities(browserIntent, PackageManager.MATCH_DEFAULT_ONLY) }
            .getOrDefault(emptyList())
            .forEach { info ->
                val packageName = info.activityInfo?.packageName ?: return@forEach
                if (packageName !in excluded) signals.putIfAbsent(packageName, Signal.BROWSER)
            }

        commonCallerPackages.forEach { packageName ->
            if (packageName !in excluded && packageName !in signals && isInstalled(pm, packageName)) {
                signals[packageName] = Signal.COMMON_CALLER
            }
        }

        return signals.map { (packageName, signal) ->
            val app = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
            Candidate(
                packageName = packageName,
                label = app?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault("") }.orEmpty(),
                signal = signal,
            )
        }.sortedWith(
            compareBy<Candidate> { it.signal.ordinal }
                .thenBy { it.displayName.lowercase() },
        )
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess
}
