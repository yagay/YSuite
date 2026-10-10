package com.yagay.YFloat;

import android.content.Context;
import android.content.ContextWrapper;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
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
    // The YUI View palette resolves module scope from a Context's feature identity.
    // WindowManager overlays often receive the Suite Application context instead of an
    // Activity, so wrap it under YFloat's package to read module-specific theme/accent.
    private static final WeakHashMap<Context, WeakReference<Context>> MODULE_CONTEXTS =
            new WeakHashMap<>();

    private static final class ModuleContext extends ContextWrapper {
        ModuleContext(Context base) { super(base); }
    }

    private static synchronized Context moduleContext(Context context) {
        WeakReference<Context> reference = MODULE_CONTEXTS.get(context);
        Context wrapper = reference == null ? null : reference.get();
        if (wrapper == null) {
            wrapper = new ModuleContext(context);
            MODULE_CONTEXTS.put(context, new WeakReference<>(wrapper));
        }
        return wrapper;
    }

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
        return YOverlayTokens.accent(moduleContext(c));
    }

    static int primaryText(Context c) {
        return YOverlayTokens.textPrimary(moduleContext(c));
    }

    static int secondaryText(Context c) {
        return YOverlayTokens.textSecondary(moduleContext(c));
    }

    static int surface(Context c) {
        return YOverlayTokens.surface(moduleContext(c));
    }

    static int actionSurface(Context c, boolean pressed) {
        return pressed ? YOverlayTokens.accent(moduleContext(c)) : YOverlayTokens.surfaceAlt(moduleContext(c));
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
        int base = YOverlayTokens.background(moduleContext(c));
        return Color.argb(Math.max(0, Math.min(255, opacity)),
                Color.red(base), Color.green(base), Color.blue(base));
    }

    static int accentWithAlpha(Context c, int opacity) {
        int base = accent(c);
        return Color.argb(Math.max(0, Math.min(255, opacity)),
                Color.red(base), Color.green(base), Color.blue(base));
    }

    static int iconBackground(Context c, int style) {
        if (style == 1) return YOverlayTokens.dark(moduleContext(c))
                ? YOverlayTokens.surfaceAlt(moduleContext(c)) : YOverlayTokens.textPrimary(moduleContext(c));
        if (style == 2) return YOverlayTokens.dark(moduleContext(c))
                ? YOverlayTokens.textPrimary(moduleContext(c)) : YOverlayTokens.background(moduleContext(c));
        return YOverlayTokens.accent(moduleContext(c));
    }

    static int iconForeground(Context c, int style) {
        if (style == 2) return YOverlayTokens.accent(moduleContext(c));
        if (style == 1) return YOverlayTokens.background(moduleContext(c));
        return YOverlayTokens.surface(moduleContext(c));
    }

    private YFloatOverlayStyle() { }
}
