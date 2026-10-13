package com.yagay.YFloat.hook;

import android.graphics.Rect;

/**
 * One canonical coordinate conversion. The testable math has no dependency
 * on Android View, Rect or Google App classes. Android only supplies inputs
 * and converts the immutable result for the presentation layer.
 */
final class GoogleSelectionCoordinateSpace {
    static final class Values {
        final int left, top, right, bottom;
        final String space;
        Values(int left, int top, int right, int bottom, String space) {
            this.left = left; this.top = top;
            this.right = right; this.bottom = bottom;
            this.space = space;
        }
        boolean valid() { return right > left && bottom > top; }
    }

    static Values project(int left, int top, int right, int bottom,
                          int width, int height, int animatedX, int animatedY,
                          boolean hasStableViewport,
                          int stableLeft, int stableTop, int stableRight, int stableBottom) {
        if (right <= left || bottom <= top || width <= 0 || height <= 0) {
            return new Values(0, 0, 0, 0, "invalid");
        }
        boolean stable = hasStableViewport && stableRight > stableLeft
                && stableBottom > stableTop
                && Math.abs(width - (stableRight - stableLeft)) <= 12
                && Math.abs(height - (stableBottom - stableTop)) <= 12;
        int offsetX = stable ? stableLeft : animatedX;
        int offsetY = stable ? stableTop : animatedY;
        int clipLeft = offsetX;
        int clipTop = offsetY;
        int clipRight = stable ? stableRight : animatedX + width;
        int clipBottom = stable ? stableBottom : animatedY + height;
        int l = Math.max(clipLeft, left + offsetX);
        int t = Math.max(clipTop, top + offsetY);
        int r = Math.min(clipRight, right + offsetX);
        int b = Math.min(clipBottom, bottom + offsetY);
        return new Values(l, t, r, b,
                r > l && b > t ? stable ? "stable_decor" : "local_view" : "outside");
    }

    static final class Result {
        final Rect bounds;
        final String space;
        Result(Rect bounds, String space) {
            this.bounds = bounds;
            this.space = space;
        }
    }

    static Result map(Rect local, int width, int height, int animatedX, int animatedY,
                      Rect stableViewport) {
        if (local == null) return new Result(null, "invalid");
        boolean exists = stableViewport != null && !stableViewport.isEmpty();
        Values values = project(local.left, local.top, local.right, local.bottom,
                width, height, animatedX, animatedY, exists,
                exists ? stableViewport.left : 0,
                exists ? stableViewport.top : 0,
                exists ? stableViewport.right : 0,
                exists ? stableViewport.bottom : 0);
        return new Result(values.valid()
                ? new Rect(values.left, values.top, values.right, values.bottom) : null,
                values.space);
    }

    private GoogleSelectionCoordinateSpace() {}
}
