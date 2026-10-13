package com.yagay.YFloat;

/** Ignores short-lived empty Google OCR updates only at a terminal text handoff. */
final class GoogleTextHandoffPolicy {
    static final long RECOVERY_WINDOW_MS = 2_000L;

    static boolean canRecover(String lastText, long lastAtElapsed, long nowElapsed) {
        return lastText != null && !lastText.isBlank() && lastAtElapsed > 0
                && nowElapsed >= lastAtElapsed
                && nowElapsed - lastAtElapsed <= RECOVERY_WINDOW_MS;
    }

    private GoogleTextHandoffPolicy() { }
}
