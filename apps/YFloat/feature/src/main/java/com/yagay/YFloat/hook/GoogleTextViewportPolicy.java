package com.yagay.YFloat.hook;

/** Only text selection may lock the Google native frozen-image transform. */
final class GoogleTextViewportPolicy {
    static boolean shouldNormalize(boolean textSelectionActive,
                                   float scaleX, float scaleY,
                                   float translationX, float translationY) {
        return textSelectionActive
                && (Math.abs(scaleX - 1f) >= 0.0005f
                || Math.abs(scaleY - 1f) >= 0.0005f
                || Math.abs(translationX) >= 0.5f
                || Math.abs(translationY) >= 0.5f);
    }

    private GoogleTextViewportPolicy() { }
}
