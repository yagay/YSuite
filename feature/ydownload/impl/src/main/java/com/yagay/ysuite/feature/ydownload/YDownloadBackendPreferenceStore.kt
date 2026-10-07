package com.yagay.ysuite.feature.ydownload

import android.content.Context
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend

internal class YDownloadBackendPreferenceStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                "ydownload_backend",
                Context.MODE_PRIVATE,
            )

    fun get(): YDownloadBackend =
        runCatching {
            YDownloadBackend.valueOf(
                preferences.getString(
                    "default_backend",
                    YDownloadBackend.System.name,
                ) ?: YDownloadBackend.System.name,
            )
        }.getOrDefault(
            YDownloadBackend.System,
        )

    fun set(
        backend: YDownloadBackend,
    ) {
        preferences.edit()
            .putString(
                "default_backend",
                backend.name,
            )
            .apply()
    }
}
