package com.yagay.suite.core

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SuiteLog {
    private const val MAX_LOG_BYTES = 1024L * 1024L
    private val lock = Any()

    fun i(context: Context, module: String, message: String) = write(context, module, "I", message, null)
    fun e(context: Context, module: String, message: String, error: Throwable? = null) = write(context, module, "E", message, error)

    fun write(context: Context, module: String, level: String, message: String, error: Throwable? = null) {
        val safeModule = module.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]"), "_")
        val dir = File(context.filesDir, "suite-logs/$safeModule").apply { mkdirs() }
        val current = File(dir, "current.log")
        synchronized(lock) {
            if (current.length() >= MAX_LOG_BYTES) {
                val previous = File(dir, "previous.log")
                if (previous.exists()) previous.delete()
                current.renameTo(previous)
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            current.appendText(buildString {
                append(timestamp).append(' ').append(level).append(' ')
                append('[').append(safeModule).append("] ").append(message).append('\n')
                if (error != null) append(error.stackTraceToString()).append('\n')
            })
        }
    }

    fun export(context: Context, modules: Set<String>? = null): String {
        val root = File(context.filesDir, "suite-logs").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val label = when {
            modules == null -> "all"
            modules.size == 1 -> modules.first()
            else -> "selected"
        }
        val fileName = "YSuite-$label-logs-$stamp.zip"
        val relativeDir = "${Environment.DIRECTORY_DOWNLOADS}/${SuiteContract.LOG_EXPORT_SUBDIR}"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create Download/${SuiteContract.LOG_EXPORT_SUBDIR}/$fileName")

        try {
            resolver.openOutputStream(uri, "w")?.buffered()?.use { output ->
                ZipOutputStream(output).use { zip ->
                    root.listFiles()?.filter { it.isDirectory }?.forEach { moduleDir ->
                        if (modules != null && moduleDir.name !in modules) return@forEach
                        moduleDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                            zip.putNextEntry(ZipEntry("${moduleDir.name}/${file.name}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } ?: error("Unable to open Download/${SuiteContract.LOG_EXPORT_SUBDIR}/$fileName")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }

        return "Download/${SuiteContract.LOG_EXPORT_SUBDIR}/$fileName"
    }
}
