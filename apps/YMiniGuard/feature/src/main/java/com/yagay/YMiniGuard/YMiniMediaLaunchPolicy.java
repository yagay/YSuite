package com.yagay.YMiniGuard;

/**
 * The OnePlus/OxygenOS 17 media panel opens the notification owner's main
 * launcher even when MediaSession.sessionActivity points at a target app.
 * Distinguish this path from a normal launcher tap without any new hooks.
 */
public final class YMiniMediaLaunchPolicy {
    private YMiniMediaLaunchPolicy() {}

    public static boolean shouldRedirect(
            boolean mainAction, boolean launcherCategory, boolean ownMainActivity,
            boolean newTask, boolean resetTask, String launchingPackage) {
        if (!mainAction || !launcherCategory || !ownMainActivity || !newTask || resetTask)
            return false;
        // Android often redacts the caller for cross-UID activity launches.
        // A null identity is allowed only when the OEM's distinctive launch
        // flags match, and the caller independently verifies a live YMini card.
        return launchingPackage == null
                || "com.android.systemui".equals(launchingPackage)
                || "com.oplus.systemui".equals(launchingPackage);
    }
}
