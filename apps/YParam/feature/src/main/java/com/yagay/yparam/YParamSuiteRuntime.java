package com.yagay.yparam;

import android.content.Context;

/** Shared YSuite/standalone runtime entry. */
public final class YParamSuiteRuntime {
    private YParamSuiteRuntime() { }

    public static Object get(Context context) {
        return YParamApp.initialize(context);
    }
}
