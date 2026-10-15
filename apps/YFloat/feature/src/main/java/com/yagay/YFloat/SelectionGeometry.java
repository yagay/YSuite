package com.yagay.YFloat;

import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

/** Single owner for View/TextView selection geometry used by result and floating menus. */
final class SelectionGeometry {
    static Rect forView(View view) {
        return visibleScreenBounds(view);
    }

    /**
     * getLocalVisibleRect is in view-local coordinates, including clipping by
     * a parent ScrollView. Convert that rect with getLocationOnScreen before
     * comparing it with the actual selection bounds. getGlobalVisibleRect
     * cannot be treated as a physical-screen rect in a centered Dialog window.
     */
    private static Rect visibleScreenBounds(View view) {
        if (view == null || !view.isShown() || !view.isAttachedToWindow()) return null;
        try {
            Rect visible = new Rect();
            if (!view.getLocalVisibleRect(visible) || visible.isEmpty()) return null;
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            int[] screen = SelectionCoordinateMath.toScreen(
                    visible.left, visible.top, visible.right, visible.bottom,
                    location[0], location[1]);
            return new Rect(screen[0], screen[1], screen[2], screen[3]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Rect forTextSelection(TextView tv) {
        if (tv == null || tv.getText() == null || !tv.isShown()) return null;
        int a = tv.getSelectionStart();
        int b = tv.getSelectionEnd();
        if (a < 0 || b < 0 || a == b) return null;

        int lo = Math.max(0, Math.min(a, b));
        int hi = Math.min(tv.length(), Math.max(a, b));
        Layout layout = tv.getLayout();
        if (layout == null || lo >= hi) return null;

        try {
            int startLine = layout.getLineForOffset(lo);
            int endLine = layout.getLineForOffset(Math.max(lo, hi - 1));
            RectF local = new RectF();
            boolean hasGeometry = false;

            for (int line = startLine; line <= endLine; line++) {
                int lineStart = layout.getLineStart(line);
                int lineEnd = layout.getLineEnd(line);
                int segmentStart = Math.max(lo, lineStart);
                int segmentEnd = Math.min(hi, lineEnd);
                if (segmentStart >= segmentEnd) continue;

                float x1 = segmentStart <= lineStart
                        ? layout.getLineLeft(line)
                        : layout.getPrimaryHorizontal(segmentStart);
                float x2 = segmentEnd >= lineEnd
                        ? layout.getLineRight(line)
                        : layout.getPrimaryHorizontal(segmentEnd);
                float left = Math.min(x1, x2);
                float right = Math.max(x1, x2);
                if (right <= left) right = left + 1f;

                RectF lineRect = new RectF(left, layout.getLineTop(line),
                        right, layout.getLineBottom(line));
                if (!hasGeometry) {
                    local.set(lineRect);
                    hasGeometry = true;
                } else {
                    local.union(lineRect);
                }
            }

            if (!hasGeometry || local.isEmpty()) {
                return endpointAnchor(tv, layout, Math.max(lo, hi - 1));
            }

            int[] loc = new int[2];
            tv.getLocationOnScreen(loc);
            float dx = loc[0] + tv.getCompoundPaddingLeft() - tv.getScrollX();
            float dy = loc[1] + tv.getExtendedPaddingTop() - tv.getScrollY();
            Rect out = new Rect(
                    Math.round(local.left + dx),
                    Math.round(local.top + dy),
                    Math.round(local.right + dx),
                    Math.round(local.bottom + dy));

            Rect visible = visibleScreenBounds(tv);
            if (visible == null) return null;
            Rect clipped = new Rect(out);
            if (clipped.intersect(visible) && !clipped.isEmpty()) return clipped;
            // Do not show a popup at a synthetic "top-of-screen" fallback
            // when the actual selection has scrolled out of the dialog.
            return endpointAnchor(tv, layout, Math.max(lo, hi - 1));
        } catch (Throwable ignored) {
            return endpointAnchor(tv, layout, Math.max(lo, hi - 1));
        }
    }

    static Rect screenToLocal(View view, Rect screenBounds) {
        if (view == null || screenBounds == null || screenBounds.isEmpty()) return null;
        try {
            int[] loc = new int[2];
            view.getLocationOnScreen(loc);
            Rect out = new Rect(screenBounds);
            out.offset(-loc[0], -loc[1]);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Rect endpointAnchor(TextView tv, Layout layout, int offset) {
        if (tv == null || layout == null || tv.length() <= 0) return null;
        try {
            int safe = Math.max(0, Math.min(tv.length() - 1, offset));
            int line = layout.getLineForOffset(safe);
            float x = layout.getPrimaryHorizontal(safe);
            int[] loc = new int[2];
            tv.getLocationOnScreen(loc);
            float dx = loc[0] + tv.getCompoundPaddingLeft() - tv.getScrollX();
            float dy = loc[1] + tv.getExtendedPaddingTop() - tv.getScrollY();
            int screenX = Math.round(x + dx);
            Rect out = new Rect(screenX, Math.round(layout.getLineTop(line) + dy),
                    screenX + 2, Math.round(layout.getLineBottom(line) + dy));

            Rect visible = visibleScreenBounds(tv);
            if (visible == null || !out.intersect(visible)) return null;
            return out.isEmpty() ? null : out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private SelectionGeometry() {}
}
