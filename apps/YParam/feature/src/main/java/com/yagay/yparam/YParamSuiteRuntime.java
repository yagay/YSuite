package com.yagay.yparam;

import android.content.Context;

import com.yagay.suite.api.ManagedFeatureRuntime;

/** Shared YSuite/standalone runtime entry. */
public final class YParamSuiteRuntime implements ManagedFeatureRuntime {
    private static volatile YParamSuiteRuntime instance;

    private final Context context;
    private volatile boolean enabled;

    private YParamSuiteRuntime(Context context) {
        this.context = context.getApplicationContext();
    }

    public static YParamSuiteRuntime get(Context context) {
        YParamSuiteRuntime local = instance;
        if (local != null) return local;
        synchronized (YParamSuiteRuntime.class) {
            local = instance;
            if (local == null) {
                local = new YParamSuiteRuntime(context);
                instance = local;
            }
            return local;
        }
    }

    @Override
    public synchronized void enable() {
        if (enabled) return;
        enabled = true;
        YParamApp.initialize(context);
    }

    @Override
    public synchronized void disable() {
        if (!enabled) return;
        enabled = false;
        YParamApp.onSuiteRuntimeDisabled();
    }

    @Override
    public synchronized void destroy() {
        disable();
    }
}
