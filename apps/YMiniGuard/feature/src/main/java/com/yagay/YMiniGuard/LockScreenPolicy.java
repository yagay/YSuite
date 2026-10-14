package com.yagay.YMiniGuard;

/**
 * Keyguard visibility is independent of process/background playback protection.
 * This policy is intentionally pure to prevent accidentally treating a retained
 * background task as a visible window in future SystemUI/OPlus Hook changes.
 */
final class LockScreenPolicy {
    static boolean allowSystemPause(boolean uiSleeping) {
        return uiSleeping;
    }

    static boolean keepWindowVisible(boolean foregroundSelected,
                                     boolean backgroundSelected,
                                     boolean nativeOplusWindow,
                                     boolean lockKeepAlive,
                                     boolean edgeHung,
                                     boolean edgeMinimizeRequested) {
        return foregroundSelected && !backgroundSelected
                && (nativeOplusWindow || lockKeepAlive
                    || edgeHung || edgeMinimizeRequested);
    }

    private LockScreenPolicy() {}
}
