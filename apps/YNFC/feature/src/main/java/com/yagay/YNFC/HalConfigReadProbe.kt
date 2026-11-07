package com.yagay.YNFC

import com.yagay.YNFC.system.RootShell

/**
 * An explicit, bounded READ-ONLY evidence survey. This is not a native hook;
 * sampling an open FD cannot prove which configuration values were sent to NFCC.
 */
internal object HalConfigReadProbe {
    private val knownSource = "/data/vendor/nfc/libnfc-mtp-SN220.conf"
    private val expectedHalHash =
        "415152acf97d4731dafe370d8bb4e136a7929ce04a7255023f03f3cb60daea22"

    fun capture(rootShell: RootShell): String {
        val raw = rootShell.run(probeScript(), 20, 36_000, showToast = false)
        return summarize(raw)
    }

    fun summarize(raw: String): String {
        val lines = raw.lineSequence().toList()
        val processes = lines.filter { it.startsWith("HAL_PROCESS=") }
        val libraries = lines.filter { it.startsWith("HAL_LOADED_LIB=") }
        val configFds = lines.filter { it.startsWith("CONFIG_FD_OBSERVED=") }
        val nxpConfigFds = configFds.filter {
            it.startsWith("CONFIG_FD_OBSERVED=android.hardware.nfc2-service.nxp/")
        }
        val configFiles = lines.filter { it.startsWith("CONFIG_CANDIDATE=") }
        val hashes = lines.filter { it.startsWith("HAL_IMPLEMENTATION_SHA256=") }
        val logHints = lines.filter { it.startsWith("LOG_MATCH_") }
        val hadRoot = lines.any { it == "PROBE_UID=0" }
        val hashMatches = hashes.any { it.substringAfter("=").substringBefore(" ") == expectedHalHash }
        val classification = when {
            !hadRoot -> "ROOT_PROBE_UNAVAILABLE"
            processes.none { it.startsWith("HAL_PROCESS=android.hardware.nfc2-service.nxp ") } -> "NXP_HAL_PROCESS_NOT_OBSERVED"
            nxpConfigFds.isNotEmpty() -> "SN220_CONFIG_OPEN_FD_OBSERVED"
            else -> "SN220_CONFIG_OPEN_NOT_OBSERVED"
        }
        return buildString {
            appendLine("=== YNFC NXP HAL CONFIG LOAD PROBE (READ ONLY) ===")
            appendLine("recorded_at_ms=" + System.currentTimeMillis())
            appendLine("result=" + classification)
            appendLine("hal_processes_observed=" + processes.size)
            appendLine("hal_libraries_mapped=" + libraries.size)
            appendLine("sn220_config_open_fds_observed=" + configFds.size)
            appendLine("sn220_nxp_hal_config_fds_observed=" + nxpConfigFds.size)
            appendLine("sn220_config_file_candidates=" + configFiles.size)
            appendLine("uploaded_sn220_hal_sha256=" + expectedHalHash)
            appendLine("installed_sn220_hal_hash_status=" + when {
                hashes.isEmpty() -> "NOT_AVAILABLE"
                hashMatches -> "MATCH"
                else -> "DIFFERENT_FIRMWARE"
            })
            appendLine("source_of_uploaded_preview=" + knownSource)
            appendLine("hal_config_read_interception=NOT_INSTALLED")
            appendLine("nfc_config_write=NOT_ATTEMPTED")
            appendLine("nfc_service_restarted=NO")
            appendLine("controller_uid_verified=NO")
            appendLine("NOTE: An open file descriptor proves the HAL process had that file open at sampling time; it does NOT prove use of NXP_CORE_CONF.")
            appendLine("NOTE: Absence of an open descriptor is not proof the file was never read; it may have been closed during earlier initialization.")
            appendLine("NOTE: Log keyword counts and mapped libraries provide hints only, not direct function call observations.")
            appendLine("--- STRUCTURED OBSERVATIONS ---")
            (processes + libraries + configFds + configFiles + hashes + logHints).forEach { appendLine(it) }
            appendLine("--- ROOT PROBE OUTPUT (BOUNDED) ---")
            appendLine(raw.take(16_000))
        }
    }

    private fun probeScript(): String = """
        echo PROBE_STARTED
        echo "PROBE_UID=__D__(id -u 2>/dev/null)"
        echo '[NFC HAL processes and loaded native libraries]'
        for name in android.hardware.nfc2-service.nxp vendor.oplus.hardware.nfc_aidl-service vendor.oplus.hardware.nfcExtns-service; do
          pid=__D__(pidof "__D__name" 2>/dev/null | awk '{print __D__1}')
          if [ -n "__D__pid" ]; then
            echo "HAL_PROCESS=__D__name PID=__D__pid"
            grep -E '/.*(nfc_nci_nxp_snxxx_v2|libnfc|nfc.*[.]so)' /proc/"__D__pid"/maps 2>/dev/null | awk '{print __D__6}' | sort -u | head -n 30 | while IFS= read -r lib; do
              [ -n "__D__lib" ] && echo "HAL_LOADED_LIB=__D__lib"
            done
            for iteration in 1 2 3 4 5; do
              for fd in /proc/"__D__pid"/fd/*; do
                [ -L "__D__fd" ] || continue
                link=__D__(readlink "__D__fd" 2>/dev/null) || continue
                case "__D__link" in
                  */libnfc-mtp-SN220.conf*) echo "CONFIG_FD_OBSERVED=__D__name/__D__pid/__D__iteration PATH=__D__link";;
                esac
              done
              sleep 0.2
            done
          fi
        done
        echo '[SN220 config candidates: existence and hash only]'
        for source in /data/vendor/nfc/libnfc-mtp-SN220.conf /vendor/etc/libnfc-mtp-SN220.conf /odm/etc/nfc/libnfc-mtp-SN220.conf; do
          if [ -f "__D__source" ]; then
            sha=__D__(sha256sum "__D__source" 2>/dev/null | awk '{print __D__1}')
            echo "CONFIG_CANDIDATE=__D__source SHA256=__D__sha"
          fi
        done
        echo '[NXP implementation file fingerprints]'
        for source in /vendor/lib64/nfc_nci_nxp_snxxx_v2.so /vendor/lib64/hw/nfc_nci_nxp_snxxx_v2.so /odm/lib64/nfc_nci_nxp_snxxx_v2.so /odm/lib64/hw/nfc_nci_nxp_snxxx_v2.so; do
          if [ -f "__D__source" ]; then
            sha=__D__(sha256sum "__D__source" 2>/dev/null | awk '{print __D__1}')
            echo "HAL_IMPLEMENTATION_SHA256=__D__sha PATH=__D__source"
          fi
        done
        echo '[Recent logcat clues: counts only; raw packets never exported]'
        recent=__D__(logcat -b all -d -v brief -t 4500 2>/dev/null | grep -iE 'libnfc-mtp-SN220|readNxpRFConfig|NXP_CORE_CONF|CORE_SET_CONFIG|NxpNciX|NxpNciR' | tail -n 150)
        for key in libnfc-mtp-SN220 readNxpRFConfig NXP_CORE_CONF CORE_SET_CONFIG NxpNciX NxpNciR; do
          count=__D__(printf '%s\n' "__D__recent" | grep -icF "__D__key" || true)
          echo "LOG_MATCH___D__key=__D__count"
        done
        echo PROBE_FINISHED
    """.trimIndent().replace("__D__", 36.toChar().toString())
}
