package com.yagay.YNFC

import android.content.Context
import android.os.Environment
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
    private val executor = Executors.newSingleThreadExecutor()

    fun export(statusSummary: (RuntimeStatus) -> String, onDone: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val report = collector.buildExportReport(statusSummary)
                val fileName = "YNFC-diagnostic_${BuildConfig.VERSION_NAME}_${System.currentTimeMillis()}.txt"
                val primary = runCatching {
                    NfcPublicDownloads.write(
                        appContext, fileName, report.toByteArray(Charsets.UTF_8), "text/plain"
                    )
                }
                if (primary.isSuccess) {
                    AppLogger.i("Diagnostics saved to Downloads: $fileName")
                    NfcPublicDownloads.RELATIVE_PATH + fileName
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
