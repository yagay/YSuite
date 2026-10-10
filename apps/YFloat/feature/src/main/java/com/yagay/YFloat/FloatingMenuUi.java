package com.yagay.YFloat;

import com.yagay.yui.YOverlayTokens;
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
    static LinearLayout root(Context c, int radiusDp) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(YOverlayTokens.menuSurface(c), dp(c, radiusDp)));
        root.setElevation(dp(c, 10));
        root.setClipToOutline(false);
        root.setClickable(true);
        return root;
    }

    static TextView action(Context c, String text, int minWidthDp) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(dp(c, minWidthDp));
        tv.setPadding(YView.controlGap(c), Math.max(1, YView.controlGap(c) / 2),
                YView.controlGap(c), Math.max(1, YView.controlGap(c) / 2));
        return tv;
    }

    static TextView row(Context c, String text, Drawable icon) {
        TextView tv = baseText(c, text);
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tv.setPadding(YView.cardPadding(c), Math.max(1, YView.controlGap(c) / 2),
                YView.cardPadding(c), Math.max(1, YView.controlGap(c) / 2));
        tv.setSingleLine(false);
        tv.setMaxLines(Integer.MAX_VALUE);
        tv.setEllipsize(null);
        if (icon != null) {
            int s = dp(c, 24);
            icon.setBounds(0, 0, s, s);
            tv.setCompoundDrawablePadding(YView.controlGap(c));
            tv.setCompoundDrawables(icon, null, null, null);
        }
        return tv;
    }

    static TextView secondaryRow(Context c, String text) {
        TextView tv = row(c, text, null);
        tv.setTextColor(YOverlayTokens.menuSecondaryText(c));
        return tv;
    }

    private static TextView baseText(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text == null ? "" : text);
        tv.setTextColor(YOverlayTokens.menuPrimaryText(c));
        tv.setTextSize(14f * YView.fontPercent(c) / 100f);
        tv.setBackground(ripple(c));
        tv.setClickable(true);
        tv.setFocusable(true);
        tv.setMinHeight(YView.rowHeight(c));
        tv.setIncludeFontPadding(true);
        return tv;
    }

    private static Drawable rounded(int color, float radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(radius);
        return gd;
    }

    private static Drawable ripple(Context c) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(Color.TRANSPARENT);
        content.setCornerRadius(999f);
        return new RippleDrawable(ColorStateList.valueOf(YOverlayTokens.menuRipple(c)), content, null);
    }

    static int dp(Context c, int value) { return YOverlayTokens.dp(c, value); }

    private FloatingMenuUi() {}
}
