package com.yagay.YNFC

import android.content.Context
import android.util.Base64
import com.yagay.YNFC.system.RootShell
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * App-side snapshot/export. No native injection, no SELinux bypass, no
 * modification of the stock HAL file and no NFC controller write.
 */
internal object NfcSharedConfigManager {
    private const val SOURCE = "/data/vendor/nfc/libnfc-mtp-SN220.conf"
    private const val SNAPSHOT_PREFS = "ynfc_shared_sn220_snapshot"
    private const val BASE64_START = "YNFC_CONF_BASE64_BEGIN"
    private const val BASE64_END = "YNFC_CONF_BASE64_END"
    private const val MAX_INPUT_BYTES = 524_288

    fun prepare(context: Context, rootShell: RootShell, card: CardModel, backend: String): CardModel {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        val snapshotId = System.currentTimeMillis().toString() + "-" +
            java.util.UUID.randomUUID().toString().take(8)
        val snapshotPath = NfcPublicDownloads.RELATIVE_PATH + "snapshots/" + snapshotId + "/"
        prefs.edit().putString("status", "PREPARING")
            .putString("error", "")
            .putString("source", "")
            .putString("stock_sha256", "")
            .putString("modified_sha256", "")
            .putString("snapshot_id", snapshotId)
            .putString("snapshot_path", snapshotPath)
            .putLong("prepared_at_ms", System.currentTimeMillis()).apply()
        return try {
            // The private profile remains the canonical command input.
            // Public Downloads is an inspectable copy, never a HAL file source.
            val prepared = NfcProfileFile.createForCard(appContext, card)
            if (backend == "NATIVE") {
                // This root-level status is the commit gate. Never publish
                // PREVIEW_ONLY until all session-specific files and hashes
                // have been written and their manifest committed.
                publishStatus(appContext, "PREPARING",
                    "Creating SN220 snapshot " + snapshotId + "; old previews must not be used")
                NfcPublicDownloads.write(
                    appContext, "selected-nfc-a.json",
                    NfcProfileFile.encodeCard(prepared), "application/json", snapshotPath
                )
                val original = readStockSn220(rootShell)
                val preview = Sn220ConfigPreview.preview(original, prepared.uid)
                NfcPublicDownloads.write(
                    appContext, "original-SN220.conf", original,
                    "application/octet-stream", snapshotPath
                )
                NfcPublicDownloads.write(
                    appContext, "modified-SN220.conf", preview.modified,
                    "application/octet-stream", snapshotPath
                )
                val summary = buildString {
                    appendLine("YNFC SN220 configuration preview")
                    appendLine("status=PREVIEW_ONLY_NATIVE_WRITE_DISABLED")
                    appendLine("stock_source=" + SOURCE)
                    appendLine("stock_sha256=" + preview.originalHash)
                    appendLine("modified_sha256=" + preview.modifiedHash)
                    appendLine("core_old_bytes=" + preview.oldCoreBytes)
                    appendLine("core_new_bytes=" + preview.newCoreBytes)
                    appendLine("folder=" + snapshotPath)
                    appendLine("snapshot_id=" + snapshotId)
                    appendLine("target_uid_length_bytes=" + prepared.uid.length / 2)
                    appendLine("requested_atqa=" + prepared.atqa)
                    appendLine("requested_sak=" + prepared.sak)
                    appendLine("applied_fields_to_preview=NFCID1_ONLY")
                    appendLine("NOTE: ATQA and SAK were NOT rewritten. A UID-only preview cannot emulate arbitrary card protocols.")
                    appendLine("The system config and HAL were not modified.")
                    appendLine("The NFC HAL does NOT automatically have Downloads access.")
                    appendLine("This preview does not establish on-air UID simulation.")
                }
                NfcPublicDownloads.write(
                    appContext, "snapshot-manifest.txt",
                    summary.toByteArray(StandardCharsets.UTF_8), "text/plain",
                    snapshotPath
                )
                // Mark the active snapshot only after all immutable exports
                // and their manifest have been committed.
                NfcPublicDownloads.write(
                    appContext, "config-status.txt",
                    summary.toByteArray(StandardCharsets.UTF_8), "text/plain"
                )
                prefs.edit().putString("status", "PREVIEW_ONLY_NATIVE_WRITE_DISABLED")
                    .putString("source", SOURCE)
                    .putString("stock_sha256", preview.originalHash)
                    .putString("modified_sha256", preview.modifiedHash)
                    .putInt("stock_bytes", original.size)
                    .putInt("preview_bytes", preview.modified.size)
                    .putString("folder", snapshotPath).apply()
                AppLogger.i("SN220_PROFILE exported native offline preview " +
                    "stockSha=" + preview.originalHash + " previewSha=" + preview.modifiedHash)
            } else {
                NfcPublicDownloads.write(
                    appContext, "selected-nfc-a.json",
                    NfcProfileFile.encodeCard(prepared), "application/json"
                )
                publishStatus(appContext, "PROFILE_EXPORTED",
                    "Only the selected JSON was exported. Previous SN220 previews are not active.")
                prefs.edit().putString("status", "PROFILE_EXPORTED")
                    .putString("folder", NfcPublicDownloads.RELATIVE_PATH).apply()
                AppLogger.i("NFC_PROFILE saved JSON to " + NfcPublicDownloads.RELATIVE_PATH)
            }
            prepared
        } catch (e: Exception) {
            val safeError = e.javaClass.simpleName + ": " + (e.message ?: "unknown").take(200)
            prefs.edit().putString("status", "FAILED")
                .putString("error", safeError)
                .apply()
            runCatching {
                publishStatus(appContext, "FAILED",
                    "The current preparation failed; do not use any older exported SN220 preview. " +
                        safeError)
            }
            AppLogger.e("SN220_PROFILE preparation failed (no native command published)", e)
            throw e
        }
    }

