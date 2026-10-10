package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Color;

import com.yagay.yui.YAppearance;
import com.yagay.yui.YAppearanceStore;
import com.yagay.yui.YOverlayTokens;

/**
 * The sole Java/Canvas bridge to YUI's live visual preferences.
 * These methods resolve the YFloat module override with global fallback; they never
 * write local visual preferences. Geometry/hit testing remains owned by each overlay.
 */
final class YFloatOverlayStyle {
    private static YAppearance appearance(Context c) {
        return new YAppearanceStore(c).appearance("yfloat");
    }

    static float dp(Context c, float value) {
        return value * c.getResources().getDisplayMetrics().density;
    }

    static float selectionStroke(Context c) {
        return dp(c, Math.max(0.5f, appearance(c).getFloatBorderWidthDp()));
    }

    static int selectionColor(Context c) {
        return new FloatSettings(c).circleBorderColor();
    }

    static int accent(Context c) {
        return YOverlayTokens.accent(c);
    }

    static int primaryText(Context c) {
        return YOverlayTokens.textPrimary(c);
    }

    static int secondaryText(Context c) {
        return YOverlayTokens.textSecondary(c);
    }

    static int surface(Context c) {
        return YOverlayTokens.surface(c);
    }

    static int actionSurface(Context c, boolean pressed) {
        return pressed ? YOverlayTokens.accent(c) : YOverlayTokens.surfaceAlt(c);
    }

    static float buttonRadius(Context c) {
        return dp(c, appearance(c).getButtonRadiusDp());
    }

    static float handleRadius(Context c) {
        return dp(c, Math.max(1f, appearance(c).getIconVisualSizeDp() / 4f));
    }

    static float controlGap(Context c) {
        return dp(c, appearance(c).getEffectiveGapDp());
    }

    static float buttonHeight(Context c) {
        return dp(c, appearance(c).getButtonHeightDp());
    }

    static float textSize(Context c, float baseSp) {
        return baseSp * c.getResources().getDisplayMetrics().scaledDensity
                * appearance(c).getFontPercent() / 100f;
    }

    /**
     * Screenshot shading uses the shared YUI surface color with an opacity
     * appropriate for a translucent overlay instead of a hard-coded black ARGB.
     */
    static int scrim(Context c, int opacity) {
        int base = YOverlayTokens.background(c);
        return Color.argb(Math.max(0, Math.min(255, opacity)),
                Color.red(base), Color.green(base), Color.blue(base));
    }

    static int accentWithAlpha(Context c, int opacity) {
        int base = accent(c);
        return Color.argb(Math.max(0, Math.min(255, opacity)),
                Color.red(base), Color.green(base), Color.blue(base));
    }

    static int iconBackground(Context c, int style) {
        if (style == 1) return YOverlayTokens.dark(c)
                ? YOverlayTokens.surfaceAlt(c) : YOverlayTokens.textPrimary(c);
        if (style == 2) return YOverlayTokens.dark(c)
                ? YOverlayTokens.textPrimary(c) : YOverlayTokens.background(c);
        return YOverlayTokens.accent(c);
    }

    static int iconForeground(Context c, int style) {
        if (style == 2) return YOverlayTokens.accent(c);
        if (style == 1) return YOverlayTokens.background(c);
        return YOverlayTokens.surface(c);
    }

    private YFloatOverlayStyle() { }
}
