package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import com.yagay.yui.YAppearanceStore;
import com.yagay.yui.YSettingKey;

/** Single bridge for YFloat visuals: all values are owned and validated by YUI.
 * Functional gesture, capture, action and privilege settings remain in FloatSettings. */
final class YFloatVisualSettings {
    static final String MODULE = "yfloat";
    private static final String MIGRATED = "yui_visuals_migrated_v1";
    private static final String[] ICON_STYLES = {"blue", "dark", "light", "custom", "slideshow"};
    private static final String[] TRAIL_STYLES = {"round", "square", "enhanced"};
    private static final String[] BORDER_COLORS = {"blue", "green", "cyan", "purple", "orange", "red", "white"};
    private static final int[] BORDER_ARGB = {0xFF4285F4, 0xFF34A853, 0xFF00B8D4,
        0xFF9C6ADE, 0xFFFF8A00, 0xFFEA4335, 0xFFFFFFFF};

    private static YSettingKey key(String legacy) {
        if (legacy == null) return null;
        return switch (legacy) {
            case FloatSettings.K_ALPHA -> YSettingKey.FLOAT_ICON_ALPHA;
            case FloatSettings.K_SIZE -> YSettingKey.FLOAT_ICON_SIZE;
            case FloatSettings.K_SHOW_PERCENT -> YSettingKey.FLOAT_EDGE_VISIBLE;
            case FloatSettings.K_CIRCLE_BORDER_WIDTH_DP -> YSettingKey.FLOAT_BORDER_WIDTH;
            case FloatSettings.K_CIRCLE_BORDER_COLOR -> YSettingKey.FLOAT_BORDER_COLOR;
            case FloatSettings.K_LINE_ALPHA -> YSettingKey.FLOAT_TRAIL_ALPHA;
            case FloatSettings.K_LINE_WIDTH -> YSettingKey.FLOAT_TRAIL_WIDTH;
            case FloatSettings.K_STYLE -> YSettingKey.FLOAT_ICON_STYLE;
            case FloatSettings.K_LINE_STYLE -> YSettingKey.FLOAT_TRAIL_STYLE;
            case FloatSettings.K_LINE_GRADIENT -> YSettingKey.FLOAT_TRAIL_GRADIENT;
            case FloatSettings.K_LINE_COLORS -> YSettingKey.FLOAT_TRAIL_COLORS;
            case FloatSettings.K_CIRCLE_BORDER_ENABLED -> YSettingKey.FLOAT_BORDER_VISIBLE;
            case FloatSettings.K_TRACK -> YSettingKey.FLOAT_TRAIL_VISIBLE;
            default -> null;
        };
    }

    static int readInt(Context context, String legacy, int fallback) {
        YSettingKey key = key(legacy);
        if (key == null) return fallback;
        String raw = new YAppearanceStore(context).value(key, MODULE);
        if (key == YSettingKey.FLOAT_ICON_STYLE) return indexOf(ICON_STYLES, raw);
        if (key == YSettingKey.FLOAT_TRAIL_STYLE) return indexOf(TRAIL_STYLES, raw);
        if (key == YSettingKey.FLOAT_BORDER_COLOR) return BORDER_ARGB[indexOf(BORDER_COLORS, raw)];
        return Integer.parseInt(raw);
    }

    static boolean readBoolean(Context context, String legacy, boolean fallback) {
        YSettingKey key = key(legacy);
        return key == YSettingKey.FLOAT_TRAIL_GRADIENT ||
            key == YSettingKey.FLOAT_BORDER_VISIBLE ||
            key == YSettingKey.FLOAT_TRAIL_VISIBLE
            ? "true".equals(new YAppearanceStore(context).value(key, MODULE)) : fallback;
    }

    static boolean writeInt(Context context, String legacy, int value) {
        YSettingKey key = key(legacy);
        if (key == null || key == YSettingKey.FLOAT_TRAIL_GRADIENT ||
            key == YSettingKey.FLOAT_BORDER_VISIBLE || key == YSettingKey.FLOAT_TRAIL_VISIBLE ||
            key == YSettingKey.FLOAT_TRAIL_COLORS) return false;
        String raw = Integer.toString(value);
        if (key == YSettingKey.FLOAT_ICON_STYLE) raw = ICON_STYLES[index(value, ICON_STYLES.length)];
        if (key == YSettingKey.FLOAT_TRAIL_STYLE) raw = TRAIL_STYLES[index(value, TRAIL_STYLES.length)];
        if (key == YSettingKey.FLOAT_BORDER_COLOR) {
            int matching = 0;
            for (int i = 0; i < BORDER_ARGB.length; i++)
                if (BORDER_ARGB[i] == value) { matching = i; break; }
            raw = BORDER_COLORS[matching];
        }
        new YAppearanceStore(context).set(key, raw, MODULE);
        return true;
    }

