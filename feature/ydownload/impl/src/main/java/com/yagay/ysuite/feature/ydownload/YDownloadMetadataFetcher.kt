package com.yagay.ysuite.feature.ydownload

import com.yagay.ysuite.feature.ydownload.api.YDownloadMetadata
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class YDownloadMetadataFetcher(
    private val client: OkHttpClient,
) {
    suspend fun fetch(
        url: String,
        referer: String?,
        userAgent: String?,
        cookies: String?,
        username: String?,
        password: String?,
        customHeaders: Map<String, String> = emptyMap(),
    ): Result<YDownloadMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val head = Request.Builder()
                    .url(url)
                    .head()
                    .applyHeaders(
                        referer,
                        userAgent,
                        cookies,
                        username,
                        password,
                        customHeaders,
                    )
                    .build()

                client.newCall(head).execute().use { response ->
                    if (response.isSuccessful) {
                        return@runCatching response.toMetadata(url)
                    }
                }

                val range = Request.Builder()
                    .url(url)
                    .get()
                    .header("Range", "bytes=0-0")
                    .applyHeaders(
                        referer,
                        userAgent,
                        cookies,
                        username,
                        password,
                        customHeaders,
                    )
                    .build()

                client.newCall(range).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP " + response.code)
                    }
                    response.toMetadata(url)
                }
            }
        }

    private fun okhttp3.Response.toMetadata(
        url: String,
    ): YDownloadMetadata {
        val contentRange = header("Content-Range")
        val totalFromRange =
            contentRange
                ?.substringAfterLast('/')
                ?.toLongOrNull()
        val total =
            totalFromRange
                ?: body?.contentLength()
                    ?.takeIf { it >= 0L }
                ?: -1L
        val contentDisposition =
            header("Content-Disposition")
        val fileName =
            fileNameFromDisposition(contentDisposition)
                ?: url.substringBefore('?')
                    .substringAfterLast('/')
                    .takeIf { it.isNotBlank() }
                ?: "download"
        val mime =
            header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                .orEmpty()
        val supportsRanges =
            code == 206 ||
                header("Accept-Ranges")
                    ?.equals("bytes", ignoreCase = true) == true

        return YDownloadMetadata(
            fileName = sanitizeFileName(fileName),
            mimeType = mime,
            totalBytes = total,
            supportsRanges = supportsRanges,
        )
    }

    private fun fileNameFromDisposition(
        value: String?,
    ): String? {
        if (value.isNullOrBlank()) return null

        val utf8 =
            Regex(
                """filename\*=UTF-8''([^;]+)""",
                RegexOption.IGNORE_CASE,
            ).find(value)
                ?.groupValues
                ?.getOrNull(1)
        if (!utf8.isNullOrBlank()) {
            return runCatching {
                URLDecoder.decode(
                    utf8,
                    StandardCharsets.UTF_8.name(),
                )
            }.getOrDefault(utf8)
        }

        return Regex(
            """filename="?([^";]+)"?""",
            RegexOption.IGNORE_CASE,
        ).find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    companion object {
        fun sanitizeFileName(value: String): String =
            value
                .replace(
                    Regex("""[\\/:*?"<>|]"""),
                    "_",
                )
                .trim()
                .ifBlank { "download" }
    }
}

internal fun Request.Builder.applyHeaders(
    referer: String?,
    userAgent: String?,
    cookies: String?,
    username: String?,
    password: String?,
    customHeaders: Map<String, String> = emptyMap(),
): Request.Builder {
    customHeaders.forEach { (name, value) ->
        if (
            name.isNotBlank() &&
            value.isNotBlank()
        ) {
            header(name.trim(), value.trim())
        }
    }
    referer
        ?.takeIf { it.isNotBlank() }
        ?.let { header("Referer", it) }
    userAgent
        ?.takeIf { it.isNotBlank() }
        ?.let { header("User-Agent", it) }
    cookies
        ?.takeIf { it.isNotBlank() }
        ?.let { header("Cookie", it) }

    if (
        !username.isNullOrBlank() &&
        password != null
    ) {
        header(
            "Authorization",
            okhttp3.Credentials.basic(username, password),
        )
    }

    return this
}
