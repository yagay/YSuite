package com.yagay.yparam;

import android.content.Context;

import io.github.libxposed.service.XposedServiceHelper;

/** YSuite-only host bridge; business/UI code stays in the YParam repository. */
public final class YParamSuiteRuntime {
    private static YParamApp listener;

    private YParamSuiteRuntime() { }

    public static synchronized Object get(Context context) {
        if (listener == null) {
            listener = new YParamApp();
            XposedServiceHelper.registerListener(listener);
        }
        return listener;
    }
}