    static boolean writeBoolean(Context context, String legacy, boolean value) {
        YSettingKey key = key(legacy);
        if (key != YSettingKey.FLOAT_TRAIL_GRADIENT &&
            key != YSettingKey.FLOAT_BORDER_VISIBLE &&
            key != YSettingKey.FLOAT_TRAIL_VISIBLE) return false;
        new YAppearanceStore(context).set(key, Boolean.toString(value), MODULE);
        return true;
    }

    static String readString(Context context, String legacy, String fallback) {
        if (key(legacy) != YSettingKey.FLOAT_TRAIL_COLORS) return fallback;
        return new YAppearanceStore(context).value(YSettingKey.FLOAT_TRAIL_COLORS, MODULE);
    }

    static boolean writeString(Context context, String legacy, String value) {
        if (key(legacy) != YSettingKey.FLOAT_TRAIL_COLORS) return false;
        new YAppearanceStore(context).set(YSettingKey.FLOAT_TRAIL_COLORS,
            value == null ? "" : value, MODULE);
        return true;
    }

    static int menuCount(Context context) {
        return Integer.parseInt(new YAppearanceStore(context)
            .value(YSettingKey.FLOAT_MENU_COUNT, MODULE));
    }

    static void setMenuCount(Context context, int count) {
        new YAppearanceStore(context).set(YSettingKey.FLOAT_MENU_COUNT,
            Integer.toString(count), MODULE);
    }

    /** Read previous local values once, never overwrite a global/explicit module override. */
    static void migrate(Context context, SharedPreferences old) {
        if (old.getBoolean(MIGRATED, false)) return;
        YAppearanceStore store = new YAppearanceStore(context);
        for (String key : new String[] {
            FloatSettings.K_ALPHA, FloatSettings.K_SIZE, FloatSettings.K_SHOW_PERCENT,
            FloatSettings.K_CIRCLE_BORDER_WIDTH_DP, FloatSettings.K_CIRCLE_BORDER_COLOR,
            FloatSettings.K_LINE_ALPHA, FloatSettings.K_LINE_WIDTH, FloatSettings.K_STYLE,
            FloatSettings.K_LINE_STYLE, FloatSettings.K_LINE_GRADIENT,
            FloatSettings.K_LINE_COLORS, FloatSettings.K_CIRCLE_BORDER_ENABLED, FloatSettings.K_TRACK,
        }) {
            YSettingKey setting = key(key);
            if (!old.contains(key) || store.isOverridden(setting, MODULE)) continue;
            Object raw = old.getAll().get(key);
            try {
                if (raw instanceof Number number) writeInt(context, key, number.intValue());
                else if (raw instanceof Boolean booleanValue) writeBoolean(context, key, booleanValue);
                else if (raw instanceof String text) writeString(context, key, text);
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy values revert to YUI's validated default.
            }
        }
        SharedPreferences oldMenu = context.getSharedPreferences("yfloat_text_menu", Context.MODE_PRIVATE);
        if (oldMenu.contains("main_item_count_v2")
                && !store.isOverridden(YSettingKey.FLOAT_MENU_COUNT, MODULE)) {
            int count = oldMenu.getInt("main_item_count_v2", 6);
            try { setMenuCount(context, count); }
            catch (IllegalArgumentException ignored) { }
        }
        SharedPreferences oldTheme = context.getSharedPreferences("yfloat_ui", Context.MODE_PRIVATE);
        if (oldTheme.contains("theme_mode")
                && !store.isOverridden(YSettingKey.THEME, MODULE)) {
            int mode = oldTheme.getInt("theme_mode", 0);
            if (mode == 1 || mode == 2) store.set(YSettingKey.THEME,
                mode == 1 ? "light" : "dark", MODULE);
        }
        old.edit().putBoolean(MIGRATED, true).apply();
    }

    private static int indexOf(String[] values, String wanted) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(wanted)) return i;
        return 0;
    }
    private static int index(int value, int len) { return Math.max(0, Math.min(len - 1, value)); }
    private YFloatVisualSettings() { }
}
