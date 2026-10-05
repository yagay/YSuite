package com.yagay.ysuite.feature.yfiles

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class YDocumentPreviewKind {
    Text,
    Pdf,
    Docx,
    Xlsx,
    Epub,
    Unsupported,
}

data class YDocumentPreview(
    val kind: YDocumentPreviewKind,
    val title: String,
    val text: String = "",
    val imagePath: String? = null,
    val pageCount: Int? = null,
    val truncated: Boolean = false,
)

data class YHexPage(
    val offset: Long,
    val bytes: ByteArray,
    val totalBytes: Long?,
    val editable: Boolean,
) {
    val formatted: String
        get() = buildString {
            var row = 0
            while (row < bytes.size) {
                append(
                    "%08X  ".format(
                        offset + row,
                    ),
                )
                val end =
                    minOf(
                        row + 16,
                        bytes.size,
                    )
                for (index in row until row + 16) {
                    if (index < end) {
                        append(
                            "%02X ".format(
                                bytes[index]
                                    .toInt() and
                                    0xff,
                            ),
                        )
                    } else {
                        append("   ")
                    }
                    if (index == row + 7) {
                        append(' ')
                    }
                }
                append(" |")
                for (index in row until end) {
                    val value =
                        bytes[index]
                            .toInt() and
                            0xff
                    append(
                        if (
                            value in 32..126
                        ) {
                            value.toChar()
                        } else {
                            '.'
                        },
                    )
                }
                append("|\n")
                row = end
            }
        }
}

data class YChecksumSet(
    val values: Map<String, String>,
)

