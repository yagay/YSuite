package com.yagay.suite.api;

import android.content.Context;

import io.github.libxposed.service.XposedServiceHelper;

public final class XposedHostBridge {
    public enum AttachResult {
        ATTACHED,
        NOT_SUITE_HOST,
        HOST_PRESENT_BUT_FAILED
    }

    public static boolean isSuiteHost(Context context) {
        if (context == null) return false;
        String name = context.getPackageName();
        return name != null && name.startsWith("com.yagay.ysuite");
    }

    public static AttachResult attachListener(
            Context context,
            String listenerId,
            XposedServiceHelper.OnServiceListener listener) {
        if (listener == null) return AttachResult.HOST_PRESENT_BUT_FAILED;
        try {
            XposedServiceHelper.registerListener(listener);
            return AttachResult.ATTACHED;
        } catch (Throwable ignored) {
            return AttachResult.HOST_PRESENT_BUT_FAILED;
        }
    }

    private XposedHostBridge() {}
}
