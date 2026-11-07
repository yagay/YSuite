package com.yagay.YNFC

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real NXP HAL packet shapes, with unrelated UID bytes deliberately omitted. */
class NciHalTraceTest {
    private val commandMs = 1_800_000_000_837L

    private fun line(ms: Int, tag: String, hex: String): String {
        val epoch = "1800000000." + ms.toString().padStart(3, '0')
        return epoch + "  2347  8200 I " + tag + " : len = " + (hex.length / 2) + " > " + hex
    }

    @Test fun routeAndDiscoveryAreNotReportedAsUidConfiguration() {
        val log = listOf(
            line(889, "NxpNciX", "21060100"),
            line(890, "NxpNciR", "41060100"),
            line(892, "NxpNciX",
                "2101310007020FC03BA000000809434343444B4176311202003903040001FEFE0103001104000300390000030039010003003902"),
            line(892, "NxpNciR", "41010100"),
            line(893, "NxpNciX", "21030F070001010102018001810182010601")
        ).joinToString("\n")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("RF_DEACTIVATE_TX=1"))
        assertTrue(report.contains("RF_SET_LISTEN_MODE_ROUTING_TX=1"))
        assertTrue(report.contains("RF_DISCOVER_TX=1"))
        assertTrue(report.contains("CORE_SET_CONFIG_TX=0"))
        assertTrue(report.contains("LA_NFCID1_TAG_33_IN_CONFIG_TX=0"))
        assertTrue(report.contains("RF_ROUTING_OR_OTHER_ONLY_NO_CORE_CONFIG_VISIBLE"))
        assertTrue(report.contains("+52ms TX/CMD RF_DEACTIVATE"))
        // No NCI packet payload or coincidental card identifier escapes.
        assertFalse(report.contains("210131000702"))
        assertFalse(report.contains("434343444B"))
    }

    @Test fun detectsCoreConfigAndUidParameterWithoutReportingUidValue() {
        val log = line(950, "NxpNciX", "20020701330404A1B2C3") +
            "\n" + line(952, "NxpNciR", "4002020000")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("CORE_SET_CONFIG_TX=1"))
        assertTrue(report.contains("LA_NFCID1_TAG_33_IN_CONFIG_TX=1"))
        assertTrue(report.contains("LA_NFCID1_LENGTHS_ONLY=4"))
        assertTrue(report.contains("UID_PARAM_SENT_NOT_RF_VERIFIED"))
        assertFalse(report.contains("04A1B2C3"))
    }

    @Test fun randomUidConfigDoesNotClaimFixedUid() {
        val report = NciHalTrace.summarize(
            line(910, "NxpNciX", "200203013300"), commandMs)
        assertTrue(report.contains("LA_NFCID1_LENGTHS_ONLY=0"))
        assertTrue(report.contains("CORE_SET_CONFIG_TX=1"))
    }

    @Test fun invalidFrameOrOutOfWindowDoesNotInventUidEvidence() {
        val log = line(900, "NxpNciX", "20020701330404A1B2") +
            "\n" + "1800000200.123  2347 I NxpNciX : len = 10 > 20020701330404A1B2C3"
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("IN_WINDOW_FRAMES=0"))
        assertTrue(report.contains("LA_NFCID1_TAG_33_IN_CONFIG_TX=0"))
        assertTrue(report.contains("NO_HAL_FRAMES_IN_COMMAND_WINDOW_OR_BUFFER_TRUNCATED"))
    }

    @Test fun emptyLogIsNotTreatedAsHardwareFailureProof() {
        val report = NciHalTrace.summarize("", commandMs)
        assertTrue(report.contains("SNAPSHOT_VALID_FRAMES=0"))
        assertTrue(report.contains("NO_HAL_FRAMES_IN_COMMAND_WINDOW_OR_BUFFER_TRUNCATED"))
    }
}
