package com.yagay.YFloat;

import com.yagay.yui.YOverlayTokens;
import com.yagay.yui.YOverlayUi;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YUiGeometry;
import com.google.android.material.button.MaterialButton;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Shared geometry and widget helpers for every result surface. */
final class ResultUi {
    /** Result panel uses the same live row/button metrics as normal YUI surfaces. */
    static int titleHeight(Context c) {
        return YView.rowHeight(YOverlayUi.scopedContext(c, "yfloat"));
    }

    static int actionHeight(Context c) {
        return YOverlayUi.actionHeightPx(c, "yfloat");
    }

    static int verticalPadding(Context c) {
        return YView.controlGap(YOverlayUi.scopedContext(c, "yfloat")) * 2;
    }

    static LinearLayout box(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(YOverlayUi.contentPaddingPx(c, "yfloat"), YView.controlGap(c),
                YOverlayUi.contentPaddingPx(c, "yfloat"), YView.controlGap(c));
        box.setBackground(popupBackground(c,
                YOverlayTokens.resultSurface(YOverlayUi.scopedContext(c, "yfloat"))));
        // Do not use platform elevation here. The result card fills the dialog window closely,
        // so Android clips the elevation shadow at the rectangular window bounds and leaves
        // square dark corners outside the rounded surface.
        box.setElevation(0f);
        box.setTranslationZ(0f);
        box.setStateListAnimator(null);
        final float radius = YOverlayUi.cornerRadiusPx(c, "yfloat");
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(android.view.View view, android.graphics.Outline outline) {
                if (view.getWidth() <= 0 || view.getHeight() <= 0) return;
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        box.setClipToOutline(true);
        return box;
    }

    static android.graphics.drawable.Drawable popupBackground(Context c, int color) {
        return YOverlayUi.panelBackground(c, "yfloat", color, YOverlayTokens.outline(c));
    }

    static TextView heading(Context c, String text) {
        TextView title = new TextView(YOverlayUi.scopedContext(c, "yfloat"));
        title.setText(text == null ? "" : text);
        title.setTextColor(YOverlayTokens.textPrimary(YOverlayUi.scopedContext(c, "yfloat")));
        YView.styleSectionTitle(title);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    /**
     * The screenshot/result dialog always presents OCR, Copy, Save, Close in
     * one horizontal action strip. The normal selection popup is unrelated.
     * If translated labels cannot fit, scroll horizontally instead of stacking.
     */
    static LinearLayout actionRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static HorizontalScrollView actionViewport(Context c, LinearLayout row) {
        HorizontalScrollView viewport = new HorizontalScrollView(c);
        viewport.setFillViewport(true);
        viewport.setHorizontalScrollBarEnabled(false);
        viewport.setOverScrollMode(android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        viewport.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return viewport;
    }

    static void addResultActions(LinearLayout row,
                                 Button ocr, Button copy, Button save, Button close) {
        for (Button button : new Button[]{ocr, copy, save, close}) {
            // Retain YUI's button styling, insets and minimum touch size.
            button.setSingleLine(true);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(button, cell);
        }
    }

    static Button button(Context c, String text) {
        return YViewLayout.secondaryButton(YOverlayUi.scopedContext(c, "yfloat"), text);
    }

    static Rect usableBounds(Context c) {
        return ScreenGeometry.usableBounds(c);
    }

    static int standardWidth(Context c, Rect usable) {
        return YUiGeometry.overlayResultWidthPx(c, "yfloat", usable.width());
    }

    static int standardMaxHeight(Context c, Rect usable) {
        return YUiGeometry.overlayResultHeightPx(c, "yfloat", usable.height());
    }

    static int imageHeight(Context c, Bitmap image, int width, int maxHeight) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return 0;
        int innerWidth = Math.max(1, width - YOverlayUi.contentPaddingPx(c, "yfloat") * 2);
        int h = Math.round(innerWidth * (image.getHeight() / (float) image.getWidth()));
        return clamp(h, 1, Math.max(1, maxHeight));
    }

    static int dp(Context c, int v) {
        return YOverlayTokens.dp(c, v);
    }

    static int clamp(int v, int min, int max) {
        return ScreenGeometry.clamp(v, min, max);
    }

    private ResultUi() {}
}
