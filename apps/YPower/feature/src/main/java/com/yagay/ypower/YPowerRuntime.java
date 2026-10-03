package com.yagay.ypower;

import android.content.Context;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.ManagedFeatureRuntime;
import com.yagay.suite.api.XposedHostBridge;
import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.xposed.XposedBridgeManager;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Host-neutral runtime shared by standalone YPower and YSuite. */
public final class YPowerRuntime implements XposedServiceHelper.OnServiceListener, ManagedFeatureRuntime {
    private static final String FEATURE_ID = "ypower";
    private static final FeatureServices SERVICES = FeatureServices.of(FEATURE_ID, "YPower");
    private static volatile YPowerRuntime instance;

    private final Context context;
    private volatile boolean enabled = true;

    private YPowerRuntime(Context context) {
        this.context = context.getApplicationContext();
        boolean sharedHost = XposedHostBridge.isSuiteHost(this.context);
        XposedBridgeManager.setSharedHost(sharedHost);

        XposedHostBridge.AttachResult result =
                XposedHostBridge.attachListener(this.context, FEATURE_ID, this);
        if (result == XposedHostBridge.AttachResult.NOT_SUITE_HOST) {
            try {
                XposedServiceHelper.registerListener(this);
            } catch (Throwable t) {
                SERVICES.warn("LSPosed service registration unavailable", t);
            }
        } else if (result == XposedHostBridge.AttachResult.HOST_PRESENT_BUT_FAILED) {
            SERVICES.error("YSuite LSPosed broker attach failed");
        }
    }

    public static YPowerRuntime get(Context context) {
        YPowerRuntime local = instance;
        if (local != null) return local;
        synchronized (YPowerRuntime.class) {
            local = instance;
            if (local == null) {
                local = new YPowerRuntime(context);
                instance = local;
            }
            return local;
        }
    }

    @Override
    public void enable() {
        enabled = true;
        SERVICES.info("managed runtime enabled");
    }

    @Override
    public void disable() {
        enabled = false;
        XposedBridgeManager.setService(null);
        SERVICES.info("managed runtime disabled; LSPosed service released");
    }

    @Override
    public void destroy() {
        disable();
    }

    @Override
    public void onServiceBind(XposedService service) {
        if (!enabled) return;
        XposedBridgeManager.setService(service);
        ProfileStore.get(context).syncAllToRemote();
        for (String pkg : ProfileStore.get(context).getEnabledPackages()) {
            XposedBridgeManager.requestScope(pkg);
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        XposedBridgeManager.setService(null);
    }
}
