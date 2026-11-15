package com.yagay.YFloat;

/** Pure overlay layer decision shared by screenshot, crop, Circle, menus and icons. */
final class FlOverlayLayerPolicy {
    static boolean elevate(String tag, boolean highLayerEnabled) {
        if (!highLayerEnabled) return false;
        // Edge wake strips are invisible full-height touch targets. Keeping
        // these below SystemUI avoids intercepting security/navigation gestures.
        return tag == null || !tag.startsWith("edge_wake_");
    }

    private FlOverlayLayerPolicy() {}
}
