package com.yagay.YNotify;

import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.util.DiagLog;
import com.yagay.YNotify.util.HookAuth;
import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.ManagedFeatureRuntime;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Host-neutral runtime shared by standalone YNotify and YSuite. */
public final class YNotifyRuntime implements XposedServiceHelper.OnServiceListener, ManagedFeatureRuntime {
    public static final String REMOTE_GROUP = "ynotify_runtime";
    public static final String KEY_SECRET = "event_secret";
    public static final String KEY_HOST_PACKAGE = "host_package";
    public static final String KEY_HOOK_HEARTBEAT = "hook_heartbeat";
    public static final String KEY_HOOK_VERSION = "hook_version";
    public static final String KEY_HOOK_PACKAGE = "hook_package";
    public static final String KEY_HOOK_PROCESS = "hook_process";
    private static final String SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker";

    private static volatile YNotifyRuntime instance;
    private static volatile XposedService service;
    private static volatile SharedPreferences remote;
    private static volatile String frameworkStatus = "LSPosed/API 102 服务未连接";

    private final Context context;
    private volatile boolean enabled = true;
    private volatile FeatureHost host;

    private YNotifyRuntime(Context context) {
        this.context = context.getApplicationContext();
        ListenerStateStore.markProcessStarted(this.context);
        HookAuth.ensureLocalSecret(this.context);
        if (!attachToSuiteBroker()) XposedServiceHelper.registerListener(this);
        DiagLog.i(this.context, "YNotifyRuntime", "runtime created; waiting for LSPosed API 102 service");
    }

    private boolean attachToSuiteBroker() {
        final Class<?> broker;
        try {
            broker = Class.forName(SUITE_BROKER, false, YNotifyRuntime.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable error) {
            DiagLog.e(context, "LSPosed", "YSuite broker lookup failed", error);
            return true;
        }
        try {
            Method attach = broker.getMethod("attachFromPlugin", String.class, Object.class);
            Object result = attach.invoke(null, "ynotify", this);
            if (!Boolean.TRUE.equals(result)) {
                DiagLog.w(context, "LSPosed", "YSuite broker rejected YNotify listener");
            }
        } catch (Throwable error) {
            DiagLog.e(context, "LSPosed", "YSuite broker attach failed", error);
        }
        return true;
    }

    public static YNotifyRuntime get(Context context) {
        YNotifyRuntime local = instance;
        if (local != null) return local;
        synchronized (YNotifyRuntime.class) {
            local = instance;
            if (local == null) {
                local = new YNotifyRuntime(context);
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
        ListenerStateStore.markProcessStarted(context);
        HookAuth.ensureLocalSecret(context);
        if (service == null) frameworkStatus = "LSPosed/API 102 服务未连接";
        DiagLog.i(context, "YNotifyRuntime", "managed runtime enabled");
    }

    @Override
    public void disable() {
        enabled = false;
        service = null;
        remote = null;
        frameworkStatus = "YNotify 已由 YSuite 停用";
        DiagLog.i(context, "YNotifyRuntime", "managed runtime disabled; framework references released");
    }

    @Override
    public void destroy() {
        disable();
        host = null;
    }

    @Override
    public void onServiceBind(XposedService bound) {
        if (!enabled) return;
        try {
            if (bound.getApiVersion() < 102) {
                frameworkStatus = "框架 API " + bound.getApiVersion() + "，需要 API 102";
                DiagLog.w(context, "LSPosed", frameworkStatus);
                return;
            }
            if ((bound.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                frameworkStatus = bound.getFrameworkName() + " 不支持 Remote Preferences";
                DiagLog.w(context, "LSPosed", frameworkStatus);
                return;
            }
            SharedPreferences prefs = bound.getRemotePreferences(REMOTE_GROUP);
            String secret = HookAuth.ensureLocalSecret(context);
            String versionName = "?";
            try {
                versionName = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (Throwable ignored) { }
            prefs.edit()
                    .putString(KEY_SECRET, secret)
                    .putString(KEY_HOST_PACKAGE, context.getPackageName())
                    .putString("app_version", versionName)
                    .putLong("app_sync_at", System.currentTimeMillis())
                    .commit();

            service = bound;
            remote = prefs;
            List<String> scope = bound.getScope();
            frameworkStatus = bound.getFrameworkName() + " " + bound.getFrameworkVersion()
                    + " · API " + bound.getApiVersion()
                    + " · Scope " + (scope == null ? 0 : scope.size());
            DiagLog.i(context, "LSPosed", "service bound: " + frameworkStatus);
        } catch (Throwable t) {
            frameworkStatus = "LSPosed 服务连接失败：" + t.getClass().getSimpleName();
            DiagLog.e(context, "LSPosed", "service bind failed", t);
        }
    }

    @Override
    public void onServiceDied(XposedService dead) {
        if (service == dead) {
            service = null;
            remote = null;
            if (enabled) {
                frameworkStatus = "LSPosed/API 102 服务已断开";
                DiagLog.w(context, "LSPosed", "service died/disconnected");
            }
        }
    }

    public static String runtimeStatus() {
        StringBuilder out = new StringBuilder(frameworkStatus);
        SharedPreferences prefs = remote;
        if (prefs != null) {
            long heartbeat = prefs.getLong(KEY_HOOK_HEARTBEAT, 0L);
            String version = prefs.getString(KEY_HOOK_VERSION, "");
            String pkg = prefs.getString(KEY_HOOK_PACKAGE, "");
            if (heartbeat > 0) {
                long age = Math.max(0L, System.currentTimeMillis() - heartbeat);
                out.append("\nHook：")
                        .append(age < 120_000L ? "运行中" : "已加载但心跳较旧")
                        .append(" · v").append(version == null ? "?" : version);
                if (pkg != null && !pkg.isEmpty()) out.append(" · ").append(pkg);
                out.append("\n最后心跳：").append(age / 1000L).append(" 秒前");
            } else {
                out.append("\nHook：尚未收到运行心跳");
            }
        }
        return out.toString();
    }
}
