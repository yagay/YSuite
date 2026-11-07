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

    private fun earlierLine(epochSeconds: Long, millis: Int, tag: String, hex: String): String {
        return "$epochSeconds." + millis.toString().padStart(3, '0') +
            "  2347  8200 I $tag : len = " + (hex.length / 2) + " > " + hex
    }

    @Test fun preWindowRfDiscoveryIsRetainedInReadOnlyStateHint() {
        val log = listOf(
            earlierLine(1799999998L, 400, "NxpNciX", "21030100"),
            earlierLine(1799999998L, 410, "NxpNciR", "41030100"),
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(952, "NxpNciR", "400203090133")
        ).joinToString("\n")
        val summary = NciHalTrace.summarize(log, commandMs)
        assertTrue(summary.contains("OUTSIDE_WINDOW_BEFORE=2"))
        assertTrue(summary.contains("PRE_UID_RF_DISCOVER_TX=1 RSP_OK=1"))
        assertTrue(summary.contains("PRE_UID_RF_STATE_HINT=DISCOVERY_POSSIBLE_NOT_PROVEN"))
        assertTrue(summary.contains("UID_PARAM_REJECTED_NCI_INVALID_PARAM"))
        assertFalse(summary.contains("04A1B2C3"))
    }

    @Test fun preWindowRfDeactivationToIdleOverridesDiscoverHint() {
        val log = listOf(
            earlierLine(1799999998L, 200, "NxpNciX", "21030100"),
            earlierLine(1799999998L, 210, "NxpNciR", "41030100"),
            earlierLine(1799999998L, 300, "NxpNciX", "21060100"),
            earlierLine(1799999998L, 310, "NxpNciR", "41060100"),
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(952, "NxpNciR", "400203090133")
        ).joinToString("\n")
        val summary = NciHalTrace.summarize(log, commandMs)
        assertTrue(summary.contains("PRE_UID_RF_DEACTIVATE_TX=1 IDLE_RSP_OK=1"))
        assertTrue(summary.contains("PRE_UID_RF_LAST_CONFIRMED_EVENT=RF_DEACTIVATE_TO_IDLE_RSP_OK"))
        assertTrue(summary.contains("PRE_UID_RF_STATE_HINT=IDLE_POSSIBLE_NOT_PROVEN"))
    }

    @Test fun incompleteEarlierRfCommandCannotBeTreatedAsStateProof() {
        val log = listOf(
            earlierLine(1799999998L, 400, "NxpNciX", "21030100"),
            line(950, "NxpNciX", "20020701330404A1B2C3")
        ).joinToString("\n")
        val summary = NciHalTrace.summarize(log, commandMs)
        assertTrue(summary.contains("PRE_UID_RF_DISCOVER_TX=1 RSP_OK=0"))
        assertTrue(summary.contains("PRE_UID_RF_STATE_HINT=UNKNOWN"))
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

    @Test fun acceptedNciResponseIsNotConfusedWithRfUidVerification() {
        val log = listOf(
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(951, "NxpNciR", "400203000000")
        ).joinToString("\n")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_MATCHED=1"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_ACCEPTED=1"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_STATUS=0x00_ACCEPTED"))
        assertTrue(report.contains("UID_PARAM_SENT_NOT_RF_VERIFIED"))
        assertFalse(report.contains("04A1B2C3"))
    }

    @Test fun rejectedNciResponseIsVisibleWithoutReportingPayload() {
        val log = listOf(
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(951, "NxpNciR", "40020101")
        ).joinToString("\n")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_MATCHED=1"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_REJECTED=1"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_STATUS=0x01_REJECTED"))
        assertFalse(report.contains("04A1B2C3"))
    }

    @Test fun orphanResponseDoesNotGetAssignedToUidCommand() {
        val report = NciHalTrace.summarize(
            line(952, "NxpNciR", "40020100"), commandMs)
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_MATCHED=0"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_STATUS=NO_MATCHING_RESPONSE_IN_LOG"))
    }

    @Test fun specificUidParamRejectionIsAuthoritative() {
        val log = listOf(
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(952, "NxpNciR", "400203090133")
        ).joinToString("\n")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_STATUS=0x09_REJECTED"))
        assertTrue(report.contains("CORE_SET_CONFIG_RSP_INVALID_PARAM_IDS=0x33"))
        assertTrue(NciHalTrace.isOwnUidParameterRejected(report))
        assertFalse(report.contains("04A1B2C3"))
    }

    @Test fun unrelatedOlderPacketsDoNotHideCurrentUidRejection() {
        val log = listOf(
            line(100, "NxpNciX", "21060100"),
            line(950, "NxpNciX", "20020701330404A1B2C3"),
            line(952, "NxpNciR", "400203090133")
        ).joinToString("\n")
        val report = NciHalTrace.summarize(log, commandMs)
        assertTrue(report.contains("LA_NFCID1_FIRST_TX_DELTA_MS=113"))
        assertTrue(NciHalTrace.isOwnUidParameterRejected(report))
        assertTrue(report.contains("UID_PARAM_REJECTED_NCI_INVALID_PARAM"))
    }

    @Test fun unrelatedOrMalformedNciRejectionCannotFinalizeUid() {
        val uidTx = line(950, "NxpNciX", "20020701330404A1B2C3")
        val unrelatedParam = line(952, "NxpNciR", "400203090131")
        val missingInvalidId = line(952, "NxpNciR", "4002020900")
        val otherCode = line(952, "NxpNciR", "40020101")
        assertFalse(NciHalTrace.isOwnUidParameterRejected(
            NciHalTrace.summarize(uidTx + "\n" + unrelatedParam, commandMs)))
        assertFalse(NciHalTrace.isOwnUidParameterRejected(
            NciHalTrace.summarize(uidTx + "\n" + missingInvalidId, commandMs)))
        assertFalse(NciHalTrace.isOwnUidParameterRejected(
            NciHalTrace.summarize(uidTx + "\n" + otherCode, commandMs)))
        assertFalse(NciHalTrace.isOwnUidParameterRejected(
            NciHalTrace.summarize(unrelatedParam, commandMs)))
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
        assertTrue(report.contains("ONLY_POST_WINDOW_HAL_FRAMES_EARLIER_TX_MAY_HAVE_BEEN_EVICTED"))
    }

    @Test fun lateProprietaryRxDoesNotProveMissingCommandOrUidWrite() {
        // Real SN220T-style vendor notifications are logged several seconds
        // after an APPLY, once the small logcat ring buffer has rotated.
        val packet = "6F360A1904000501000000000C"
        val logs = listOf(
            "1800000008.653  2366  8692 I NxpNciR : len = 13 > $packet",
            "1800000010.543  2366  8692 I NxpNciR : len = 13 > $packet",
        ).joinToString("\n")
        val report = NciHalTrace.summarize(logs, commandMs)
        assertTrue(report.contains("SNAPSHOT_VALID_FRAMES=2"))
        assertTrue(report.contains("ALL_CAPTURED_HAL_TX=0 HAL_RX=2 PROPRIETARY_RX_NOTIFICATIONS=2"))
        assertTrue(report.contains("OUTSIDE_WINDOW_BEFORE=0 AFTER=2"))
        assertTrue(report.contains("FIRST_DELTA_MS=7816"))
        assertTrue(report.contains("IN_WINDOW_FRAMES=0"))
        assertTrue(report.contains("ONLY_POST_WINDOW_HAL_FRAMES_EARLIER_TX_MAY_HAVE_BEEN_EVICTED"))
        assertFalse(report.contains(packet))
    }

    @Test fun emptyLogIsNotTreatedAsHardwareFailureProof() {
        val report = NciHalTrace.summarize("", commandMs)
        assertTrue(report.contains("SNAPSHOT_VALID_FRAMES=0"))
        assertTrue(report.contains("NO_HAL_FRAMES_IN_COMMAND_WINDOW_OR_BUFFER_TRUNCATED"))
    }
}
