package com.yagay.YNFC.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.nfc.NfcDiscoveryParameters;
import com.android.nfc.NfcService;
import com.android.nfc.dhimpl.TestNativeNfcManager;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

public class NfcDiscoveryCapabilityRegistryTest {
    @Test public void detectsAospComputeAndRoutingContract() {
        Method compute = NfcDiscoveryCapabilityRegistry.findCompute(
                NfcService.class, NfcDiscoveryParameters.class);
        assertNotNull(compute);
        assertEquals("computeDiscoveryParameters", compute.getName());
        assertTrue(NfcDiscoveryCapabilityRegistry.resolveRouting(NfcService.class).available());
    }

    @Test public void selectsOnlyShapeValidatedOemDiscoveryDrivers() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        List<NfcDiscoveryCapabilityRegistry.Candidate> drivers =
                NfcDiscoveryCapabilityRegistry.findDrivers(loader,
                        List.of(TestNativeNfcManager.class.getName(), "java.lang.String"),
                        NfcDiscoveryParameters.class);
        assertEquals(1, drivers.size());
        assertEquals("enableDiscovery", drivers.get(0).method.getName());
        assertFalse(NfcDiscoveryCapabilityRegistry.isDiscoveryDriver(
                TestNativeNfcManager.class.getDeclaredMethod("dangerousRefresh", int.class),
                NfcDiscoveryParameters.class));
    }
}
