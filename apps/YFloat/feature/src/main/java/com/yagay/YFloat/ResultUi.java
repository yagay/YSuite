package com.yagay.YFloat;

import com.yagay.yui.YOverlayTokens;
import com.yagay.yui.YOverlayUi;
import com.yagay.yui.YView;
import com.google.android.material.button.MaterialButton;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared geometry and widget helpers for every result surface. */
final class ResultUi {
    static final int OUTER_MARGIN_DP = 12;
    static final int BOX_HPAD_DP = 14;
    static final int TITLE_H_DP = 38;
    static final int ACTION_H_DP = 50;
    static final int ROOT_VPAD_DP = 16;

    /** One visible corner radius for Screenshot / View / OCR result popups. */
    static final int POPUP_RADIUS_DP = 22;

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

    static LinearLayout actionRow(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    static Button button(Context c, String text) {
        MaterialButton b = new MaterialButton(YOverlayUi.scopedContext(c, "yfloat"));
        b.setText(text);
        YView.styleSecondaryButton(b);
        return b;
    }

    static Rect usableBounds(Context c) {
        return ScreenGeometry.usableBounds(c);
    }

    static int standardWidth(Context c, Rect usable) {
        int margin = dp(c, OUTER_MARGIN_DP);
        int available = Math.max(1, usable.width() - margin * 2);
        int preferred = Math.round(dp(c, 410) *
                (YOverlayUi.appearance(c, "yfloat").getContentWidthPercent() / 100f));
        return Math.min(available, Math.max(1, Math.max(dp(c, 220), preferred)));
    }

    static int standardMaxHeight(Context c, Rect usable) {
        int byScreen = Math.round(usable.height() * .52f);
        int hardCap = dp(c, 430);
        int available = Math.max(dp(c, 170), usable.height() - dp(c, OUTER_MARGIN_DP * 2));
        return Math.min(available, Math.max(dp(c, 190), Math.min(byScreen, hardCap)));
    }

    static int imageHeight(Context c, Bitmap image, int width, int maxHeight) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return 0;
        int innerWidth = Math.max(dp(c, 120), width - dp(c, BOX_HPAD_DP * 2));
        int h = Math.round(innerWidth * (image.getHeight() / (float) image.getWidth()));
        return clamp(h, dp(c, 72), Math.max(dp(c, 72), maxHeight));
    }

    static int dp(Context c, int v) {
        return YOverlayTokens.dp(c, v);
    }

    static int clamp(int v, int min, int max) {
        return ScreenGeometry.clamp(v, min, max);
    }

    private ResultUi() {}
}
