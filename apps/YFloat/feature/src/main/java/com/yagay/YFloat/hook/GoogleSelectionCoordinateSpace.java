package com.yagay.YFloat.hook;

import android.graphics.Rect;

/**
 * Text OCR coordinates have only one owner. When Google's OCR text view spans
 * the original viewport, convert word-local Rects using the stationary decor
 * origin. An animated TextSelectionView origin must never change the global
 * position of a word in the immutable screen frame. Smaller views still use
 * their actual screen-space origin and are clipped to their viewport.
 */
final class GoogleSelectionCoordinateSpace {
    static final class Result {
        final Rect bounds;
        final String space;
        Result(Rect bounds, String space) {
            this.bounds = bounds == null ? null : new Rect(bounds);
            this.space = space;
        }
    }

    static Result map(Rect local, int width, int height, int animatedX, int animatedY,
                      Rect stableViewport) {
        if (local == null || local.isEmpty() || width <= 0 || height <= 0) {
            return new Result(null, "invalid");
        }
        boolean rootAligned = stableViewport != null && !stableViewport.isEmpty()
                && Math.abs(width - stableViewport.width()) <= 12
                && Math.abs(height - stableViewport.height()) <= 12;
        int originX = rootAligned ? stableViewport.left : animatedX;
        int originY = rootAligned ? stableViewport.top : animatedY;
        Rect result = new Rect(local);
        result.offset(originX, originY);
        Rect clip = rootAligned ? stableViewport
                : new Rect(animatedX, animatedY, animatedX + width, animatedY + height);
        if (!result.intersect(clip) || result.isEmpty()) {
            return new Result(null, "outside");
        }
        return new Result(result, rootAligned ? "stable_decor" : "local_view");
    }

    private GoogleSelectionCoordinateSpace() {}
}
