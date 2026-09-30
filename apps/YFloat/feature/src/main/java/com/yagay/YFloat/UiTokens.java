package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Color;

import com.yagay.yui.YView;

/**
 * YFloat visual tokens.
 *
 * Normal Activity/settings surfaces are backed by the shared YUI Material token resolver so YFloat
 * follows the same theme as the rest of YSuite. Floating result/action surfaces keep a few explicit
 * high-contrast tokens because they are overlay interactions rather than normal app screens.
 */
final class UiTokens {
    static boolean dark(Context c) { return ThemeSettings.isDark(c); }

    // Normal app-screen tokens: single source of truth is YUI / Material theme attributes.
    static int background(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorSurface, Color.WHITE);
    }
    static int surface(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorSurfaceContainer,
                dark(c) ? 0xFF181B20 : 0xFFF5F6F8);
    }
    static int surfaceAlt(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorSurfaceContainerHigh,
                dark(c) ? 0xFF23272E : 0xFFF0F2F5);
    }
    static int textPrimary(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorOnSurface,
                dark(c) ? 0xFFF4F5F7 : 0xFF17191D);
    }
    static int textSecondary(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorOnSurfaceVariant,
                dark(c) ? 0xFFAEB4BE : 0xFF69707B);
    }
    static int outline(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorOutlineVariant,
                dark(c) ? 0xFF292E35 : 0xFFE6E8EC);
    }
    static int success(Context c) {
        return YView.color(c, android.R.attr.colorAccent,
                dark(c) ? 0xFF7ED7A2 : 0xFF197A45);
    }
    static int warning(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorTertiary,
                dark(c) ? 0xFFFFC266 : 0xFFA05A00);
    }
    static int successSurface(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorSecondaryContainer,
                dark(c) ? 0xFF173527 : 0xFFE9F6EE);
    }
    static int warningSurface(Context c) {
        return YView.color(c, com.google.android.material.R.attr.colorTertiaryContainer,
                dark(c) ? 0xFF3A2A13 : 0xFFFFF1DF);
    }
    static int accent(Context c) {
        return YView.color(c, android.R.attr.colorAccent,
                dark(c) ? 0xFF9CC2FF : 0xFF285FBE);
    }
    static int ripple(Context c) {
        return YView.color(c, android.R.attr.colorControlHighlight,
                dark(c) ? 0x22FFFFFF : 0x12000000);
    }

    // Specialized floating surfaces intentionally use controlled contrast independent of page cards.
    static int menuSurface(Context c) { return dark(c) ? 0xFF2B2B2B : 0xFFF8F8F8; }
    static int menuPrimaryText(Context c) { return dark(c) ? 0xFFF5F5F5 : 0xFF202124; }
    static int menuSecondaryText(Context c) { return dark(c) ? 0xFFB8B8B8 : 0xFF5F6368; }
    static int menuRipple(Context c) { return dark(c) ? 0x33FFFFFF : 0x22000000; }
    static int resultSurface(Context c) { return dark(c) ? 0xF0202124 : 0xF8FFFFFF; }

    static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    private UiTokens() {}
}
