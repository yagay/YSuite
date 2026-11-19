package com.yagay.YMiniGuard;

/**
 * Keyguard visibility is independent of process/background playback protection.
 * This policy is intentionally pure to prevent accidentally treating a retained
 * background task as a visible window in future SystemUI/OPlus Hook changes.
 */
final class LockScreenPolicy {
    /** A lock transition must retain the protected app's playback state.
     *  OPlus visibility/sleep hooks independently allow the surface to hide.
     */
    static boolean preservePlaybackOnLock(boolean uiSleeping, boolean keyguardShowing) {
        return uiSleeping || keyguardShowing;
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
