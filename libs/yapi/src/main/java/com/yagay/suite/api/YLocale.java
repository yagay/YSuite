package com.yagay.suite.api;

import android.content.Context;

import androidx.annotation.StringRes;

/**
 * Small process-local resource resolver for feature code that cannot conveniently carry a Context.
 *
 * <p>The resolver is initialized by {@link YLocaleInitProvider}. All user-visible copy must still
 * live in Android resources; this class only removes the temptation to hardcode text in runtime,
 * model, or service classes.</p>
 */
public final class YLocale {
    private static volatile Context appContext;

    private YLocale() {}

    static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    public static Context context() {
        Context context = appContext;
        if (context == null) {
            throw new IllegalStateException("YLocale is not initialized in this process");
        }
        return context;
    }

    public static String text(@StringRes int resId) {
        return context().getString(resId);
    }

    public static String text(@StringRes int resId, Object... formatArgs) {
        return context().getString(resId, formatArgs);
    }
}
