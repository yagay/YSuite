package com.yagay.ypower;

import android.content.Context;
import android.util.Log;

import com.topjohnwu.superuser.Shell;
import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.ManagedFeatureRuntime;
import com.yagay.ypower.data.ProfileStore;
import com.yagay.ypower.xposed.XposedBridgeManager;

import java.lang.reflect.Method;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Host-neutral runtime shared by standalone YPower and YSuite. */
public final class YPowerRuntime implements XposedServiceHelper.OnServiceListener, ManagedFeatureRuntime {
    private static final String YSUITE_PACKAGE = "com.yagay.YSuite";
    private static final String SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker";
    private static volatile YPowerRuntime instance;
    private final Context context;
    private volatile boolean enabled = true;
    private volatile FeatureHost host;

    private YPowerRuntime(Context context) {
        this.context = context.getApplicationContext();
        boolean sharedHost = YSUITE_PACKAGE.equals(this.context.getPackageName()) || suiteBrokerPresent();
        XposedBridgeManager.setSharedHost(sharedHost);

        if (!sharedHost) {
            Shell.enableVerboseLogging = false;
            Shell.setDefaultBuilder(Shell.Builder.create()
                    .setContext(this.context)
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(15));
        }

        if (!attachToSuiteBroker()) {
            try {
                XposedServiceHelper.registerListener(this);
            } catch (Throwable t) {
                Log.w("YPower", "LSPosed service registration unavailable", t);
            }
        }
    }

    private static boolean suiteBrokerPresent() {
        try {
            Class.forName(SUITE_BROKER, false, YPowerRuntime.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean attachToSuiteBroker() {
        final Class<?> broker;
        try {
            broker = Class.forName(SUITE_BROKER, false, YPowerRuntime.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable error) {
            Log.e("YPower", "YSuite broker lookup failed", error);
            return true;
        }
        try {
            Method attach = broker.getMethod("attachFromPlugin", String.class, Object.class);
            attach.invoke(null, "ypower", this);
        } catch (Throwable error) {
            Log.e("YPower", "YSuite broker attach failed", error);
        }
        return true;
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
    public void attach(FeatureHost host) {
        this.host = host;
    }

    @Override
    public void enable() {
        enabled = true;
        Log.i("YPower", "managed runtime enabled");
    }

    @Override
    public void disable() {
        enabled = false;
        XposedBridgeManager.setService(null);
        Log.i("YPower", "managed runtime disabled; LSPosed service released");
    }

    @Override
    public void destroy() {
        disable();
        host = null;
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
