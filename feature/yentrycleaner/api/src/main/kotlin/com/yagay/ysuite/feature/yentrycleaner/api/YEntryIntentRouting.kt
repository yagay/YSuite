package com.yagay.ysuite.feature.yentrycleaner.api

/**
 * Pure routing contract shared by the app's discovery rules and the system resolver hook.
 * Keep multiple-share and process-text isolated from single-share candidate rules.
 */
object YEntryIntentRouting {
    const val SEND = "android.intent.action.SEND"
    const val SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    const val PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
    const val VIEW = "android.intent.action.VIEW"

    fun surface(
        action: String?,
        mimeType: String?,
        scheme: String?,
    ): YEntrySurface? {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val normalizedScheme = scheme.orEmpty().lowercase()
        return when {
            action == SEND_MULTIPLE -> YEntrySurface.ShareMultiple
            action == PROCESS_TEXT -> YEntrySurface.ProcessText
            action == SEND && mime.startsWith("image/") -> YEntrySurface.ShareImage
            action == SEND -> YEntrySurface.ShareText
            action != VIEW -> null
            normalizedScheme == "http" || normalizedScheme == "https" ->
                if (mime in setOf("", "*/*", "text/html", "application/xhtml+xml")) {
                    YEntrySurface.Browser
                } else {
                    YEntrySurface.Open
                }
            normalizedScheme == "file" || normalizedScheme == "content" ->
                YEntrySurface.Open
            normalizedScheme in setOf("magnet", "geo", "mailto", "tel", "sms", "smsto") ->
                YEntrySurface.Open
            normalizedScheme.isBlank() && mime.isNotBlank() ->
                YEntrySurface.Open
            else -> null
        }
    }

    fun qualifier(
        surface: YEntrySurface,
        mimeType: String?,
        host: String?,
        scheme: String? = null,
    ): String = when (surface) {
        YEntrySurface.Browser ->
            host.orEmpty().lowercase().removePrefix("www.").ifBlank { "*" }
        YEntrySurface.ShareMultiple -> "*"
        YEntrySurface.ShareText, YEntrySurface.ShareImage,
        YEntrySurface.ProcessText ->
            mimeType.orEmpty().substringBefore(';').trim().lowercase().ifBlank { "*" }
        YEntrySurface.Open ->
            mimeType.orEmpty().substringBefore(';').trim().lowercase()
                .ifBlank {
                    scheme?.lowercase()?.takeIf { it !in setOf("file", "content", "http", "https") }
                        ?.let { "scheme:" + it } ?: "*"
                }
        else -> "*"
    }
}
