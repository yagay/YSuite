package com.yagay.YNFC.xposed;

import static org.junit.Assert.*;
import org.junit.Test;

/** Backend selection must never silently fall back to privileged ColorOS calls. */
public final class FileInterceptBackendTest {
    @Test public void interceptBackendIsExclusive() {
        SimConfig config = new SimConfig(true, true, "C1B0BC1B",
                "08", "0004", "INTERCEPT", true, 123L,
                0L, 0L, "APPLY", "PENDING", 0, 1L);
        assertTrue(config.useInterceptTypeA());
        assertFalse(config.useVendorTypeA());
        assertEquals("INTERCEPT", config.backend);
        assertTrue(config.withControllerEpoch(2L).useInterceptTypeA());
    }

    @Test public void vendorAndRfDoNotBecomeIntercept() {
        SimConfig vendor = new SimConfig(true, true, "C1B0BC1B",
                "08", "0004", "VENDOR", false, 123L,
                0L, 0L, "APPLY", "PENDING", 0, 1L);
        SimConfig rf = new SimConfig(true, true, "C1B0BC1B",
                "08", "0004", "RF", false, 123L,
                0L, 0L, "APPLY", "PENDING", 0, 1L);
        assertTrue(vendor.useVendorTypeA());
        assertFalse(vendor.useInterceptTypeA());
        assertFalse(rf.useVendorTypeA());
        assertFalse(rf.useInterceptTypeA());
    }
}
