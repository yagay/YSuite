package com.yagay.YFloat;

/**
 * UI-only exit policy. Neither Android navigation nor Google doHide is proof
 * that a pending screenshot bitmap may be cancelled. Only an active TEXT or
 * REGION selection has a toolbar/outline to remove immediately.
 */
final class GoogleV2VisualExitPolicy {
    static boolean canSuspend(GoogleV2State.Phase phase) {
        return phase==GoogleV2State.Phase.TEXT
                ||phase==GoogleV2State.Phase.REGION;
    }

    private GoogleV2VisualExitPolicy() {}
}
