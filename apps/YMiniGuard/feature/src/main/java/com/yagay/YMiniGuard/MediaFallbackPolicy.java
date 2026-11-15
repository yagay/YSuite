package com.yagay.YMiniGuard;

/**
 * OEM media sessions can be active with state=null and metadata=null.
 * Fallback is intentionally conservative: the selected session must be the
 * unique eligible package AND device audio playback must be active.
 * A synthetic card must never invent duration or current position.
 */
final class MediaFallbackPolicy {
    private MediaFallbackPolicy() {}

    static boolean shouldActivate(int uniqueEligiblePackages, boolean musicActive) {
        return uniqueEligiblePackages == 1 && musicActive;
    }
}
