package com.yagay.YNotify;

import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.util.DiagLog;
import com.yagay.YNotify.util.HookAuth;
import com.yagay.suite.api.ManagedFeatureRuntime;
import com.yagay.suite.api.XposedHostBridge;

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
    private static final String FEATURE_ID = "ynotify";

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

    private YNotifyRuntime(Context context) {
        this.context = context.getApplicationContext();
        ListenerStateStore.markProcessStarted(this.context);
        HookAuth.ensureLocalSecret(this.context);

        XposedHostBridge.AttachResult attach =
                XposedHostBridge.attachListener(this.context, FEATURE_ID, this);
        if (attach == XposedHostBridge.AttachResult.NOT_SUITE_HOST) {
            XposedServiceHelper.registerListener(this);
        } else if (attach == XposedHostBridge.AttachResult.HOST_PRESENT_BUT_FAILED) {
            DiagLog.e(this.context, "LSPosed", "YSuite broker attach failed", null);
        }
        DiagLog.i(this.context, "YNotifyRuntime", "runtime created; waiting for LSPosed API 102 service");
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
        Context app = context.getApplicationContext();
        StringBuilder out = new StringBuilder(frameworkStatusText(app));
        SharedPreferences prefs = remote;
        if (prefs != null) {
            long heartbeat = prefs.getLong(KEY_HOOK_HEARTBEAT, 0L);
            String version = prefs.getString(KEY_HOOK_VERSION, "");
            String pkg = prefs.getString(KEY_HOOK_PACKAGE, "");
            if (heartbeat > 0) {
                long age = Math.max(0L, System.currentTimeMillis() - heartbeat);
                out.append('\n').append(app.getString(age < 120_000L
                        ? R.string.ynotify_hook_status_running
                        : R.string.ynotify_hook_status_stale));
                out.append('\n').append(app.getString(R.string.ynotify_hook_version,
                        version == null || version.isEmpty() ? "?" : version));
                if (pkg != null && !pkg.isEmpty()) {
                    out.append('\n').append(app.getString(R.string.ynotify_hook_package, pkg));
                }
                out.append('\n').append(app.getString(R.string.ynotify_last_heartbeat_seconds, age / 1000L));
            } else {
                out.append('\n').append(app.getString(R.string.ynotify_hook_no_heartbeat));
            }
        }
        return out.toString();
    }

    private static String frameworkStatusText(Context context) {
        return switch (frameworkState) {
            case DISABLED -> context.getString(R.string.ynotify_runtime_disabled);
            case API_TOO_OLD -> context.getString(R.string.ynotify_runtime_api_required, frameworkName, frameworkApi);
            case REMOTE_UNSUPPORTED -> context.getString(R.string.ynotify_runtime_remote_unsupported, frameworkName);
            case CONNECTED -> context.getString(R.string.ynotify_runtime_connected,
                    frameworkName, frameworkVersion, frameworkApi, frameworkScopeCount);
            case CONNECT_FAILED -> context.getString(R.string.ynotify_runtime_connect_failed, frameworkError);
            case DIED -> context.getString(R.string.ynotify_runtime_disconnected);
            case DISCONNECTED -> context.getString(R.string.ynotify_runtime_not_connected);
        };
    }
}
