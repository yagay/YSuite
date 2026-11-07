package com.yagay.YNFC

import android.content.Context
import android.content.pm.PackageManager
import com.yagay.YNFC.system.NfcSystemService
import com.yagay.YNFC.system.RootShell

/**
 * Owns diagnostic collection and Full Check report generation.
 *
 * Keeping shell/log collection out of MainActivity makes diagnostics independently evolvable and
 * ensures UI recomposition never contains the expensive root/logcat work itself.
 */
class DiagnosticsCollector(
    context: Context,
    private val rootShell: RootShell,
    private val nfcSystemService: NfcSystemService,
    private val runtimeRepository: RuntimeStatusRepository,
    private val cardRepository: CardRepository
) {
    private val packageName = context.packageName
    private val packageManager = context.applicationContext.packageManager
    private val nciTracePreferences = context.applicationContext.getSharedPreferences(
        "ynfc_nci_trace_readonly_v1", Context.MODE_PRIVATE
    )

    /**
     * Capture only a sanitized NCI command summary immediately after an RF
     * APPLY result. Waiting for the user to export a report can let Android's
     * small logcat buffers evict the relevant TX/RX events. No raw packets,
     * UIDs, config values, or new NFC operations are persisted here.
     */
    fun captureNativeNciAfterApply(generation: Long) {
        if (generation <= 0L) return
        val capturedAt = System.currentTimeMillis()
        val raw = runRoot(
            "logcat -b all -d -v epoch -t 2400 2>/dev/null | grep -E 'NxpNciX[[:space:]]*:|NxpNciR[[:space:]]*:' | tail -n 380",
            10, 165_000
        )
        val summary = NciHalTrace.summarize(raw, generation)
        nciTracePreferences.edit()
            .putLong("generation", generation)
            .putLong("captured_at_ms", capturedAt)
            .putString("summary", summary)
            .apply()
        AppLogger.i("NCI_TRACE_SNAPSHOT commandGeneration=$generation capturedAt=$capturedAt " +
            "summaryOnly=true")
    }

    fun fetchLogs(source: LogSource): String {
        val pid = currentNfcPid()
        val output = when (source) {
            LogSource.STATUS -> runRoot("logcat -d -t 350 -v threadtime -s YNFC NfcUIDSim 2>/dev/null")
            LogSource.LSPOSED -> runRoot("""
                { grep -R -h -E 'NfcUIDSim|com.yagay.YNFC|\[ynfc/main\]|YSuite[.]Xposed|YSuite[.]Hook|PROD MODULE|PROD HOOK|RFPROBE|NFCID1|plugin load failed; plugin=ynfc|plugin lifecycle failed;.*ynfc' /data/adb/lspd/log 2>/dev/null || true
                  logcat -b all -d -v threadtime 2>/dev/null | grep -E 'NfcUIDSim|YNFC|YSuite[.]Xposed|YSuite[.]Hook|PROD MODULE|PROD HOOK|RFPROBE|NFCID1|COMMAND' || true; } | tail -n 1500
            """.trimIndent())
            LogSource.KERNEL_SU -> runRoot("for f in ${'$'}(ls -t /data/adb/ksu/log/sulog* 2>/dev/null | head -n 3); do echo === ${'$'}f ===; tail -n 300 ${'$'}f; done")
            LogSource.SYSTEM -> runRoot("logcat -b all -d -v threadtime 2>/dev/null | tail -n 1800")
            LogSource.NFC -> {
                val filter = "NfcUIDSim|NfcService|NxpNfcService|NfcChipDeviceImpl|NFCID1|COMMAND|changeRfParamsByConfig|setRfConfig|VendorNfcService|enableNfcShareMode|NfcSwitchCardDispatcher|RealTimeSwitchCardManager|HceAccessCard|LxDebugProfileCompare|StrProfileMatch|TapToShareEvent|NfcRfEventStateMachine|RoutingTableParser|NxpNciX|NxpNciR|NfcAdaptation"
                val pidFilter = if (pid.isNotBlank()) "${'$'}0 ~ / $pid / || ${'$'}0 ~ /$filter/" else "${'$'}0 ~ /$filter/"
                runRoot("logcat -b all -d -v threadtime 2>/dev/null | awk '$pidFilter' | tail -n 1800")
            }
            LogSource.HAL -> runRoot("""
                echo '--- NFC PROCESSES ---'; ps -A | grep -E 'android.hardware.nfc|vendor.oplus.hardware.nfc|com.android.nfc' || true
                echo '--- NFC PROPERTIES ---'; getprop | grep -i -E 'nfc|nxp|st21|st54|sn100|sn220|oplus' | head -n 300 || true
                echo '--- HAL LOGCAT ---'; logcat -b all -d -v threadtime 2>/dev/null | grep -i -E 'android.hardware.nfc|vendor.oplus.hardware.nfc|NxpNfc|NfcHal|NxpNciX|NxpNciR|NfcAdaptation|oplus_nfc|libnfc|nfc-service|NFC HAL|STNfc|sn100|sn220' | tail -n 5000 || true
            """.trimIndent())
            LogSource.PROVIDER -> buildString {
                appendLine("=== PROVIDER STATE ===")
                runtimeRepository.readProviderMap().toSortedMap().forEach { (k, v) -> appendLine("$k=$v") }
                appendLine("current_nfc_pid=${currentNfcPid()}")
            }
            LogSource.APP -> AppLogger.readAll().ifBlank { "No app logs" }
        }
        return output.ifBlank { "No matching logs found for ${source.label}" }
    }

    /**
     * Fast export path for the user-facing button. Full forensic capture can
     * invoke dozens of independent root processes and take several minutes,
     * leaving the UI apparently stuck. Capture one bounded NFC logcat snapshot,
     * plus app logs and the actual provider state, without depending on Root
     * being available at all.
     */
    fun buildExportReport(statusSummary: (RuntimeStatus) -> String): String = buildString {
        val capturedAt = System.currentTimeMillis()
        val snapshot = runtimeRepository.readProviderMap().toMap()
        val state = runtimeRepository.decode(snapshot, null)
        appendLine("=== YNFC DIAGNOSTIC EXPORT ${BuildConfig.VERSION_NAME} ===")
        appendLine("Captured epoch ms: $capturedAt")
        appendLine("NFC scope: com.android.nfc")
        appendLine("--- STATUS ---")
        appendLine(runCatching { statusSummary(state) }.getOrElse {
            "Status unavailable: ${it.javaClass.simpleName}: ${it.message}"
        })
        appendLine("--- PROVIDER SNAPSHOT ---")
        snapshot.toSortedMap().forEach { (key, value) -> appendLine("$key=$value") }
        appendLine("--- UID-ONLY RF ENTRYPOINT COMPARISON ---")
        appendLine("legacy_changeRfParamsByConfig=${snapshot["rf_uid_legacy_entry"] ?: "NOT_REPORTED"}")
        appendLine("modern_setNciConfig=${snapshot["rf_uid_modern_entry"] ?: "NOT_REPORTED"}")
        appendLine("inventory_pid=${snapshot["rf_uid_inventory_pid"] ?: "0"}")
        val observedWriterPid = snapshot["rf_uid_writer_observed_pid"]?.toIntOrNull() ?: 0
        val actualNfcPid = snapshot["runtime_pid"]?.toIntOrNull() ?: 0
        appendLine("writer_observation=${if (actualNfcPid > 0 && actualNfcPid == observedWriterPid) "CURRENT_PID_NOT_UID_PROOF" else "NO_CURRENT_PID_INVOCATION_PROOF"}")
        appendLine("writer_pid=$observedWriterPid current_pid=$actualNfcPid")
        appendLine("writer_target=${snapshot["rf_uid_writer_target"] ?: "NOT_OBSERVED"}")
        appendLine("writer_shape=${snapshot["rf_uid_writer_payload_shape"] ?: "NOT_OBSERVED"}")
        appendLine("writer_prefix=${snapshot["rf_uid_writer_payload_prefix"] ?: ""}")
        appendLine("writer_score=${snapshot["rf_uid_writer_payload_score"] ?: "0"}")
        appendLine("writer_observed_at=${snapshot["rf_uid_writer_observed_at"] ?: "0"}")
        appendLine("NOTE: A writer invocation or a native return is not proof of the UID seen by an external reader.")
        // No UID writer callback was observed in this firmware's Java NFC
        // service. A compact HAL inventory helps distinguish an OEM-native
        // NFC control path from an inactive Java hook. Read-only: no NFC
        // toggle, Binder transaction, controller write or service restart.
        appendLine("--- NFC HAL / NATIVE PATH INVENTORY (READ ONLY) ---")
        appendLine(runRoot("""
            echo '[HAL processes]'
            ps -A 2>/dev/null | grep -i -E 'android.hardware.nfc|vendor.oplus.hardware.nfc|nfc-hal|nfc-service' | head -n 16 || true
            echo '[HAL service registrations]'
            service list 2>/dev/null | grep -i -E 'nfc|nxp' | head -n 24 || true
            echo '[NFC properties]'
            getprop 2>/dev/null | grep -i -E 'nfc|nxp|st21|st54|sn100|sn220' | head -n 35 || true
            echo '[NFC library filenames]'
            ls -1 /vendor/lib64/*nfc* /vendor/lib64/hw/*nfc* /system/lib64/*nfc* 2>/dev/null | head -n 36 || true
        """.trimIndent(), 10, 18_000))
        // Read-only NXP HAL investigation. On SN220-class firmware the
        // current Java setNciConfig Hook is installed but not called.
        // Show only configuration file paths and relevant *key names*.
        // Do not alter HAL configuration, controller state or UID data.
        appendLine("--- NXP NATIVE NFC CONFIG / NCI EVIDENCE (READ ONLY) ---")
        appendLine(runRoot("""
            echo '[All NFC-related config paths: no file contents]'
            find /vendor/etc /odm/etc /system/etc /system_ext/etc /product/etc -maxdepth 3 -type f \( -iname '*nfc*.conf' -o -iname '*nfc*.cfg' -o -iname '*nfc*.xml' \) 2>/dev/null | head -n 55 || true
            echo '[NFC config key inventory: values hidden]'
            grep -HniE '^[[:space:]]*[a-zA-Z_][a-zA-Z0-9_]*[[:space:]]*=' /vendor/etc/*nfc*.conf /odm/etc/*nfc*.conf /system/etc/*nfc*.conf /system_ext/etc/*nfc*.conf /product/etc/*nfc*.conf 2>/dev/null | sed -E 's/=.*$/=<hidden>/' | head -n 90 || true
            echo '[NXP/HCE/UID-specific keys: values hidden]'
            grep -HniEi '^[[:space:]]*[a-zA-Z_][a-zA-Z0-9_]*(NFCID|UID|HCE|LISTEN|RNDM|RANDOM|CORE_CONF)[a-zA-Z0-9_]*[[:space:]]*=' /vendor/etc/*nfc*.conf /odm/etc/*nfc*.conf /system/etc/*nfc*.conf /system_ext/etc/*nfc*.conf /product/etc/*nfc*.conf 2>/dev/null | sed -E 's/=.*$/=<hidden>/' | head -n 45 || true
            echo '[Logcat buffer availability]'
            logcat -g 2>&1 | head -n 8 || true
            echo '[Recent raw logcat line count]'
            logcat -b all -d -t 350 -v brief 2>/dev/null | wc -l
            echo '[NCI TX/RX recent lines: not guaranteed exposed on stock firmware]'
            logcat -b all -d -v threadtime -t 3000 2>/dev/null | grep -i -E 'NxpNciX|NxpNciR|CORE_SET_CONFIG|nfc_ncif_send|NfcAdaptation.*HalWrite' | tail -n 30 || true
        """.trimIndent(), 12, 45_000))
        appendLine("--- NATIVE NCI CORRELATION TO LAST UID COMMAND (READ ONLY) ---")
        val priorCapturedGeneration = nciTracePreferences.getLong("generation", 0L)
        val priorCapturedAt = nciTracePreferences.getLong("captured_at_ms", 0L)
        if (priorCapturedGeneration > 0L) {
            appendLine("EARLY_SNAPSHOT_COMMAND_GENERATION=$priorCapturedGeneration")
            appendLine("EARLY_SNAPSHOT_CAPTURED_AT_MS=$priorCapturedAt")
            if (priorCapturedGeneration == state.commandGeneration) {
                appendLine("--- EARLY NCI SNAPSHOT (CAPTURED IMMEDIATELY AFTER APPLY) ---")
                appendLine(nciTracePreferences.getString("summary", "NO_EARLY_SUMMARY"))
            } else {
                appendLine("EARLY_SNAPSHOT_STATUS=OTHER_GENERATION_NOT_CURRENT_UID_PROOF")
            }
        } else {
            appendLine("EARLY_SNAPSHOT_STATUS=NOT_CAPTURED")
        }
        appendLine(runCatching {
            val halPackets = runRoot(
                "logcat -b all -d -v epoch -t 4200 2>/dev/null | grep -E 'NxpNciX[[:space:]]*:|NxpNciR[[:space:]]*:' | tail -n 400",
                12, 150_000
            )
            NciHalTrace.summarize(halPackets, state.commandGeneration)
        }.getOrElse { "NCI analysis unavailable: " + it.javaClass.simpleName })
        appendLine("UID_CONFIG_PATH_NOTE: No UID write or HAL configuration was attempted by diagnostic collection.")
        if (snapshot["polling_guard_detail"].orEmpty().contains("java_poll_mask_not_zero_0xffffffff")) {
            appendLine("POLLING_MASK_NOTE: 0xffffffff is also the Hook unreadable-mask sentinel (-1); it does not prove all polling technologies are enabled.")
        }
        appendLine("--- SAVED CARD PARAMETERS ---")
        runCatching { cardRepository.load() }.getOrDefault(emptyList()).forEach { card ->
            appendLine("card uid=${card.uid} sak=${card.sak} atqa=${card.atqa}")
        }
        // Read-only permission inventory: a Root shell and an LSPosed hook do
        // not grant the NFC process the OEM signature/privileged permission.
        // Keep this bounded and avoid mutating framework permission state.
        appendLine("--- COLOROS NFC PERMISSION (READ ONLY) ---")
        appendLine(runRoot("""
            echo '[NFC process identity]'; ps -A | grep '[c]om.android.nfc' | head -n 3 || true
            echo '[permission definition]'; dumpsys package permissions 2>/dev/null | grep -F -B 3 -A 12 'com.oplus.permission.safe.NFC' | head -n 60 || true
            echo '[NFC package requests/grants]'; dumpsys package com.android.nfc 2>/dev/null | grep -F -B 5 -A 7 'com.oplus.permission.safe.NFC' | head -n 60 || true
            echo '[NFC app location]'; pm path com.android.nfc 2>/dev/null | head -n 5 || true
        """.trimIndent(), 10, 20_000))
        appendLine("[safe.NFC PackageManager authorization]")
        appendLine(runCatching {
            val permission = "com.oplus.permission.safe.NFC"
            val status = packageManager.checkPermission(permission, "com.android.nfc")
            when (status) {
                PackageManager.PERMISSION_GRANTED -> "com.android.nfc=GRANTED"
                PackageManager.PERMISSION_DENIED -> "com.android.nfc=DENIED"
                else -> "com.android.nfc=UNKNOWN($status)"
            }
        }.getOrElse { "Permission query unavailable: ${it.javaClass.simpleName}: ${it.message}" })
        appendLine("--- APP LOGS ---")
        appendLine(runCatching { AppLogger.readAll() }.getOrElse {
            "App logs unavailable: ${it.javaClass.simpleName}: ${it.message}"
        })
        appendLine("--- ROOT LOGCAT NFC/LSPOSED/COLOROS ---")
        appendLine(runRoot("""
            logcat -b all -d -t 2200 -v threadtime 2>/dev/null | grep -i -E 'YNFC|NfcUIDSim|VENDOR_TYPE_A|NfcService|NxpNfc|STNfc|NfcAdaptation|RFPROBE|NFCID1|NfcHal|com.android.nfc|LSPosed.*(YNFC|NfcUIDSim|com.yagay.YSuite)' | tail -n 800 || true
        """.trimIndent(), 12, 700_000))
        appendLine("--- EXPORT COMPLETE ---")
    }

    fun buildFullReport(statusSummary: (RuntimeStatus) -> String): String = buildString {
        val snapshotAt = System.currentTimeMillis()
        val snapshotMap = runtimeRepository.readProviderMap().toMap()
        val snapshotPid = currentNfcPid().toIntOrNull()
        val status = runtimeRepository.decode(snapshotMap, snapshotPid)

        appendLine("=== NFC FULL CHECK ${BuildConfig.VERSION_NAME} ===")
        appendLine("Generated: $snapshotAt")
        appendLine("Snapshot: frozen provider + NFC PID; generation=${status.commandGeneration} pid=${status.currentPid}")
        appendLine("Trigger: LSPosed in-process NFC command engine; AIDL transaction IDs are reflected with compatibility fallbacks")
        appendLine("--- FINAL SUMMARY ---")
        appendLine(statusSummary(status))
        appendLine()
        appendLine("--- SAVED CARDS ---")
        val cards = cardRepository.load()
        appendLine("count=${cards.size}")
        cards.forEach { appendLine("card uid=${it.uid} sak=${it.sak} atqa=${it.atqa}") }
        appendLine("--- APP / APK ---")
        appendLine(runRoot("dumpsys package $packageName 2>/dev/null | grep -E 'versionName=|versionCode=|path:'"))
        appendLine("--- ROOT ---")
        appendLine(runRoot("id; su -v 2>/dev/null || true"))
        appendLine("--- NFC PROCESS / HAL ---")
        appendLine(runRoot("pm path com.android.nfc; pidof com.android.nfc; ps -A | grep -E 'android.hardware.nfc|vendor.oplus.hardware.nfc|com.android.nfc|$packageName'"))
        appendLine("--- NFC CONFIG SNAPSHOT ---")
        appendLine(nfcSystemService.collectNfcConfigSnapshot())
        appendLine("--- NFC SERVICE FULL ---")
        appendLine(runRoot("dumpsys nfc 2>/dev/null", 25, 300_000))
        appendLine("--- NFC OVERWRITE TRACE / CORRELATED TIMELINE ---")
        appendLine(collectNfcOverwriteTrace())

        LogSource.entries.forEach { source ->
            appendLine()
            appendLine("=== LOG SOURCE: ${source.name} / ${source.label} ===")
            if (source == LogSource.PROVIDER) {
                appendLine("=== PROVIDER STATE (FROZEN SNAPSHOT) ===")
                snapshotMap.toSortedMap().forEach { (k, v) -> appendLine("$k=$v") }
                appendLine("current_nfc_pid=${snapshotPid ?: 0}")
            } else {
                appendLine(fetchLogs(source))
            }
        }
    }

    private fun currentNfcPid(): String = nfcSystemService.currentNfcPid()

    /** High-retention timeline used for the OEM/NXP NFCID1 overwrite investigation. */
    private fun collectNfcOverwriteTrace(): String = buildString {
        appendLine("TRACE_CAPTURE_EPOCH_MS=${System.currentTimeMillis()}")
        appendLine("TRACE_CURRENT_NFC_PID=${currentNfcPid()}")
        appendLine("TRACE_PROVIDER_BEGIN")
        runtimeRepository.readProviderMap().toSortedMap().forEach { (k, v) -> appendLine("$k=$v") }
        appendLine("TRACE_PROVIDER_END")

        appendLine("--- LOGCAT BUFFER INFO ---")
        appendLine(runRoot("logcat -g 2>/dev/null || true", 15, 300_000))

        appendLine("--- NCI TX/RX FULL TIMELINE (epoch) ---")
        appendLine(runRoot("""
            logcat -b all -d -v epoch 2>/dev/null | \
              grep -E 'NxpNciX|NxpNciR|NfcAdaptation::HalWrite|android\.hardware\.nfc-service\.nxp: write|NxpHal|phNxp|nfc_ncif_send|CORE_SET_CONFIG' || true
        """.trimIndent(), 35, 4_000_000))

        appendLine("--- NCI SET-CONFIG / NFCID1 CANDIDATES ---")
        appendLine(runRoot("""
            logcat -b all -d -v epoch 2>/dev/null | \
              grep -E 'NxpNciX|NxpNciR' | \
              grep -E -i '20[ :_-]*02|2002|33[ :_-]*00|3300|C1[ :_-]*B0[ :_-]*BC[ :_-]*1B|NFCID1' || true
        """.trimIndent(), 25, 2_000_000))

        appendLine("--- OPLUS RUNTIME CARD / SUPER-CARD TIMELINE (epoch) ---")
        appendLine(runRoot("""
            logcat -b all -d -v epoch 2>/dev/null | \
              grep -E 'NfcSwitchCardDispatcher|RealTimeSwitchCardManager|HceAccessCard|StrProfileMatch|LxDebugProfileCompare|TapToShareEvent|NfcRfEventStateMachine|VendorNfcService|loadListenTechMask|RESTORE_SUPERCARD|SUPER.?CARD|RF_FIELD|onRfFieldDetected|onLxDebugConfigData' || true
        """.trimIndent(), 30, 3_000_000))

        appendLine("--- BOOT / ROUTING / TAP-SHARE NFC TIMELINE (epoch) ---")
        appendLine(runRoot("""
            logcat -b all -d -v epoch 2>/dev/null | \
              grep -E 'ACTION_OPLUS_BOOT_COMPLETED|BOOT_COMPLETED|RoutingTableParser|NfcServiceRegister|accept-tap_share|startAdvertise|NfcChipDeviceImpl|setRfConfig|changeRfParamsByConfig|enableNfcShareMode|NfcUIDSim' || true
        """.trimIndent(), 30, 3_000_000))

        appendLine("--- NFC PROCESS / HAL STATE AT EXPORT ---")
        appendLine(runRoot("""
            echo '[processes]'; ps -A | grep -E 'com.android.nfc|android.hardware.nfc|vendor.oplus.hardware.nfc' || true
            echo '[properties]'; getprop | grep -i -E 'nfc|nxp|nfcuidsim|initialized' || true
            echo '[dumpsys summary]'; dumpsys nfc 2>/dev/null | grep -E -i 'state|screen|routing|discovery|reader|secure|listen|poll|host|aid' | head -n 600 || true
        """.trimIndent(), 25, 1_500_000))
    }

    private fun runRoot(command: String, timeoutSeconds: Long = 20, maxChars: Int = 1_000_000): String =
        rootShell.run(command, timeoutSeconds, maxChars)
}
