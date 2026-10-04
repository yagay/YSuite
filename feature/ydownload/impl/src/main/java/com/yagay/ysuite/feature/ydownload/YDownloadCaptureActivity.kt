package com.yagay.ysuite.feature.ydownload

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.yagay.ysuite.feature.ydownload.api.YDownloadFeatureContract
import com.yagay.ysuite.ui.YSUITE_EXTRA_INITIAL_FEATURE_ID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class YDownloadCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        incomingUrl(intent)?.let { url ->
            YDownloadIncomingUrlStore.push(
                applicationContext,
                url,
            )
            packageManager
                .getLaunchIntentForPackage(packageName)
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                    putExtra(
                        YSUITE_EXTRA_INITIAL_FEATURE_ID,
                        YDownloadFeatureContract
                            .descriptor.id,
                    )
                }
                ?.let(::startActivity)
        }
        finish()
    }

    private fun incomingUrl(
        intent: Intent,
    ): String? {
        val raw =
            when (intent.action) {
                Intent.ACTION_VIEW ->
                    intent.dataString
                Intent.ACTION_SEND ->
                    intent.getStringExtra(
                        Intent.EXTRA_TEXT,
                    )
                else -> null
            }.orEmpty()

        return URL_PATTERN.find(raw)
            ?.value
            ?.trimEnd(
                '.',
                ',',
                ';',
                ')',
                ']',
            )
    }

    private companion object {
        val URL_PATTERN =
            Regex("""https?://\S+""")
    }
}

internal object YDownloadIncomingUrlStore {
    private const val PREFS =
        "ydownload_incoming"
    private const val KEY_URL =
        "pending_url"

    private val mutableUrls =
        MutableSharedFlow<String>(
            extraBufferCapacity = 4,
        )
    val urls = mutableUrls.asSharedFlow()

    fun push(
        context: Context,
        url: String,
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        ).edit()
            .putString(KEY_URL, url)
            .apply()
        mutableUrls.tryEmit(url)
    }

    fun consume(
        context: Context,
    ): String? {
        val preferences =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE,
            )
        val url =
            preferences.getString(
                KEY_URL,
                null,
            )
        if (url != null) {
            preferences.edit()
                .remove(KEY_URL)
                .apply()
        }
        return url
    }

    fun clearIfMatches(
        context: Context,
        url: String,
    ) {
        val preferences =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE,
            )
        if (
            preferences.getString(
                KEY_URL,
                null,
            ) == url
        ) {
            preferences.edit()
                .remove(KEY_URL)
                .apply()
        }
    }
}
