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
     * On the logged OnePlus CPH2747 / Android 17 build, a small 2032
     * accessibility icon can be assigned a touchable region near screen
     * centre even while getLocationOnScreen() reports the requested right
     * edge (e.g. raw 719,1520 vs icon 1222,668).
     *
     * Keep persistent touchable controls in the stable 2038 host on this
     * combination. Screenshot/crop/crosshair/menus still honor 2032.
     * Do not generalize this OEM-specific runtime bug to all Android phones.
     */
    static boolean requiresStableIconHost(String manufacturer, String model,
                                          String device, int sdkInt) {
        if (sdkInt < 35 || manufacturer == null) return false;
        String vendor = manufacturer.toLowerCase(java.util.Locale.ROOT);
        if (!vendor.contains("oneplus")) return false;
        return "CPH2747".equalsIgnoreCase(model)
                || "CPH2747".equalsIgnoreCase(device);
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
