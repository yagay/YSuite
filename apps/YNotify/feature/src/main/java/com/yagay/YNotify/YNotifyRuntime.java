package com.yagay.YNotify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.util.DiagLog;
import com.yagay.YNotify.util.HookAuth;
import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.ManagedFeatureRuntime;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

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

    private enum FrameworkState {
        DISCONNECTED,
        DISABLED,
        API_TOO_OLD,
        REMOTE_UNSUPPORTED,
        CONNECTED,
        CONNECT_FAILED,
        DIED
    }

    private static volatile YNotifyRuntime instance;
    private static volatile XposedService service;
    private static volatile SharedPreferences remote;
    private static volatile FrameworkState frameworkState = FrameworkState.DISCONNECTED;
    private static volatile String frameworkName = "LSPosed";
    private static volatile String frameworkVersion = "";
    private static volatile int frameworkApi = 0;
    private static volatile int frameworkScopeCount = 0;
    private static volatile String frameworkError = "";

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
        if (service == null) frameworkState = FrameworkState.DISCONNECTED;
        DiagLog.i(context, "YNotifyRuntime", "managed runtime enabled");
    }

    @Override
    public void disable() {
        enabled = false;
        service = null;
        remote = null;
        frameworkState = FrameworkState.DISABLED;
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
            frameworkName = bound.getFrameworkName();
            frameworkVersion = bound.getFrameworkVersion();
            frameworkApi = bound.getApiVersion();
            if (frameworkApi < 102) {
                frameworkState = FrameworkState.API_TOO_OLD;
                DiagLog.w(context, "LSPosed", "framework API " + frameworkApi + "; API 102 required");
                return;
            }
            if ((bound.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                frameworkState = FrameworkState.REMOTE_UNSUPPORTED;
                DiagLog.w(context, "LSPosed", frameworkName + " does not support Remote Preferences");
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
            frameworkScopeCount = scope == null ? 0 : scope.size();
            frameworkState = FrameworkState.CONNECTED;
            frameworkError = "";
            DiagLog.i(context, "LSPosed", "service bound: " + frameworkName + " " + frameworkVersion
                    + ", API " + frameworkApi + ", scope " + frameworkScopeCount);
        } catch (Throwable t) {
            frameworkError = t.getClass().getSimpleName();
            frameworkState = FrameworkState.CONNECT_FAILED;
            DiagLog.e(context, "LSPosed", "service bind failed", t);
        }
    }

    @Override
    public void onServiceDied(XposedService dead) {
        if (service == dead) {
            service = null;
            remote = null;
            if (enabled) {
                frameworkState = FrameworkState.DIED;
                DiagLog.w(context, "LSPosed", "service died/disconnected");
            }
        }
    }

    public static String runtimeStatus(Context context) {
        boolean zh = isChinese(context);
        StringBuilder out = new StringBuilder(frameworkStatusText(zh));
        SharedPreferences prefs = remote;
        if (prefs != null) {
            long heartbeat = prefs.getLong(KEY_HOOK_HEARTBEAT, 0L);
            String version = prefs.getString(KEY_HOOK_VERSION, "");
            String pkg = prefs.getString(KEY_HOOK_PACKAGE, "");
            if (heartbeat > 0) {
                long age = Math.max(0L, System.currentTimeMillis() - heartbeat);
                out.append('\n')
                        .append(zh ? "Hook 状态：" : "Hook status: ")
                        .append(age < 120_000L
                                ? (zh ? "运行中" : "Running")
                                : (zh ? "已加载，但心跳较旧" : "Loaded, but the heartbeat is old"));
                out.append('\n').append(zh ? "Hook 版本：" : "Hook version: ")
                        .append(version == null || version.isEmpty() ? "?" : version);
                if (pkg != null && !pkg.isEmpty()) {
                    out.append('\n').append(zh ? "Hook 包名：" : "Hook package: ").append(pkg);
                }
                out.append('\n').append(zh ? "最后心跳：" : "Last heartbeat: ")
                        .append(age / 1000L)
                        .append(zh ? " 秒前" : " seconds ago");
            } else {
                out.append('\n').append(zh ? "Hook 状态：尚未收到运行心跳" : "Hook status: no runtime heartbeat received yet");
            }
        }
        return out.toString();
    }

    private static String frameworkStatusText(boolean zh) {
        switch (frameworkState) {
            case DISABLED:
                return zh ? "YNotify 已由 YSuite 停用" : "YNotify is disabled by YSuite";
            case API_TOO_OLD:
                return zh
                        ? frameworkName + " API " + frameworkApi + "。需要 API 102。"
                        : frameworkName + " API " + frameworkApi + ". API 102 is required.";
            case REMOTE_UNSUPPORTED:
                return zh
                        ? frameworkName + " 不支持 Remote Preferences。"
                        : frameworkName + " does not support Remote Preferences.";
            case CONNECTED:
                return zh
                        ? frameworkName + " " + frameworkVersion + " 已连接。API " + frameworkApi
                            + "。Scope 数量 " + frameworkScopeCount + "。"
                        : frameworkName + " " + frameworkVersion + " connected. API " + frameworkApi
                            + ". Scope count " + frameworkScopeCount + ".";
            case CONNECT_FAILED:
                return zh
                        ? "LSPosed 服务连接失败：" + frameworkError
                        : "LSPosed service connection failed: " + frameworkError;
            case DIED:
                return zh ? "LSPosed API 102 服务已断开" : "LSPosed API 102 service disconnected";
            case DISCONNECTED:
            default:
                return zh ? "LSPosed API 102 服务未连接" : "LSPosed API 102 service is not connected";
        }
    }

    private static boolean isChinese(Context context) {
        Locale locale;
        try {
            if (context != null && Build.VERSION.SDK_INT >= 24) {
                locale = context.getResources().getConfiguration().getLocales().get(0);
            } else if (context != null) {
                locale = context.getResources().getConfiguration().locale;
            } else {
                locale = Locale.getDefault();
            }
        } catch (Throwable ignored) {
            locale = Locale.getDefault();
        }
        return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
    }
}
