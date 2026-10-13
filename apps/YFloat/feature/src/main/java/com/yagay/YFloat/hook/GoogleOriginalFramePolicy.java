package com.yagay.YFloat.hook;

/** Bounded size/geometry validation for Google-owned screenshot sources. */
final class GoogleOriginalFramePolicy {
    static boolean validDecodedSize(int w, int h) {
        return w >= 320 && h >= 480 && (long) w * h <= 16_000_000L;
    }

    static boolean matchesFrozenView(int bitmapWidth, int bitmapHeight,
                                     int viewWidth, int viewHeight) {
        if (!validDecodedSize(bitmapWidth, bitmapHeight)
                || viewWidth <= 0 || viewHeight <= 0) return false;
        double ratio = (double) bitmapWidth / bitmapHeight;
        double expected = (double) viewWidth / viewHeight;
        return Math.abs(ratio / expected - 1d) <= 0.18d
                && bitmapWidth >= viewWidth * 0.55d
                && bitmapHeight >= viewHeight * 0.55d;
    }

    private GoogleOriginalFramePolicy() {}
}
