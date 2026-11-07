package com.yagay.YNFC

import org.junit.Assert.assertTrue
import org.junit.Test

class HalConfigReadProbeTest {
    @Test fun reportsVisibleConfigFdWithoutClaimingNfcid1WasWritten() {
        val shell = listOf(
            "PROBE_STARTED",
            "PROBE_UID=0",
            "HAL_PROCESS=android.hardware.nfc2-service.nxp PID=2366",
            "HAL_LOADED_LIB=/vendor/lib64/nfc_nci_nxp_snxxx_v2.so",
            "CONFIG_FD_OBSERVED=android.hardware.nfc2-service.nxp/2366/1 PATH=/data/vendor/nfc/libnfc-mtp-SN220.conf",
            "PROBE_FINISHED",
            "[exit=0]",
        ).joinToString("\n")
        val report = HalConfigReadProbe.summarize(shell)
        assertTrue(report.contains("result=SN220_CONFIG_OPEN_FD_OBSERVED"))
        assertTrue(report.contains("nfc_config_write=NOT_ATTEMPTED"))
        assertTrue(report.contains("controller_uid_verified=NO"))
        assertTrue(report.contains("hal_config_read_interception=NOT_INSTALLED"))
    }

    @Test fun noOpenFdCannotProveFileWasNeverRead() {
        val report = HalConfigReadProbe.summarize(
            "PROBE_STARTED\nPROBE_UID=0\nHAL_PROCESS=android.hardware.nfc2-service.nxp PID=100\nPROBE_FINISHED\n[exit=0]"
        )
        assertTrue(report.contains("result=SN220_CONFIG_OPEN_NOT_OBSERVED"))
        assertTrue(report.contains("it may have been closed during earlier initialization"))
    }

    @Test fun differentNfcServiceCannotMasqueradeAsNxpHal() {
        val report = HalConfigReadProbe.summarize(
            "PROBE_STARTED\nPROBE_UID=0\nHAL_PROCESS=vendor.oplus.hardware.nfc_aidl-service PID=100\nPROBE_FINISHED\n[exit=0]"
        )
        assertTrue(report.contains("result=NXP_HAL_PROCESS_NOT_OBSERVED"))
    }

    @Test fun otherServiceFdDoesNotCountAsNxpConfigOpen() {
        val report = HalConfigReadProbe.summarize(
            "PROBE_STARTED\nPROBE_UID=0\nHAL_PROCESS=android.hardware.nfc2-service.nxp PID=100\n" +
            "CONFIG_FD_OBSERVED=vendor.oplus.hardware.nfc_aidl-service/110/1 PATH=/data/vendor/nfc/libnfc-mtp-SN220.conf\nPROBE_FINISHED\n[exit=0]"
        )
        assertTrue(report.contains("result=SN220_CONFIG_OPEN_NOT_OBSERVED"))
        assertTrue(report.contains("sn220_nxp_hal_config_fds_observed=0"))
    }

    @Test fun incompleteRootProbeCannotBeReportedAsNoFd() {
        val report = HalConfigReadProbe.summarize(
            "PROBE_STARTED\nPROBE_UID=0\nHAL_PROCESS=android.hardware.nfc2-service.nxp PID=100\n[timeout=20s]"
        )
        assertTrue(report.contains("result=PROBE_INCOMPLETE"))
        assertTrue(report.contains("probe_completed=false"))
    }

    @Test fun rootFailureReportedExplicitly() {
        val report = HalConfigReadProbe.summarize("[exit=1]")
        assertTrue(report.contains("result=ROOT_PROBE_UNAVAILABLE"))
    }
}
