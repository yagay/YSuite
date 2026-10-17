package com.yagay.YFloat;

/**
 * Exact Google Lens ACTION_SEND bitmap experiments are observation-only.
 *
 * The user-selected crop has image-local coordinates, not full-screen CTS
 * coordinates. A Lens region gesture must never produce a YFloat screenshot
 * result or make YFloat freeze Google's viewport. Selected OCR text remains
 * eligible for the regular YFloat text-result bridge.
 */
public final class GoogleLensShareEventPolicy {
    public static boolean mayForwardRegion(boolean imageInputProbe) {
        return !imageInputProbe;
    }

    public static boolean mayProtectViewport(boolean imageInputProbe) {
        return !imageInputProbe;
    }

    private GoogleLensShareEventPolicy() {}
}
