package com.yagay.suite.api;

import android.os.SystemClock;

/** Shared freshness rules for cross-process health reports and diagnostics. */
public final class RuntimeHealth {
    private static final long CLOCK_SKEW_MS = 5_000L;
    private static final long BOOT_EPOCH_TOLERANCE_MS = 10_000L;

    private RuntimeHealth() {}

    /** Indicates only a recent event; it is not proof that a Hook is installed or still alive. */
    public static boolean isRecentWallClock(long eventEpochMillis, long maxAgeMillis) {
        if (eventEpochMillis <= 0L || maxAgeMillis <= 0L) return false;
        long age = System.currentTimeMillis() - eventEpochMillis;
        return age >= -CLOCK_SKEW_MS && age <= maxAgeMillis;
    }

    /** Periodic health signals must be recent and produced since this Android boot. */
    public static boolean isRecentFromCurrentBoot(long heartbeatEpochMillis, long maxAgeMillis) {
        if (!isRecentWallClock(heartbeatEpochMillis, maxAgeMillis)) return false;
        long bootEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        return heartbeatEpochMillis >= bootEpoch - BOOT_EPOCH_TOLERANCE_MS;
    }
}
