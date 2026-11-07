package com.yagay.YNFC.xposed;

import java.lang.reflect.Method;

/**
 * Fail-closed, read-only predicate for native UID reassertion at the system's
 * natural enableDiscovery() boundary. Do not infer listen eligibility from
 * 'RF discovery started': NFC polling and off-host routing may still be active.
 *
 * This does not change discovery parameters, payment AIDs, APDUs, or eSE routes.
 * An eligible return is only a Java-policy hint, NOT hardware RF proof.
 */
final class OwnUidDiscoveryPolicy {
    private OwnUidDiscoveryPolicy() { }

    static String classify(Object params) {
        if (params == null || !NfcPollingGuard.supportsType(params.getClass())) {
            return "UNSUPPORTED_DISCOVERY_PARAMS";
        }
        try {
            // Host routing is a necessary condition for the YNFC host UID
            // emulation path. When OEM disables host listen, leave the
            // NFCC/secure element alone even if discovery stays running.
            if (!NfcPollingGuard.hostRouting(params)) {
                return "HOST_ROUTING_DISABLED";
            }
            Method reader = params.getClass().getDeclaredMethod("shouldEnableReaderMode");
            reader.setAccessible(true);
            Object isReader = reader.invoke(params);
            if (!(isReader instanceof Boolean)) return "READER_MODE_UNKNOWN";
            if ((Boolean) isReader) return "READER_MODE_ACTIVE";
            return "HOST_LISTEN_ELIGIBLE";
        } catch (Throwable unavailable) {
            return "DISCOVERY_POLICY_UNVERIFIED";
        }
    }
}
