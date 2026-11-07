package com.yagay.YNFC.xposed;

/**
 * Pure eligibility policy. Only a genuine controller invalidation can
 * initiate a one-shot modern NFCID1 reapplication. Discovery state changes
 * must not be interpreted as a controller reset.
 */
final class OwnNciLifecyclePolicy {
    private OwnNciLifecyclePolicy() { }

    static boolean sameDesiredCommand(SimConfig expected, SimConfig current) {
        return expected != null && current != null &&
                expected.initialized && current.initialized &&
                expected.active && current.active &&
                expected.generation > 0 && expected.generation == current.generation &&
                expected.controllerEpoch == current.controllerEpoch &&
                expected.uid != null && expected.uid.equalsIgnoreCase(current.uid) &&
                "RF".equals(current.backend) &&
                "APPLY".equals(current.commandAction) &&
                "UNVERIFIED".equals(current.commandStatus) &&
                current.handledGeneration == current.generation;
    }

    static boolean shouldRecover(SimConfig desired,
                                 HookConfigStore.OwnNciRfEvidence evidence,
                                 int pid, long invalidAt,
                                 long lastWriteInvalidAt, long lastWriteGeneration) {
        if (desired == null || evidence == null || !desired.initialized ||
                !desired.active || !"RF".equals(desired.backend) ||
                !"APPLY".equals(desired.commandAction) ||
                !"UNVERIFIED".equals(desired.commandStatus) ||
                desired.handledGeneration != desired.generation ||
                desired.generation <= 0 || desired.controllerEpoch <= 0 ||
                !"own-nci-0x33".equals(evidence.source) ||
                pid <= 0 || invalidAt <= 0) return false;
        if (lastWriteGeneration == desired.generation &&
                lastWriteInvalidAt >= invalidAt) return false;
        if (evidence.pid == pid &&
                evidence.controllerEpoch == desired.controllerEpoch) return false;
        if (desired.uid == null) return false;
        String uid = desired.uid.replaceAll("[\\s:.-]", "");
        return uid.matches("(?i)[0-9a-f]+") &&
                (uid.length() == 8 || uid.length() == 14 || uid.length() == 20);
    }
}
