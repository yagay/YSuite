package com.yagay.YMiniGuard;

/**
 * Pure background transition classification, kept separate from system_server
 * hooks so OxygenOS lifecycle regressions can be unit-tested on the JVM.
 */
final class BackgroundPlaybackPolicy {
    private BackgroundPlaybackPolicy() {}

    /** WindowManager uses these reasons while an already-armed task is hidden. */
    static boolean isBackgroundTransition(String reason) {
        return reason != null
                && (reason.contains("makeInvisible")
                || reason.contains("setVisibility")
                || reason.contains("finishTransition")
                || reason.contains("pauseInRecentsAnim")
                || reason.contains("pauseBackTasks"));
    }

    static boolean isVisibilityHide(String reason) {
        return reason != null
                && (reason.contains("makeInvisible")
                || reason.contains("setVisibility"));
    }

    /**
     * Do not interpret a same-package resuming candidate as a real RESUMED
     * state during an already-protected background/recents transition.
     */
    static boolean shouldRetainArmedProtection(
            boolean backgroundProtected,
            String reason
    ) {
        return backgroundProtected && isBackgroundTransition(reason);
    }
}
