package com.yagay.YFloat;

import android.content.SharedPreferences;

/**
 * Immutable, validated style snapshot for the YFloat-owned Google Circle overlay.
 * It is transported via LSPosed Remote Preferences, never read from another
 * application's private storage. Defaults reproduce the existing appearance.
 */
public final class GoogleOverlayAppearance {
    public final int radiusDp;
    public final int outlineDp;
    public final int glowWidthDp;
    public final int glowBlurDp;
    public final int glowAlpha;
    public final int scrimAlpha;
    public final int trailWidthDp;
    public final boolean yuiColor;
    public final int accentColor;

    private GoogleOverlayAppearance(int radius, int outline, int glowWidth, int glowBlur,
                                    int glowAlpha, int scrimAlpha, int trailWidth,
                                    boolean yuiColor, int accentColor) {
        this.radiusDp = clamp(radius, 0, 96);
        this.outlineDp = clamp(outline, 0, 24);
        this.glowWidthDp = clamp(glowWidth, 0, 24);
        this.glowBlurDp = clamp(glowBlur, 0, 80);
        this.glowAlpha = clamp(glowAlpha, 0, 255);
        this.scrimAlpha = clamp(scrimAlpha, 0, 240);
        this.trailWidthDp = clamp(trailWidth, 0, 24);
        this.yuiColor = yuiColor;
        this.accentColor = accentColor | 0xFF000000;
    }

    public static GoogleOverlayAppearance defaults() {
        return new GoogleOverlayAppearance(8, 1, 1, 20, 235, 102, 3, true, 0xFF4285F4);
    }

    public static GoogleOverlayAppearance fromPreferences(SharedPreferences preferences) {
        if (preferences == null) return defaults();
        try {
            return new GoogleOverlayAppearance(
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_RADIUS_DP, 8),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_OUTLINE_DP, 1),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_GLOW_WIDTH_DP, 1),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_GLOW_BLUR_DP, 20),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_GLOW_ALPHA, 235),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_SCRIM_ALPHA, 102),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_TRAIL_WIDTH_DP, 3),
                    preferences.getBoolean(LsposedRuntimeConfig.K_GOOGLE_FRAME_USE_YUI_COLOR, true),
                    preferences.getInt(LsposedRuntimeConfig.K_GOOGLE_FRAME_YUI_COLOR, 0xFF4285F4));
        } catch (RuntimeException error) {
            return defaults();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
