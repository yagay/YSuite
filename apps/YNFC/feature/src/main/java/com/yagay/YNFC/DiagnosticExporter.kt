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

    /**
     * Separate, user-triggered HAL evidence capture. Never restarts NFC or
     * sends config commands. Save the probe beside the other YNFC exports.
     */
    fun exportHalConfigReadProbe(onDone: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val report = collector.buildHalConfigReadProbe()
                val fileName = "YNFC-hal-config-probe_" + System.currentTimeMillis() + ".txt"
                val saved = runCatching {
                    NfcPublicDownloads.write(
                        appContext, fileName, report.toByteArray(Charsets.UTF_8), "text/plain"
                    )
                }
                if (saved.isSuccess) saved.getOrThrow()
                else saveToAppFolder(fileName, report).absolutePath
            }
            runCatching { onDone(result) }.onFailure {
                AppLogger.i("HAL config probe callback failed: " + it.message)
            }
        }
    }

    /** Read-only BEFORE_OFF / AFTER_BOOT power-state snapshot, saved with current evidence. */
    fun exportPowerOffCapture(phase: String, onDone: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val report = collector.capturePowerOffRouteEvidence(phase)
                writePowerOffEvidence("capture", report)
            }
            runCatching { onDone(result) }.onFailure {
                AppLogger.i("Power-off capture callback failed: " + it.message)
            }
        }
    }

    /** Stores manual external reader observation; never claims verified hardware behavior. */
    fun exportPowerOffReaderResult(source: String, uid: String, onDone: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val report = collector.recordPowerOffReaderUid(source, uid)
                writePowerOffEvidence("reader", report)
            }
            runCatching { onDone(result) }.onFailure {
                AppLogger.i("Power-off reader callback failed: " + it.message)
            }
        }
    }

    private fun writePowerOffEvidence(kind: String, report: String): String {
        val fileName = "YNFC-poweroff-${kind}_${System.currentTimeMillis()}.txt"
        return runCatching {
            NfcPublicDownloads.write(
                appContext, fileName, report.toByteArray(Charsets.UTF_8), "text/plain"
            )
        }.getOrElse {
            saveToAppFolder(fileName, report).absolutePath
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
