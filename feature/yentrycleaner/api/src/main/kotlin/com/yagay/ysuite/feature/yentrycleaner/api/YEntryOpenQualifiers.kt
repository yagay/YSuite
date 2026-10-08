package com.yagay.ysuite.feature.yentrycleaner.api

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Compatibility keys used for former OpenPreset filtering, including custom extensions. */
object YEntryOpenQualifiers {
    data class CustomDefinition(
        val mimeTypes: Set<String>,
        val extensions: Set<String>,
    )

    val presetNames = setOf(
        "PDF", "WORD", "EXCEL", "POWERPOINT", "EPUB", "APK",
        "TORRENT", "MARKDOWN", "CSV", "JSON", "XML", "SVG",
        "GIF", "IMAGE", "VIDEO", "AUDIO", "TEXT", "ARCHIVE",
        "MAGNET", "GEO", "MAILTO", "TEL", "SMS",
    ) + (1..8).map { "CUSTOM_" + it }

    fun qualifiers(
        mimeType: String?,
        scheme: String?,
        path: String? = null,
        custom: Map<String, CustomDefinition> = emptyMap(),
    ): List<String> {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val protocol = scheme.orEmpty().lowercase()
        val result = linkedSetOf<String>()
        if (mime.isNotEmpty()) result += mime
        if (protocol !in setOf("", "file", "content", "http", "https")) {
            result += "scheme:" + protocol
        }
        // A trustworthy MIME type beats a misleading URI extension.
        val extension = if (mime.isEmpty() || mime in setOf(
            "*/*", "application/octet-stream", "binary/octet-stream", "application/x-download",
        )) fileExtension(path) else ""
        for ((slot, definition) in custom.toSortedMap()) {
            if (slot !in presetNames || !slot.startsWith("CUSTOM_")) continue
            if (definition.mimeTypes.any { it == mime ||
                    (it.endsWith("/*") && mime.startsWith(it.removeSuffix("*"))) } ||
                (extension.isNotEmpty() && extension in definition.extensions)) {
                result += "preset:" + slot
            }
        }
        val preset = fixedPreset(mime, protocol, extension)
        if (preset != null) result += "preset:" + preset
        if ('/' in mime) result += mime.substringBefore('/') + "/*"
        result += "*"
        return result.toList()
    }

    private fun fileExtension(path: String?): String {
        val raw = path.orEmpty().substringBefore('?').substringBefore('#')
        val decoded = if ('%' in raw) runCatching {
            URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(raw) else raw
        return decoded.substringAfterLast('/').substringAfterLast('.', "").lowercase().take(24)
    }

    private fun fixedPreset(mime: String, scheme: String, ext: String): String? {
        when (scheme) {
            "magnet" -> return "MAGNET"
            "geo" -> return "GEO"
            "mailto" -> return "MAILTO"
            "tel" -> return "TEL"
            "sms", "smsto" -> return "SMS"
        }
        return when {
            mime == "application/pdf" || ext == "pdf" -> "PDF"
            mime in setOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") || ext in setOf("doc", "docx") -> "WORD"
            mime in setOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") || ext in setOf("xls", "xlsx") -> "EXCEL"
            mime in setOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation") || ext in setOf("ppt", "pptx") -> "POWERPOINT"
            mime == "application/epub+zip" || ext == "epub" -> "EPUB"
            mime == "application/vnd.android.package-archive" || ext in setOf("apk", "apks", "xapk") -> "APK"
            mime == "application/x-bittorrent" || ext == "torrent" -> "TORRENT"
            mime in setOf("text/markdown", "text/x-markdown") || ext in setOf("md", "markdown") -> "MARKDOWN"
            mime in setOf("text/csv", "application/csv") || ext == "csv" -> "CSV"
            mime in setOf("application/json", "text/json") || mime.endsWith("+json") || ext == "json" -> "JSON"
            mime == "image/svg+xml" || ext == "svg" -> "SVG"
            mime == "image/gif" || ext == "gif" -> "GIF"
            mime in setOf("application/xml", "text/xml") || mime.endsWith("+xml") || ext == "xml" -> "XML"
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "avif") -> "IMAGE"
            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov") -> "VIDEO"
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "flac", "aac", "wav", "ogg", "opus") -> "AUDIO"
            mime.startsWith("text/") || ext in setOf("txt", "log", "ini", "conf", "cfg") -> "TEXT"
            mime in setOf("application/zip", "application/x-zip-compressed", "application/vnd.rar", "application/x-rar-compressed", "application/x-7z-compressed", "application/x-tar", "application/gzip") ||
                ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> "ARCHIVE"
            else -> null
        }
    }
}
