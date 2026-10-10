package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/** Shared FL drag-selection visuals. */
final class SelectionVisuals {
    /** FL normal selection is red. */
    static final int FL_ACTIVE_COLOR = 0xFFFF0000;
    /** FL confirmed/extractable selection is yellow. */
    static final int FL_CONFIRMED_COLOR = 0xFFFFFF00;

    static int frameColor(SelectionVisualState state) {
        return state == SelectionVisualState.READY ? FL_CONFIRMED_COLOR : FL_ACTIVE_COLOR;
    }

    static int edgeThicknessPx(Context c) {
        // Track/ready colors retain their semantic meaning; border geometry belongs to YUI.
        return Math.max(1, Math.round(YFloatOverlayStyle.selectionStroke(c)));
    }

    static void configureFramePaint(Context c, Paint frame, SelectionVisualState state) {
        frame.reset();
        frame.setAntiAlias(true);
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(frameColor(state));
        frame.setStrokeWidth(YFloatOverlayStyle.selectionStroke(c));
        frame.setStrokeJoin(Paint.Join.MITER);
    }

    /** Labels are YFloat-only helpers; keep them single-layer and unobtrusive. */
    static void configureTextPaint(Context c, Paint text, float sp) {
        text.reset();
        text.setAntiAlias(true);
        text.setStyle(Paint.Style.FILL);
        text.setColor(YFloatOverlayStyle.primaryText(c));
        text.setTextSize(YFloatOverlayStyle.textSize(c, sp));
    }

    static void drawFrame(Canvas c, Rect rect, Paint frame) {
        if (c == null || rect == null || rect.isEmpty()) return;
        c.drawRect(rect, frame);
    }

    static void drawText(Canvas c, String text, float x, float y, Paint fill) {
        if (c == null || text == null || text.isEmpty()) return;
        c.drawText(text, x, y, fill);
    }

    static void configureEdgePaint(Paint frame, SelectionVisualState state) {
        frame.reset();
        frame.setAntiAlias(false);
        frame.setStyle(Paint.Style.FILL);
        frame.setColor(frameColor(state));
    }

    static void drawEdge(Canvas c, int width, int height, Paint frame) {
        if (c == null || width <= 0 || height <= 0) return;
        c.drawRect(0, 0, width, height, frame);
    }

    private SelectionVisuals() {}
}
