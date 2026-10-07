package com.yagay.ysuite.feature.ydiag

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.yagay.ysuite.feature.ydiag.api.YDiagEvent
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal object YDiagSessionExporter {
    fun export(
        context: Context,
        packageName: String,
        enabledOptions: Set<String>,
        events: List<YDiagEvent>,
    ): String {
        val prefs = context.getSharedPreferences(
            com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.PREFS,
            Context.MODE_PRIVATE,
        )
        val dir = prefs.getString(
            com.yagay.ysuite.feature.ydiag.runtime.YDiagMonitorService.KEY_CURRENT,
            null,
        )?.let(::File)?.takeIf { it.isDirectory }
        val createdAt = System.currentTimeMillis()
        val issues = detectIssues(dir, events)
        val summary = JSONObject().apply {
            put("package", packageName)
            put("createdAt", createdAt)
            put("options", JSONArray(enabledOptions.sorted()))
            put("eventCount", events.size)
            put("issueCount", issues.length())
            put("sessionDirectoryPresent", dir != null)
        }
        val manifest = JSONObject().apply {
            put("format", "YSuite.YDiag.v2")
            put("package", packageName)
            put("createdAt", createdAt)
            put(
                "files",
                JSONArray(
                    dir?.listFiles()
                        ?.map { it.name }
                        ?.sorted()
                        .orEmpty(),
                ),
            )
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        "YDiag-" + packageName.replace('.', '_') + "-" + createdAt + ".zip",
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/YSuite/YDiag",
                    )
                },
            ),
        )
        resolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                putText(zip, "summary.json", summary.toString(2))
                putText(zip, "issues.json", issues.toString(2))
                putText(zip, "manifest.json", manifest.toString(2))
                putText(
                    zip,
                    "README-AI.txt",
                    "YSuite YDiag diagnostic bundle\n" +
                        "Read summary.json first, then issues.json and timeline.jsonl.\n" +
                        "Raw evidence is under raw/.\n",
                )
                putText(
                    zip,
                    "events.jsonl",
                    events.joinToString("\n") { event ->
                        JSONObject().apply {
                            put("id", event.id)
                            put("time", event.timestampMillis)
                            put("option", event.optionId)
                            put("severity", event.severity.name)
                            put("title", event.title)
                            put("detail", event.detail)
                        }.toString()
                    },
                )
                dir?.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedBy { it.name }
                    ?.forEach { file ->
                        zip.putNextEntry(ZipEntry("raw/" + file.name))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
        } ?: error("Unable to open YDiag export")
        return uri.toString()
    }

    private fun detectIssues(dir: File?, events: List<YDiagEvent>): JSONArray {
        val text = buildString {
            events.forEach { appendLine(it.detail) }
            dir?.listFiles()
                ?.filter { it.isFile && it.length() <= 8L * 1024L * 1024L }
                ?.forEach { file ->
                    runCatching {
                        appendLine(file.readText().takeLast(2_000_000))
                    }
                }
        }.lowercase()
        val out = JSONArray()
        fun add(id: String, severity: String, evidence: String) {
            out.put(
                JSONObject().apply {
                    put("id", id)
                    put("severity", severity)
                    put("evidence", evidence)
                },
            )
        }
        if ("fatal exception" in text || "fatal signal" in text) {
            add("crash", "fatal", "Fatal exception/signal observed")
        }
        if ("anr in" in text || "not responding" in text) {
            add("anr", "error", "ANR evidence observed")
        }
        if ("avc: denied" in text) {
            add("selinux", "warning", "SELinux denial observed")
        }
        if ("outofmemoryerror" in text || "lowmemory" in text) {
            add("memory", "error", "Memory pressure/OOM observed")
        }
        if ("hook_failed" in text) {
            add("hook", "warning", "LSPosed hook failure observed")
        }
        return out
    }

    private fun putText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }
}
