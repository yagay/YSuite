package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.suite.api.XposedHostBridge;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * Publishes the physical app host package to hooked processes when YFloat runs as a YSuite plugin.
 * Standalone YFloat does not need this listener; hook-side transport defaults to its own package.
 */
final class YFloatHostIdentity implements XposedServiceHelper.OnServiceListener {
    private static final String LISTENER_ID = "yfloat.host";

    private static volatile YFloatHostIdentity instance;
    private final String hostPackage;

    private YFloatHostIdentity(Context context) {
        hostPackage = context.getPackageName();
    }

    static void initialize(Context context) {
        if (context == null || !XposedHostBridge.isSuiteHost(context) || instance != null) return;
        synchronized (YFloatHostIdentity.class) {
            if (instance != null) return;
            YFloatHostIdentity listener = new YFloatHostIdentity(context.getApplicationContext());
            XposedHostBridge.AttachResult result =
                    XposedHostBridge.attachListener(context, LISTENER_ID, listener);
            if (result == XposedHostBridge.AttachResult.ATTACHED) {
                instance = listener;
            } else {
                DiagnosticLog.critical(context, "HOST_IDENTITY", "Managed host identity listener attach failed");
            }
        }
    }

    @Override
    public void onServiceBind(XposedService service) {
        try {
            SharedPreferences remote = service.getRemotePreferences(LsposedRuntimeConfig.GROUP);
            if (remote != null) {
                remote.edit()
                        .putString(LsposedRuntimeConfig.K_HOST_PACKAGE, hostPackage)
                        .putLong(LsposedRuntimeConfig.K_UPDATED_AT, System.currentTimeMillis())
                        .commit();
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void onServiceDied(XposedService service) { }
}