class YFilesPreviewService(
    private val engine: YFilesEngine,
    private val cacheDirectory: File,
) {
    suspend fun preview(
        ref: YFileRef,
        name: String,
    ): Outcome<YDocumentPreview> =
        withContext(Dispatchers.IO) {
            try {
                val lower =
                    name.lowercase()
                when {
                    lower.endsWith(".pdf") ->
                        previewPdf(
                            materialize(
                                ref,
                                suffix = ".pdf",
                            ),
                            name,
                        )
                    lower.endsWith(".docx") ->
                        previewDocx(
                            materialize(
                                ref,
                                suffix = ".docx",
                            ),
                            name,
                        )
                    lower.endsWith(".xlsx") ->
                        previewXlsx(
                            materialize(
                                ref,
                                suffix = ".xlsx",
                            ),
                            name,
                        )
                    lower.endsWith(".epub") ->
                        previewEpub(
                            materialize(
                                ref,
                                suffix = ".epub",
                            ),
                            name,
                        )
                    isTextName(lower) ->
                        previewText(
                            ref,
                            name,
                        )
                    else ->
                        Outcome.Success(
                            YDocumentPreview(
                                kind =
                                    YDocumentPreviewKind
                                        .Unsupported,
                                title = name,
                            ),
                        )
                }
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "document_preview_failed",
                    message =
                        error.message
                            ?: "Unable to preview document",
                    cause = error,
                )
            }
        }

    suspend fun readHexPage(
        ref: YFileRef,
        offset: Long,
        length: Int = DEFAULT_HEX_PAGE,
    ): Outcome<YHexPage> =
        withContext(Dispatchers.IO) {
            val safeOffset =
                offset.coerceAtLeast(0L)
            when (
                val stat = engine.stat(ref)
            ) {
                is Outcome.Failure -> stat
                is Outcome.Success -> {
                    when (
                        val read =
                            engine.read(
                                ref,
                                safeOffset,
                                length.coerceIn(
                                    16,
                                    MAX_HEX_PAGE,
                                ),
                            )
                    ) {
                        is Outcome.Failure ->
                            read
                        is Outcome.Success ->
                            Outcome.Success(
                                YHexPage(
                                    offset =
                                        safeOffset,
                                    bytes =
                                        read.value.data,
                                    totalBytes =
                                        stat.value
                                            .sizeBytes,
                                    editable =
                                        stat.value
                                            .writable &&
                                            (
                                                stat.value
                                                    .sizeBytes
                                                    ?: 0L
                                                ) <=
                                                MAX_EDIT_BYTES,
                                ),
                            )
                    }
                }
            }
        }

    suspend fun writeHex(
        ref: YFileRef,
        offset: Long,
        hex: String,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val compact =
                    hex.filterNot(
                        Char::isWhitespace,
                    )
                require(
                    compact.length % 2 == 0,
                ) {
                    "Hex input must contain complete bytes"
                }
                require(
                    compact.length <=
                        MAX_HEX_WRITE_BYTES *
                        2,
                ) {
                    "Hex edit is too large"
                }
                val bytes =
                    ByteArray(
                        compact.length / 2,
                    ) { index ->
                        compact
                            .substring(
                                index * 2,
                                index * 2 + 2,
                            )
                            .toInt(16)
                            .toByte()
                    }
                engine.write(
                    ref = ref,
                    offset =
                        offset.coerceAtLeast(0L),
                    data = bytes,
                    truncate = false,
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = "hex_write_failed",
                    message =
                        error.message
                            ?: "Unable to write hex data",
                    cause = error,
                )
            }
        }

    suspend fun checksums(
        ref: YFileRef,
    ): Outcome<YChecksumSet> =
        withContext(Dispatchers.IO) {
            try {
                val digests =
                    linkedMapOf(
                        "MD5" to
                            MessageDigest
                                .getInstance("MD5"),
                        "SHA-1" to
                            MessageDigest
                                .getInstance("SHA-1"),
                        "SHA-256" to
                            MessageDigest
                                .getInstance("SHA-256"),
                        "SHA-512" to
                            MessageDigest
                                .getInstance("SHA-512"),
                    )
                var offset = 0L
                while (true) {
                    when (
                        val chunk =
                            engine.read(
                                ref,
                                offset,
                                BUFFER_SIZE,
                            )
                    ) {
                        is Outcome.Failure ->
                            return@withContext
                                chunk
                        is Outcome.Success -> {
                            digests.values
                                .forEach {
                                    it.update(
                                        chunk.value
                                            .data,
                                    )
                                }
                            offset +=
                                chunk.value
                                    .data.size
                            if (
                                chunk.value.eof
                            ) {
                                break
                            }
                        }
                    }
                }
                Outcome.Success(
                    YChecksumSet(
                        digests.mapValues {
                            (_, digest) ->
                            digest.digest()
                                .joinToString(
                                    "",
                                ) {
                                    "%02x"
                                        .format(it)
                                }
                        },
                    ),
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "checksum_failed",
                    message =
                        error.message
                            ?: "Unable to calculate checksums",
                    cause = error,
                )
            }
        }

    private suspend fun previewText(
        ref: YFileRef,
        name: String,
    ): Outcome<YDocumentPreview> {
        val output =
            java.io.ByteArrayOutputStream()
        var offset = 0L
        var truncated = false
        while (output.size() < MAX_TEXT_BYTES) {
            val want =
                minOf(
                    BUFFER_SIZE,
                    MAX_TEXT_BYTES -
                        output.size(),
                )
            when (
                val chunk =
                    engine.read(
                        ref,
                        offset,
                        want,
                    )
            ) {
                is Outcome.Failure ->
                    return chunk
                is Outcome.Success -> {
                    output.write(
                        chunk.value.data,
                    )
                    offset +=
                        chunk.value.data.size
                    if (chunk.value.eof) {
                        break
                    }
                    if (
                        output.size() >=
                        MAX_TEXT_BYTES
                    ) {
                        truncated = true
                    }
                }
            }
        }
        return Outcome.Success(
            YDocumentPreview(
                kind =
                    YDocumentPreviewKind.Text,
                title = name,
                text =
                    output.toString(
                        Charsets.UTF_8.name(),
                    ),
                truncated = truncated,
            ),
        )
    }

    private fun previewPdf(
        file: File,
        name: String,
    ): Outcome<YDocumentPreview> {
        val descriptor =
            ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor
                    .MODE_READ_ONLY,
            )
        PdfRenderer(descriptor).use {
            renderer ->
            val count =
                renderer.pageCount
            if (count <= 0) {
                return Outcome.Success(
                    YDocumentPreview(
                        kind =
                            YDocumentPreviewKind
                                .Pdf,
                        title = name,
                        pageCount = 0,
                    ),
                )
            }
            renderer.openPage(0).use {
                page ->
                val scale =
                    minOf(
                        1f,
                        MAX_PDF_PREVIEW_DIMENSION
                            .toFloat() /
                            maxOf(
                                page.width,
                                page.height,
                            ).toFloat(),
                    )
                val bitmap =
                    Bitmap.createBitmap(
                        maxOf(
                            1,
                            (
                                page.width *
                                    scale
                                ).toInt(),
                        ),
                        maxOf(
                            1,
                            (
                                page.height *
                                    scale
                                ).toInt(),
                        ),
                        Bitmap.Config.ARGB_8888,
                    )
                page.render(
                    bitmap,
                    null,
                    Matrix().apply {
                        postScale(
                            scale,
                            scale,
                        )
                    },
                    PdfRenderer.Page
                        .RENDER_MODE_FOR_DISPLAY,
                )
                cacheDirectory.mkdirs()
                val preview =
                    File(
                        cacheDirectory,
                        "pdf-" +
                            UUID.randomUUID() +
                            ".png",
                    )
                FileOutputStream(preview)
                    .use {
                        bitmap.compress(
                            Bitmap.CompressFormat
                                .PNG,
                            90,
                            it,
                        )
                    }
                bitmap.recycle()
                return Outcome.Success(
                    YDocumentPreview(
                        kind =
                            YDocumentPreviewKind
                                .Pdf,
                        title = name,
                        imagePath =
                            preview.absolutePath,
                        pageCount = count,
                    ),
                )
            }
        }
    }

    private fun previewDocx(
        file: File,
        name: String,
    ): Outcome<YDocumentPreview> =
        ZipFile(file).use { zip ->
            val entry =
                zip.getEntry(
                    "word/document.xml",
                )
                    ?: error(
                        "DOCX document body is missing",
                    )
            val xml =
                zip.getInputStream(entry)
                    .bufferedReader()
                    .use { it.readText() }
            val text =
                xmlText(xml)
                    .take(MAX_TEXT_CHARS)
            Outcome.Success(
                YDocumentPreview(
                    kind =
                        YDocumentPreviewKind
                            .Docx,
                    title = name,
                    text = text,
                    truncated =
                        text.length >=
                            MAX_TEXT_CHARS,
                ),
            )
        }

    private fun previewXlsx(
        file: File,
        name: String,
    ): Outcome<YDocumentPreview> =
        ZipFile(file).use { zip ->
            val shared =
                zip.getEntry(
                    "xl/sharedStrings.xml",
                )?.let {
                    entry ->
                    xmlValues(
                        zip.getInputStream(
                            entry,
                        ).bufferedReader()
                            .use {
                                it.readText()
                            },
                        "t",
                    )
                }.orEmpty()
            val sheet =
                zip.entries()
                    .asSequence()
                    .filter {
                        !it.isDirectory &&
                            it.name.matches(
                                Regex(
                                    "xl/worksheets/sheet\\d+\\.xml",
                                ),
                            )
                    }
                    .sortedBy { it.name }
                    .firstOrNull()
                    ?: error(
                        "XLSX worksheet is missing",
                    )
            val xml =
                zip.getInputStream(sheet)
                    .bufferedReader()
                    .use { it.readText() }
            val rows =
                Regex(
                    "<row[^>]*>(.*?)</row>",
                    setOf(
                        RegexOption
                            .DOT_MATCHES_ALL,
                        RegexOption.IGNORE_CASE,
                    ),
                ).findAll(xml)
                    .take(MAX_SHEET_ROWS)
                    .map { row ->
                        Regex(
                            "<c[^>]*?(?:t=\"([^\"]+)\")?[^>]*>(.*?)</c>",
                            setOf(
                                RegexOption
                                    .DOT_MATCHES_ALL,
                                RegexOption
                                    .IGNORE_CASE,
                            ),
                        ).findAll(
                            row.groupValues[1],
                        ).map { cell ->
                            val body =
                                cell.groupValues[2]
                            val value =
                                Regex(
                                    "<v[^>]*>(.*?)</v>",
                                    setOf(
                                        RegexOption
                                            .DOT_MATCHES_ALL,
                                        RegexOption
                                            .IGNORE_CASE,
                                    ),
                                ).find(body)
                                    ?.groupValues
                                    ?.getOrNull(1)
                                    .orEmpty()
                            if (
                                cell.groupValues
                                    .getOrNull(1) ==
                                    "s"
                            ) {
                                value.toIntOrNull()
                                    ?.let {
                                        shared
                                            .getOrNull(
                                                it,
                                            )
                                    }
                                    ?: value
                            } else {
                                htmlDecode(value)
                            }
                        }.joinToString("\t")
                    }.joinToString("\n")
            Outcome.Success(
                YDocumentPreview(
                    kind =
                        YDocumentPreviewKind
                            .Xlsx,
                    title = name,
                    text =
                        rows.take(
                            MAX_TEXT_CHARS,
                        ),
                    truncated =
                        rows.length >
                            MAX_TEXT_CHARS,
                ),
            )
        }

    private fun previewEpub(
        file: File,
        name: String,
    ): Outcome<YDocumentPreview> =
        ZipFile(file).use { zip ->
            val chapters =
                zip.entries()
                    .asSequence()
                    .filter {
                        !it.isDirectory &&
                            (
                                it.name.endsWith(
                                    ".xhtml",
                                    true,
                                ) ||
                                    it.name
                                        .endsWith(
                                            ".html",
                                            true,
                                        ) ||
                                    it.name
                                        .endsWith(
                                            ".htm",
                                            true,
                                        )
                                )
                    }
                    .sortedBy { it.name }
                    .take(MAX_EPUB_CHAPTERS)
                    .map { entry ->
                        val html =
                            zip.getInputStream(
                                entry,
                            ).bufferedReader()
                                .use {
                                    it.readText()
                                }
                        xmlText(html)
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .joinToString(
                        "\n\n",
                    )
            Outcome.Success(
                YDocumentPreview(
                    kind =
                        YDocumentPreviewKind
                            .Epub,
                    title = name,
                    text =
                        chapters.take(
                            MAX_TEXT_CHARS,
                        ),
                    truncated =
                        chapters.length >
                            MAX_TEXT_CHARS,
                ),
            )
        }

    private suspend fun materialize(
        ref: YFileRef,
        suffix: String,
    ): File {
        if (ref.providerId == "local") {
            val local = File(ref.path)
            if (local.isFile) {
                return local
            }
        }
        cacheDirectory.mkdirs()
        val target =
            File(
                cacheDirectory,
                "preview-" +
                    UUID.randomUUID() +
                    suffix,
            )
        FileOutputStream(target).use {
            output ->
            var offset = 0L
            while (true) {
                when (
                    val chunk =
                        engine.read(
                            ref,
                            offset,
                            BUFFER_SIZE,
                        )
                ) {
                    is Outcome.Failure ->
                        error(
                            chunk.message,
                        )
                    is Outcome.Success -> {
                        output.write(
                            chunk.value.data,
                        )
                        offset +=
                            chunk.value
                                .data.size
                        if (
                            chunk.value.eof
                        ) {
                            break
                        }
                    }
                }
            }
        }
        return target
    }

    private fun xmlText(
        xml: String,
    ): String =
        htmlDecode(
            xml.replace(
                Regex(
                    "<(br|p|div|tr|w:p)[^>]*>",
                    RegexOption
                        .IGNORE_CASE,
                ),
                "\n",
            ).replace(
                Regex("<[^>]+>"),
                "",
            ),
        ).replace(
            Regex("[ \\t]+"),
            " ",
        ).replace(
            Regex("\\n{3,}"),
            "\n\n",
        ).trim()

    private fun xmlValues(
        xml: String,
        tag: String,
    ): List<String> =
        Regex(
            "<$tag[^>]*>(.*?)</$tag>",
            setOf(
                RegexOption.DOT_MATCHES_ALL,
                RegexOption.IGNORE_CASE,
            ),
        ).findAll(xml)
            .map {
                htmlDecode(
                    it.groupValues[1]
                        .replace(
                            Regex("<[^>]+>"),
                            "",
                        ),
                )
            }.toList()

    private fun htmlDecode(
        value: String,
    ): String =
        android.text.Html
            .fromHtml(
                value,
                android.text.Html
                    .FROM_HTML_MODE_LEGACY,
            ).toString()

    private fun isTextName(
        lower: String,
    ): Boolean =
        lower.substringAfterLast(
            '.',
            "",
        ) in TEXT_EXTENSIONS

    companion object {
        private const val BUFFER_SIZE =
            128 * 1024
        private const val MAX_TEXT_BYTES =
            2 * 1024 * 1024
        private const val MAX_TEXT_CHARS =
            300_000
        private const val MAX_SHEET_ROWS = 500
        private const val MAX_EPUB_CHAPTERS =
            128
        private const val DEFAULT_HEX_PAGE =
            4 * 1024
        private const val MAX_HEX_PAGE =
            64 * 1024
        private const val MAX_HEX_WRITE_BYTES =
            64 * 1024
        private const val MAX_EDIT_BYTES =
            64L * 1024L * 1024L
        private const val MAX_PDF_PREVIEW_DIMENSION =
            1600

        private val TEXT_EXTENSIONS =
            setOf(
                "txt",
                "md",
                "json",
                "xml",
                "yaml",
                "yml",
                "toml",
                "ini",
                "cfg",
                "conf",
                "log",
                "csv",
                "tsv",
                "sh",
                "bash",
                "zsh",
                "fish",
                "py",
                "kt",
                "kts",
                "java",
                "js",
                "ts",
                "html",
                "css",
                "scss",
                "c",
                "cpp",
                "h",
                "hpp",
                "rs",
                "go",
                "rb",
                "php",
                "sql",
                "gradle",
                "properties",
                "gitignore",
                "env",
            )
    }
}