    private fun publishStatus(context: Context, status: String, detail: String) {
        val message = "status=" + status + "\n" +
            "folder=" + NfcPublicDownloads.RELATIVE_PATH + "\n" +
            "detail=" + detail + "\n" +
            "native_hal_download_readable=NOT_VERIFIED\n" +
            "native_config_interception=NOT_INSTALLED\n"
        NfcPublicDownloads.write(
            context, "config-status.txt", message.toByteArray(StandardCharsets.UTF_8), "text/plain"
        )
    }

    private fun readStockSn220(rootShell: RootShell): ByteArray {
        // Only the active data/vendor path referenced by the submitted HAL
        // binary is eligible. Never guess among ODM revision-suffixed files.
        val script = """
            src='$SOURCE'
            if [ ! -f "${'$'}src" ]; then echo 'YNFC_SN220_SOURCE_MISSING'; exit 5; fi
            size=${'$'}(stat -c %s "${'$'}src" 2>/dev/null)
            case "${'$'}size" in ''|*[!0-9]*) echo 'YNFC_SN220_SIZE_UNKNOWN'; exit 6;; esac
            if [ "${'$'}size" -lt 1 ] || [ "${'$'}size" -gt $MAX_INPUT_BYTES ]; then
              echo 'YNFC_SN220_SIZE_INVALID'; exit 7
            fi
            printf '$BASE64_START\n'
            base64 "${'$'}src" | tr -d '\r\n'
            printf '\n$BASE64_END\n'
        """.trimIndent()
        val output = rootShell.run(script, 15, 790_000, showToast = false)
        val pattern = Regex(
            """(?s)""" + BASE64_START +
                """\s+([A-Za-z0-9+/=\r\n]+?)\s+""" + BASE64_END
        )
        val match = pattern.find(output)
            ?: throw IOException("Cannot read active SN220 config through Root: " +
                output.takeLast(180).trim())
        val encoded = match.groupValues[1].replace(Regex("""\s+"""), "")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        if (bytes.isEmpty() || bytes.size > MAX_INPUT_BYTES) {
            throw IOException("Invalid SN220 configuration length")
        }
        return bytes
    }

    fun diagnostics(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(
            SNAPSHOT_PREFS, Context.MODE_PRIVATE
        )
        return buildString {
            appendLine("--- YNFC SHARED CONFIG EXPORT ---")
            appendLine("folder=" + NfcPublicDownloads.RELATIVE_PATH)
            appendLine("status=" + prefs.getString("status", "NOT_PREPARED"))
            appendLine("stock_source=" + prefs.getString("source", "NOT_CAPTURED"))
            appendLine("stock_sha256=" + prefs.getString("stock_sha256", "NOT_CAPTURED"))
            appendLine("modified_sha256=" + prefs.getString("modified_sha256", "NOT_CAPTURED"))
            appendLine("stock_bytes=" + prefs.getInt("stock_bytes", 0))
            appendLine("preview_bytes=" + prefs.getInt("preview_bytes", 0))
            appendLine("prepared_at_ms=" + prefs.getLong("prepared_at_ms", 0L))
            appendLine("snapshot_id=" + prefs.getString("snapshot_id", "NOT_PREPARED"))
            appendLine("snapshot_path=" + prefs.getString("snapshot_path", "NOT_PREPARED"))
            appendLine("error=" + prefs.getString("error", ""))
            appendLine("native_hal_download_readable=NOT_VERIFIED")
            appendLine("native_config_interception=NOT_INSTALLED")
        }
    }
}

