package com.yagay.ydiag.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yagay.ydiag.data.SessionStore
import com.yagay.suite.api.FeatureDiagnosticArchive
import com.yagay.ydiag.model.Issue
import com.yagay.ydiag.model.SessionMeta
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Summary(
    val formatVersion: Int = 1,
    val app: String = "YDiag",
    val targetPackages: List<String>,
    val sessionStart: Long,
    val sessionEnd: Long?,
    val problemMarks: List<Long>,
    val rootAvailable: Boolean,
    val lsposedConnected: Boolean,
    val enabledDiagnostics: List<String>,
    val issueCount: Int,
    val fatalOrErrorCount: Int,
    val warningCount: Int,
)

@Serializable
private data class ManifestEntry(val path: String, val bytes: Long, val type: String)

@Serializable
private data class ExportManifest(val formatVersion: Int = 1, val files: List<ManifestEntry>)

class DiagnosticExporter(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportLatest(customTree: Uri? = null): Uri? {
        val pair = SessionStore(context).listSessions().firstOrNull() ?: return null
        return export(pair.first, pair.second, customTree)
    }

    fun export(sessionDir: File, meta: SessionMeta, customTree: Uri? = null): Uri? {
        EvidenceCollector.collect(context, sessionDir, meta)
        prepareAiFiles(sessionDir, meta)

        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(exports, "YDiag-${meta.targetPackages.firstOrNull() ?: "session"}-${meta.id}.zip")
        FeatureDiagnosticArchive.zipDirectory(sessionDir, zipFile)

        return if (customTree != null) {
            copyToTree(zipFile, customTree)
        } else {
            copyToDownloads(zipFile)
        }
    }

    private fun prepareAiFiles(dir: File, meta: SessionMeta) {
        val issues = File(dir, "issues.jsonl").takeIf { it.isFile }?.readLines().orEmpty()
            .mapNotNull { runCatching { json.decodeFromString<Issue>(it) }.getOrNull() }
        val summary = Summary(
            targetPackages = meta.targetPackages,
            sessionStart = meta.startedAt,
            sessionEnd = meta.endedAt,
            problemMarks = meta.problemMarks,
            rootAvailable = meta.rootAvailable,
            lsposedConnected = meta.lsposedConnected,
            enabledDiagnostics = meta.enabledOptions,
            issueCount = issues.size,
            fatalOrErrorCount = issues.count { it.severity == "FATAL" || it.severity == "ERROR" },
            warningCount = issues.count { it.severity == "WARNING" },
        )
        File(dir, "summary.json").writeText(json.encodeToString(summary))
        File(dir, "issues.json").writeText(json.encodeToString(issues))
        File(dir, "diagnosis.txt").writeText(buildString {
            appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_generated_f8256e2521e6))
            appendLine("================")
            appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_targets, meta.targetPackages.joinToString()))
            appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_marks, meta.problemMarks.joinToString()))
            appendLine(com.yagay.suite.api.YLocale.text(com.yagay.ydiag.R.string.ydiag_export_issues, issues.size))
            issues.take(50).forEach {
                appendLine("[${it.severity}] ${it.category}: ${it.title}")
            }
        })
        File(dir, "README-AI.txt").writeText(buildString {
            appendLine("YDiag diagnostic package")
            appendLine()
            appendLine("Recommended analysis order:")
            appendLine("1. summary.json")
            appendLine("2. diagnosis.txt")
            appendLine("3. issues.json")
            appendLine("4. timeline.jsonl")
            appendLine("5. process-snapshots.txt")
            appendLine("6. evidence/*")
            appendLine("7. logcat-full-*.txt")
            appendLine()
            appendLine("Problem marks are explicit timestamps chosen by the user. Prioritize events around them.")
            appendLine("Raw logs are retained for verification; summary files are indexes, not replacements.")
        })

        val manifestEntries = dir.walkTopDown()
            .filter { it.isFile && it.name != "manifest.json" }
            .map {
                val rel = it.relativeTo(dir).invariantSeparatorsPath
                ManifestEntry(rel, it.length(), fileType(rel))
            }.toList()
        File(dir, "manifest.json").writeText(json.encodeToString(ExportManifest(files = manifestEntries)))
    }

    private fun fileType(path: String): String = when {
        path.endsWith(".jsonl") -> "jsonl"
        path.endsWith(".json") -> "json"
        path.endsWith(".perfetto-trace") -> "perfetto"
        path.contains("logcat") -> "android_logcat"
        path.contains("tombstone") -> "android_tombstone"
        path.contains("anr") -> "android_anr"
        else -> "text"
    }

    private fun copyToDownloads(source: File): Uri? =
        runCatching {
            FeatureDiagnosticArchive.publishToDownloads(context, source, source.name, "YDiag")
        }.getOrNull()

    private fun copyToTree(source: File, tree: Uri): Uri? {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return null
        root.findFile(source.name)?.delete()
        val target = root.createFile("application/zip", source.name) ?: return null
        context.contentResolver.openOutputStream(target.uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: return null
        return target.uri
    }
}
