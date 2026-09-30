package com.yagay.ypower;

import android.app.Application;

/** Standalone APK shell; shared runtime lives in YPowerRuntime. */
public class YPowerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        YPowerRuntime.get(this);
    }
}
