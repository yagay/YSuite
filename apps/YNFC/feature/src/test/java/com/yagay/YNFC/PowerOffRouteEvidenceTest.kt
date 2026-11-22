package com.yagay.YNFC

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOffRouteEvidenceTest {
    @Test fun nfcUidNormalizationAcceptsOnlySupportedLengths() {
        assertEquals("A1B2C3D4", PowerOffRouteEvidence.normalizedUid("a1:b2:c3:d4"))
        assertEquals("01020304050607", PowerOffRouteEvidence.normalizedUid("01 02 03 04 05 06 07"))
        assertEquals("0102030405060708090A", PowerOffRouteEvidence.normalizedUid("0102030405060708090a"))
        assertNull(PowerOffRouteEvidence.normalizedUid("ABC"))
        assertNull(PowerOffRouteEvidence.normalizedUid("0011ZZ44"))
    }

    @Test fun parsesNxpConfigAndSwitchOffBitWithoutClaimingVerifiedUid() {
        val sample = """
            DEFAULT_AID_ROUTE=0x01
            DEFAULT_AID_PWR_STATE=0x39
            DEFAULT_MIFARE_CLT_ROUTE=0x01
            DEFAULT_MIFARE_CLT_PWR_STATE=0x3B
            DEFAULT_DESFIRE_PWR_STATE=0x3B
            RANDOM_OTHER_CONF=0xFF
        """.trimIndent()
        val fields = PowerOffRouteEvidence.routeFields(sample)
        assertEquals(5, fields.size)
        assertEquals("0x01", fields["DEFAULT_MIFARE_CLT_ROUTE"])
        assertTrue(PowerOffRouteEvidence.switchOffPowerBit(fields["DEFAULT_MIFARE_CLT_PWR_STATE"])!!)
        assertFalse(PowerOffRouteEvidence.switchOffPowerBit(fields["DEFAULT_AID_PWR_STATE"])!!)
        assertNull(PowerOffRouteEvidence.switchOffPowerBit("invalid"))
        assertNull(PowerOffRouteEvidence.switchOffPowerBit(null))
    }

    @Test fun reportsMissingCaptureWithoutPretendingRoutesAreSame() {
        val baseline = PowerOffRouteEvidence.routeFields("DEFAULT_MIFARE_CLT_PWR_STATE=0x3B")
        val changed = PowerOffRouteEvidence.routeFields("DEFAULT_MIFARE_CLT_PWR_STATE=0x39")
        assertEquals("NOT_COMPARABLE", PowerOffRouteEvidence.compareFields(baseline, emptyMap()))
        assertEquals("UNCHANGED", PowerOffRouteEvidence.compareFields(baseline, baseline))
        assertTrue(PowerOffRouteEvidence.compareFields(baseline, changed)
            .contains("DEFAULT_MIFARE_CLT_PWR_STATE:0x3B->0x39"))
    }
}
