package com.yagay.YNFC;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single policy contract for NFC UI and in-process LSPosed runtime.
 * Desired user mode and Java/native evidence must not be conflated.
 */
public final class NfcControlPolicy {
    public enum Mode { NORMAL, HCE_ONLY }
    private volatile Boolean desiredBlock;
    private final AtomicLong revision = new AtomicLong();

    public static Mode mode(boolean persistentlyBlocked) {
        return persistentlyBlocked ? Mode.HCE_ONLY : Mode.NORMAL;
    }

    public static boolean canRead(Mode mode, boolean uidSimulationActive) {
        return mode == Mode.NORMAL && !uidSimulationActive;
    }

    public static boolean shouldSuppressPolling(Mode mode, int technologyMask) {
        return mode == Mode.HCE_ONLY && technologyMask != 0;
    }

    /**
     * A routing change can stop/start RF discovery. Do not issue an automatic
     * refresh if the controller is already in the desired Java state, the
     * OEM state is unobservable, or an automatic attempt has already run in
     * this controller epoch. Explicit preference changes are still supported.
     */
    public static boolean shouldSkipRoutingRefresh(boolean blocked, int currentMask,
                                                   boolean automatic, boolean attemptedAuto) {
        if (blocked && currentMask == 0) return true;
        if (!automatic) return false;
        if (!blocked || attemptedAuto) return true;
        return currentMask < 0;
    }

    public Boolean desired() { return desiredBlock; }

    /** True only for a real state change; null means not initialized yet. */
    public synchronized boolean setDesired(boolean disabled) {
        Boolean previous = desiredBlock;
        desiredBlock = disabled;
        return previous == null || previous != disabled;
    }

    public long nextRevision() { return revision.incrementAndGet(); }
    public boolean isCurrent(long value) { return revision.get() == value; }
    public void invalidateRefresh() { revision.incrementAndGet(); }
}
