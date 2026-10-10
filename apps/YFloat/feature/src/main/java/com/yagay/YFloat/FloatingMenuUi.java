package com.yagay.YFloat;

import com.yagay.yui.YOverlayTokens;
import com.yagay.yui.YOverlayUi;
import com.yagay.yui.YView;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Single visual factory for YFloat floating text/image action menus. */
final class FloatingMenuUi {
    static LinearLayout root(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(YOverlayUi.menuBackground(c, "yfloat",
                YOverlayTokens.menuSurface(YOverlayUi.scopedContext(c, "yfloat"))));
        root.setElevation(YOverlayUi.elevationPx(c, "yfloat"));
        root.setClipToOutline(false);
        root.setClickable(true);
        return root;
    }

    static TextView action(Context c, String text, int minWidthDp) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(dp(c, minWidthDp));
        tv.setPadding(YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2),
                YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2));
        return tv;
    }

    static TextView row(Context c, String text, Drawable icon) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tv.setPadding(YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2),
                YOverlayUi.contentPaddingPx(c, "yfloat"),
                Math.max(1, YView.controlGap(c) / 2));
        tv.setSingleLine(false);
        tv.setMaxLines(Integer.MAX_VALUE);
        tv.setEllipsize(null);
        if (icon != null) {
            int s = YView.iconSize(c);
            icon.setBounds(0, 0, s, s);
            tv.setCompoundDrawablePadding(YView.controlGap(c));
            tv.setCompoundDrawables(icon, null, null, null);
        }
        return tv;
    }

    static TextView secondaryRow(Context c, String text) {
        TextView tv = row(c, text, null);
        tv.setTextColor(YOverlayTokens.menuSecondaryText(YOverlayUi.scopedContext(c, "yfloat")));
        return tv;
    }

    private static TextView baseText(Context c, String text) {
        TextView tv = new TextView(YOverlayUi.scopedContext(c, "yfloat"));
        tv.setText(text == null ? "" : text);
        tv.setTextColor(YOverlayTokens.menuPrimaryText(YOverlayUi.scopedContext(c, "yfloat")));
        YView.styleBody(tv);
        tv.setBackground(YOverlayUi.actionRipple(c, "yfloat"));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setMinHeight(Math.max(YView.rowHeight(YOverlayUi.scopedContext(c, "yfloat")),
                YOverlayUi.actionHeightPx(c, "yfloat")));
        tv.setIncludeFontPadding(true);
        return tv;
    }

    static int dp(Context c, int value) { return YOverlayTokens.dp(c, value); }

    private FloatingMenuUi() {}
}
