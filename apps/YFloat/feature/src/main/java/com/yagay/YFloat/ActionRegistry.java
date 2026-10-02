package com.yagay.YFloat;

import android.content.Context;

import androidx.annotation.StringRes;

import java.util.LinkedHashMap;

/** Single catalog for configurable action IDs, localized labels and defaults. */
final class ActionRegistry {
    private static final LinkedHashMap<String, Integer> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(ActionId.NONE, R.string.yfloat_action_none);
        LABELS.put(ActionId.BACK, R.string.yfloat_action_back);
        LABELS.put(ActionId.HOME, R.string.yfloat_action_home);
        LABELS.put(ActionId.RECENTS, R.string.yfloat_action_recents);
        LABELS.put(ActionId.SCREENSHOT, R.string.yfloat_action_screenshot);
        LABELS.put(ActionId.REGION_SCREENSHOT, R.string.yfloat_action_region_screenshot);
        LABELS.put(ActionId.OCR, R.string.yfloat_action_ocr);
        LABELS.put(ActionId.AI_SCREEN, R.string.yfloat_action_ai_screen);
        LABELS.put(ActionId.NOTIFICATIONS, R.string.yfloat_action_notifications);
        LABELS.put(ActionId.CLICK_UNDER, R.string.yfloat_action_click_under);
        LABELS.put(ActionId.MOVE_ICON, R.string.yfloat_action_move_icon);
        LABELS.put(ActionId.HIDE, R.string.yfloat_action_hide);
    }

    static String[] availableIds() {
        return LABELS.keySet().toArray(new String[0]);
    }

    @StringRes
    static int labelRes(String id) {
        Integer label = LABELS.get(id);
        return label == null ? R.string.yfloat_action_none : label;
    }

    static String label(Context context, String id) {
        return context.getString(labelRes(id));
    }

    static String defaultForPreference(String key) {
        if (FloatSettings.K_ACTION_DOUBLE.equals(key)) return ActionId.SCREENSHOT;
        if (FloatSettings.K_ACTION_RECOGNIZE.equals(key)) return ActionId.OCR;
        if (FloatSettings.K_ACTION_UP.equals(key)) return ActionId.RECENTS;
        if (FloatSettings.K_ACTION_DOWN_SHORT.equals(key)) return ActionId.NOTIFICATIONS;
        if (FloatSettings.K_ACTION_SIDE_SHORT.equals(key)) return ActionId.BACK;
        return ActionId.NONE;
    }

    private ActionRegistry() {}
}
