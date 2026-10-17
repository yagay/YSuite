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

    /**
     * Google live word-list callback explicitly projects the selected word
     * rectangle through TextSelectionView onto screen coordinates. Only
     * these verified screen rectangles may anchor YFloat's text toolbar.
     * A generic semantic snapshot or image-local crop rectangle cannot.
     */
    public static boolean mayAnchorTextMenu(boolean imageInputProbe,String detail) {
        if(!imageInputProbe)return true;
        return detail!=null&&detail.contains("liveFilter=accepted")
                &&detail.contains(" space=")
                &&detail.contains(" screen=");
    }

    /** A selected OCR result is successful even if Lens later exits via Back. */
    public static boolean workflowSucceeded(boolean screenshotSuccess,boolean nativeTextReceived) {
        return screenshotSuccess||nativeTextReceived;
    }

    private GoogleLensShareEventPolicy() {}
}
