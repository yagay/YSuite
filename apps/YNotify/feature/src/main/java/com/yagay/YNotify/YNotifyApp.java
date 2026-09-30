package com.yagay.YNotify;

import android.app.Application;

/** Standalone APK shell; real runtime lives in YNotifyRuntime for suite reuse. */
public final class YNotifyApp extends Application {
    public static final String REMOTE_GROUP = YNotifyRuntime.REMOTE_GROUP;
    public static final String KEY_SECRET = YNotifyRuntime.KEY_SECRET;
    public static final String KEY_HOST_PACKAGE = YNotifyRuntime.KEY_HOST_PACKAGE;
    public static final String KEY_HOOK_HEARTBEAT = YNotifyRuntime.KEY_HOOK_HEARTBEAT;
    public static final String KEY_HOOK_VERSION = YNotifyRuntime.KEY_HOOK_VERSION;
    public static final String KEY_HOOK_PACKAGE = YNotifyRuntime.KEY_HOOK_PACKAGE;
    public static final String KEY_HOOK_PROCESS = YNotifyRuntime.KEY_HOOK_PROCESS;

    @Override
    public void onCreate() {
        super.onCreate();
        YNotifyRuntime.get(this);
    }

    public static String runtimeStatus() {
        return YNotifyRuntime.runtimeStatus();
    }
}
