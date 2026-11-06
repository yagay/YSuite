package com.yagay.YNFC

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.concurrent.Executors

/**
 * Export a bounded diagnostic snapshot, then persist it to Downloads.
 * MediaStore errors fall back to application external files without deleting
 * the only diagnostic snapshot.
 */
internal class DiagnosticExporter(
    context: Context,
    private val collector: DiagnosticsCollector
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val executor = Executors.newSingleThreadExecutor()

    fun export(statusSummary: (RuntimeStatus) -> String, onDone: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val report = collector.buildExportReport(statusSummary)
                val fileName = "YNFC-diagnostic_${BuildConfig.VERSION_NAME}_${System.currentTimeMillis()}.txt"
                val primary = runCatching { saveToDownloads(fileName, report) }
                if (primary.isSuccess) {
                    AppLogger.i("Diagnostics saved to Downloads: $fileName")
                    "Downloads/$fileName"
                } else {
                    val error = primary.exceptionOrNull()
                    AppLogger.i("MediaStore export failed: ${error?.javaClass?.simpleName}: ${error?.message}")
                    val fallback = saveToAppFolder(fileName, report)
                    AppLogger.i("Diagnostics saved to fallback: ${fallback.absolutePath}")
                    fallback.absolutePath
                }
            }.onFailure { error ->
                AppLogger.i("Diagnostics export failed: ${error.javaClass.simpleName}: ${error.message}")
            }
            runCatching { onDone(result) }.onFailure {
                AppLogger.i("Diagnostics callback failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    private fun saveToDownloads(fileName: String, report: String) {
        var uri: Uri? = null
        try {
            uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            ) ?: error("MediaStore Downloads insert returned null")
            resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(report)
            } ?: error("Cannot open Downloads output stream")
            val updated = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            if (updated < 1) error("Cannot publish pending Downloads file")
        } catch (error: Throwable) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            throw error
        }
    }

    private fun saveToAppFolder(fileName: String, report: String): File {
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        if (!directory.exists() && !directory.mkdirs()) {
            error("Cannot create diagnostic fallback directory")
        }
        val output = File(directory, fileName)
        output.writeText(report, Charsets.UTF_8)
        return output
    }

    override fun close() {
        executor.shutdown()
    }
}
