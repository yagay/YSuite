package com.yagay.suite.api;

import android.content.Context;

public final class YLocale {
    private static volatile Context app;

    public static void initialize(Context context) {
        if (context != null) {
            Context candidate = context.getApplicationContext();
            app = candidate != null ? candidate : context;
        }
    }

    public static String text(int resId, Object... args) {
        Context context = app;
        if (context == null) return Integer.toString(resId);
        try {
            return args == null || args.length == 0
                    ? context.getString(resId)
                    : context.getString(resId, args);
        } catch (Throwable ignored) {
            return Integer.toString(resId);
        }
    }

    private YLocale() {}
}
