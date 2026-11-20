package com.yagay.YFloat;

/** Pure overlay layer decision shared by screenshot, crop, Circle, menus and icons. */
final class FlOverlayLayerPolicy {
    static boolean elevate(String tag, boolean highLayerEnabled) {
        if (!highLayerEnabled) return false;
        // Edge wake strips are invisible full-height touch targets. Keeping
        // these below SystemUI avoids intercepting security/navigation gestures.
        return tag == null || !tag.startsWith("edge_wake_");
    }

    /**
     * High layer is a user choice, not a device-model blacklist. If a real
     * misrouted touch is observed, prefer the stable application host until
     * the user explicitly retries the accessibility window.
     */
    static boolean preferAccessibilityIconHost(boolean accessibilityAvailable,
                                               boolean applicationOverlayAllowed,
                                               boolean highLayerRequested,
                                               boolean runtimeTouchMismatch) {
        return accessibilityAvailable && (!applicationOverlayAllowed
                || (highLayerRequested && !runtimeTouchMismatch));
    }

    /**
     * Catch any other OEM with an incorrect touchable-input coordinate frame.
     * A normal finger hit on the enlarged edge handle can extend slightly
     * outside the icon, but not hundreds of pixels away.
     */
    static boolean touchOutsideIconBounds(
            float rawX, float rawY, int ownerX, int ownerY,
            int iconWidth, int iconHeight) {
        float marginX = Math.max(32f, iconWidth * 0.75f);
        float marginY = Math.max(32f, iconHeight * 0.75f);
        return rawX < ownerX - marginX
                || rawX > ownerX + iconWidth + marginX
                || rawY < ownerY - marginY
                || rawY > ownerY + iconHeight + marginY;
    }

    private FlOverlayLayerPolicy() {}
}
