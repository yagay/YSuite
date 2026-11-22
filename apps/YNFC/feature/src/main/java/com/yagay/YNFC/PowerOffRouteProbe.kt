package com.yagay.YNFC

import android.content.Context
import com.yagay.YNFC.system.RootShell

/**
 * Stores three explicit, read-only observations around a manual power-off experiment.
 * The phone cannot sample RF while it is powered off. A separately connected reader
 * and user-entered UID is always required for actual shutdown verification.
 *
 * The collector NEVER calls setNciConfig, setPoweroffTask, NFC service restart,
 * vendor card management, or eSE/NFCEE routing changes.
 */
internal class PowerOffRouteProbe(
    context: Context,
    private val shell: RootShell,
    private val runtime: RuntimeStatusRepository
) {
    companion object {
        const val OFFICIAL = "OFFICIAL_BEFORE_OFF"
        const val YNFC = "YNFC_BEFORE_OFF"
        const val AFTER_BOOT = "AFTER_BOOT"
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        "ynfc_poweroff_route_study_v1", Context.MODE_PRIVATE
    )

    fun capture(phase: String): String {
        require(phase in setOf(OFFICIAL, YNFC, AFTER_BOOT)) { "Unknown capture stage" }
        val provider = runtime.readProviderMap().toMap()
        val enabled = provider[ConfigProvider.KEY_SIMULATION_ENABLED] == "true"
        if (phase == OFFICIAL && enabled) {
            error("Stop YNFC and restore original NFC before sampling official wallet")
        }
        if (phase == YNFC && !enabled) {
            error("First activate one YNFC test card, then capture the YNFC stage")
        }
        val raw = shell.run(readOnlyScript(), 18, 20_000, showToast = false)
        val fields = PowerOffRouteEvidence.routeFields(raw)
        val capturedAt = System.currentTimeMillis()
        val uid = provider[ConfigProvider.KEY_UID]?.let {
            PowerOffRouteEvidence.normalizedUid(it)
        }.orEmpty()
        val phaseSnapshot = buildString {
            appendLine("stage=$phase")
            appendLine("captured_at_ms=$capturedAt")
            appendLine("ynfc_enabled=$enabled")
            appendLine("ynfc_backend=${provider[ConfigProvider.KEY_BACKEND] ?: "RF"}")
            appendLine("ynfc_expected_uid=${if (phase == YNFC) uid else "NOT_APPLICABLE"}")
            appendLine("command_generation=${provider[ConfigProvider.KEY_COMMAND_GENERATION] ?: "0"}")
            appendLine("command_status=${provider[ConfigProvider.KEY_COMMAND_STATUS] ?: "UNKNOWN"}")
            appendLine("hook_impl_revision=${provider["hook_impl_revision"] ?: "UNKNOWN"}")
            appendLine("controller_epoch=${provider[ConfigProvider.KEY_CONTROLLER_EPOCH] ?: "UNKNOWN"}")
            appendLine("nfc_process_pid=${provider[ConfigProvider.KEY_RUNTIME_PID] ?: "UNKNOWN"}")
            appendLine("route_config_entries=${fields.size}")
            appendLine("default_mifare_clt_switch_off_bit=" +
                when (PowerOffRouteEvidence.switchOffPowerBit(fields["DEFAULT_MIFARE_CLT_PWR_STATE"])) {
                    true -> "PRESENT"
                    false -> "ABSENT"
                    null -> "UNKNOWN"
                })
            appendLine("--- READ-ONLY DEVICE OUTPUT ---")
            appendLine(raw)
        }
        val editor = prefs.edit().putString("snapshot_$phase", phaseSnapshot)
            .putLong("timestamp_$phase", capturedAt)
        // A new trial must not inherit a reader observation from a previous card.
        if (phase == OFFICIAL) editor.remove("reader_uid_OFFICIAL").remove("reader_uid_at_OFFICIAL")
        if (phase == YNFC) editor.remove("reader_uid_YNFC").remove("reader_uid_at_YNFC")
        check(editor.commit()) {
            "Failed to persist power-off evidence"
        }
        return report()
    }

    fun recordReaderUid(cardSource: String, observed: String): String {
        require(cardSource in setOf("OFFICIAL", "YNFC")) { "Unknown tested card source" }
        val before = if (cardSource == "OFFICIAL") OFFICIAL else YNFC
        require(!prefs.getString("snapshot_$before", null).isNullOrBlank()) {
            "Capture the matching before-shutdown snapshot first"
        }
        val value = if (observed == "NO_RESPONSE") "NO_RESPONSE" else
            PowerOffRouteEvidence.normalizedUid(observed)
                ?: error("External reader UID must be 4, 7 or 10 bytes of hexadecimal")
        val capturedAt = System.currentTimeMillis()
        check(prefs.edit().putString("reader_uid_$cardSource", value)
            .putLong("reader_uid_at_$cardSource", capturedAt).commit()) {
            "Failed to persist reader observation"
        }
        return report()
    }

    fun report(): String = buildString {
        val official = prefs.getString("snapshot_$OFFICIAL", "").orEmpty()
        val ynfc = prefs.getString("snapshot_$YNFC", "").orEmpty()
        val after = prefs.getString("snapshot_$AFTER_BOOT", "").orEmpty()
        val officialFields = PowerOffRouteEvidence.comparisonFields(official)
        val ynfcFields = PowerOffRouteEvidence.comparisonFields(ynfc)
        val afterFields = PowerOffRouteEvidence.comparisonFields(after)
        fun bootId(report: String) = report.lineSequence().firstOrNull {
            it.startsWith("BOOT_ID=")
        }?.substringAfter('=').orEmpty()
        val beforeBootId = bootId(ynfc).ifEmpty { bootId(official) }
        val afterBootId = bootId(after)
        val officialUid = prefs.getString("reader_uid_OFFICIAL", "").orEmpty()
        val ynfcUid = prefs.getString("reader_uid_YNFC", "").orEmpty()
        val expected = ynfc.lineSequence().firstOrNull {
            it.startsWith("ynfc_expected_uid=")
        }?.substringAfter('=').orEmpty()

        appendLine("=== YNFC POWER-OFF ROUTE STUDY v1 (READ ONLY) ===")
        appendLine("official_snapshot=${if (official.isEmpty()) "MISSING" else "CAPTURED"}")
        appendLine("ynfc_snapshot=${if (ynfc.isEmpty()) "MISSING" else "CAPTURED"}")
        appendLine("after_boot_snapshot=${if (after.isEmpty()) "MISSING" else "CAPTURED"}")
        appendLine("boot_identity_comparison=" + when {
            beforeBootId.isEmpty() || afterBootId.isEmpty() -> "NOT_COMPARABLE"
            beforeBootId == afterBootId -> "SAME_BOOT_NO_RESTART_OBSERVED"
            else -> "DIFFERENT_BOOT_IDS_RESTART_OBSERVED_POWER_OFF_NOT_PROVEN"
        })
        appendLine("official_vs_ynfc_route=" +
            PowerOffRouteEvidence.compareFields(officialFields, ynfcFields))
        appendLine("ynfc_vs_after_boot_route=" +
            PowerOffRouteEvidence.compareFields(ynfcFields, afterFields))
        appendLine("official_reader_result=" + when (officialUid) {
            "" -> "NOT_RECORDED"
            "NO_RESPONSE" -> "USER_REPORTED_NO_RESPONSE"
            else -> "USER_REPORTED_UID"
        })
        appendLine("ynfc_reader_result=" + when (ynfcUid) {
            "" -> "NOT_RECORDED"
            "NO_RESPONSE" -> "USER_REPORTED_NO_RESPONSE"
            else -> "USER_REPORTED_UID"
        })
        appendLine("ynfc_reader_uid_match=" + when {
            ynfcUid == "NO_RESPONSE" -> "USER_REPORTED_NO_RESPONSE"
            ynfcUid.isEmpty() || expected.isEmpty() -> "NOT_REPORTED"
            ynfcUid.equals(expected, ignoreCase = true) -> "USER_REPORTED_MATCH"
            else -> "USER_REPORTED_DIFFERENCE"
        })
        appendLine("poweroff_auto_verified=NO")
        appendLine("note=Power-state route bits indicate eligibility only, not a custom NFC-A UID or off-host applet")
        appendLine("note=External UID values are user-entered; physical shutdown and reader behavior cannot be independently verified by Android")
        appendLine("note=AFTER_BOOT is sampled after Android is running again, never during shutdown")
        listOf(OFFICIAL, YNFC, AFTER_BOOT).forEach { phase ->
            appendLine("--- SNAPSHOT $phase ---")
            appendLine(prefs.getString("snapshot_$phase", "NOT_CAPTURED"))
        }
        appendLine("--- USER REPORTED OFFICIAL READER UID ---")
        appendLine(if (officialUid.isEmpty()) "NOT_RECORDED" else officialUid)
        appendLine("--- USER REPORTED YNFC READER UID ---")
        appendLine(if (ynfcUid.isEmpty()) "NOT_RECORDED" else ynfcUid)
    }

    private fun readOnlyScript(): String = """
        echo "ROOT_UID=__D__(id -u 2>/dev/null)"
        echo "NFC_PID=__D__(pidof com.android.nfc 2>/dev/null)"
        echo "BOOT_ID=__D__(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
        echo "NFC_CHIP=__D__(getprop persist.sys.nfc.chip_name)"
        echo "NFC_IMPL=__D__(getprop ro.boot.nfc)"
        for src in /odm/etc/nfc/pnscr/libnfc-nxp-pnscr.conf /vendor/etc/libnfc-nxp.conf /odm/etc/libnfc-nxp.conf; do
          if [ -f "__D__src" ]; then
            echo "ROUTE_CONFIG_SOURCE=__D__src"
            grep -E '^[[:space:]]*DEFAULT_(AID|DESFIRE|MIFARE_CLT|FELICA_CLT)_(ROUTE|PWR_STATE)[[:space:]]*=' "__D__src" | head -n 16
            break
          fi
        done
        for key in hce_access_card_mode hce_access_card_sak hce_access_card_atqa; do
          value=__D__(settings get global "__D__key" 2>/dev/null)
          echo "GLOBAL___D__key=__D__value"
        done
        uid_value=__D__(settings get global hce_access_card_uid 2>/dev/null)
        if [ -n "__D__uid_value" ] && [ "__D__uid_value" != "null" ]; then
          echo "GLOBAL_hce_access_card_uid=PRESENT_REDACTED"
        else
          echo "GLOBAL_hce_access_card_uid=ABSENT"
        fi
        echo "ROUTING_DUMPSYS_HINTS_BEGIN"
        dumpsys nfc 2>/dev/null | grep -Ei 'routing|mDefaultRoute|eSE|uicc|off.?host' | head -n 24
        echo "ROUTING_DUMPSYS_HINTS_END"
        echo "DH_MODE_LAST_LOG_HINT_BEGIN"
        logcat -b all -d -t 2500 -v brief 2>/dev/null | grep -i -E 'isUsingDH[=:](yes|no|true|false)' | grep -o -i -E 'isUsingDH[=:](yes|no|true|false)' | tail -n 1
        echo "DH_MODE_LAST_LOG_HINT_END"
        echo "PROBE_FINISHED"
    """.trimIndent().replace("__D__", 36.toChar().toString())
}
