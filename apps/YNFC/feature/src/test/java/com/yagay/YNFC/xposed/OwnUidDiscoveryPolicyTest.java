package com.yagay.YNFC.xposed;

import com.android.nfc.NfcDiscoveryParameters;
import org.junit.Test;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

public class OwnUidDiscoveryPolicyTest {
    @Test public void unknownParametersFailClosed() {
        assertEquals("UNSUPPORTED_DISCOVERY_PARAMS",
                OwnUidDiscoveryPolicy.classify(null));
        assertEquals("UNSUPPORTED_DISCOVERY_PARAMS",
                OwnUidDiscoveryPolicy.classify(new Object()));
    }

    @Test public void hostRoutingEnabledNotReaderAllowsPreDiscoveryWrite() {
        assertEquals("HOST_LISTEN_ELIGIBLE",
                OwnUidDiscoveryPolicy.classify(new NfcDiscoveryParameters()));
    }

    @Test public void hostRoutingDisabledCannotConsumeReassertThrottle() throws Exception {
        NfcDiscoveryParameters params = new NfcDiscoveryParameters();
        Field host = params.getClass().getDeclaredField("mEnableHostRouting");
        host.setAccessible(true);
        host.setBoolean(params, false);
        assertEquals("HOST_ROUTING_DISABLED",
                OwnUidDiscoveryPolicy.classify(params));
    }

    @Test public void readerModeMustNotRewriteNfcAListenIdentity() throws Exception {
        NfcDiscoveryParameters params = new NfcDiscoveryParameters();
        Field reader = params.getClass().getDeclaredField("mEnableReaderMode");
        reader.setAccessible(true);
        reader.setBoolean(params, true);
        assertEquals("READER_MODE_ACTIVE",
                OwnUidDiscoveryPolicy.classify(params));
    }
}
