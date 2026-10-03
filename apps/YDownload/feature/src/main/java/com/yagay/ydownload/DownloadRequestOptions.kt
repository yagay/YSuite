package com.yagay.ydownload

internal object DownloadRequestOptions {
    private val headerName = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    private val blockedHeaders = setOf(
        "connection",
        "content-length",
        "host",
        "if-range",
        "range",
    )

    fun parseHeaders(raw: String): Result<Map<String, String>> = runCatching {
        if (raw.isBlank()) return@runCatching emptyMap()
        val parsed = linkedMapOf<String, String>()
        raw.lineSequence().forEachIndexed { index, originalLine ->
            val line = originalLine.trim()
            if (line.isBlank()) return@forEachIndexed
            val separator = line.indexOf(':')
            require(separator > 0) { "Invalid header on line ${index + 1}" }
            val name = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(headerName.matches(name)) { "Invalid header name on line ${index + 1}" }
            require(name.lowercase() !in blockedHeaders) { "Reserved header: $name" }
            require(value.isNotBlank()) { "Empty header value on line ${index + 1}" }
            require(value.none { it == '\r' || it == '\n' || it.code < 0x20 && it != '\t' }) {
                "Invalid header value on line ${index + 1}"
            }
            parsed[name] = value
        }
        parsed
    }

    fun normalizeSha256(raw: String): Result<String?> = runCatching {
        val value = raw.trim()
        if (value.isBlank()) return@runCatching null
        require(value.matches(Regex("(?i)[0-9a-f]{64}"))) { "SHA-256 must contain 64 hexadecimal characters" }
        value.lowercase()
    }
}
