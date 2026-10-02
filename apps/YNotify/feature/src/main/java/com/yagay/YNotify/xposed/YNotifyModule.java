package com.yagay.YNotify.xposed;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.yagay.YNotify.BuildConfig;
import com.yagay.YNotify.YNotifyApp;
import com.yagay.YNotify.collector.XposedEventReceiver;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.util.HookAuth;
import com.yagay.YNotify.util.TextUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YNotifyModule extends XposedModule {
    private static final String TAG = "YNotify-Xposed";
    private static final String STANDALONE_PACKAGE = "com.yagay.YNotify";
    private static final String YSUITE_PACKAGE = "com.yagay.YSuite";
    private static final String YSUITE_BRIDGE_RECEIVER = "com.yagay.YSuite.ipc.SuiteBridgeReceiver";
    private static final Map<Object, PendingUiEvent> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConcurrentHashMap<String, Long> RECENT_UI = new ConcurrentHashMap<>();

    private volatile SharedPreferences runtimePrefs;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        try {
            runtimePrefs = getRemotePreferences(YNotifyApp.REMOTE_GROUP);
            markHeartbeat(param.getProcessName(), "module");
        } catch (Throwable ignored) {}
        log(Log.INFO, TAG, "module loaded");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!param.isFirstPackage() || pkg == null || pkg.equals(hostPackage())) return;
        try {
            ClassLoader cl = param.getClassLoader();
            if ("android".equals(pkg)) {
                installSystemServerHooks(cl);
            } else {
                installFrameworkHooks(pkg);
                installSnackbarHooks(cl, pkg);
                if ("com.android.systemui".equals(pkg)) installSystemUiHooks(cl);
            }
            markHeartbeat(currentProcessName(), pkg);
            log(Log.INFO, TAG, "enhanced capture ready: " + pkg);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook setup failed: " + pkg, t);
        }
    }

    private void installFrameworkHooks(String pkg) {
        hookNamed(Toast.class, "makeText", chain -> {
            Context context = argContext(chain);
            String text = textArg(context, chain);
            Object result = chain.proceed();
            if (result instanceof Toast && context != null && text != null && !text.isBlank()) {
                PENDING.put(result, new PendingUiEvent(context, EventTypes.TOAST, text, Toast.class.getName()));
            }
            return result;
        });

        hookNamed(Toast.class, "show", chain -> {
            Object self = chain.getThisObject();
            Object result = chain.proceed();
            if (self instanceof Toast) {
                PendingUiEvent pending = PENDING.remove(self);
                if (pending != null) {
                    emitUi(pending.context, pkg, pending.type, pending.text, pending.className);
                } else {
                    Toast toast = (Toast) self;
                    View view = toast.getView();
                    emitUi(view == null ? null : view.getContext(), pkg, EventTypes.TOAST,
                            TextUtil.collectText(view), toast.getClass().getName());
                }
            }
            return result;
        });

        hookNamed(Dialog.class, "show", chain -> {
            Object result = chain.proceed();
            Object self = chain.getThisObject();
            if (self instanceof Dialog) {
                Dialog d = (Dialog) self;
                View root = d.getWindow() == null ? null : d.getWindow().getDecorView();
                emitUi(d.getContext(), pkg, EventTypes.DIALOG, TextUtil.collectText(root), d.getClass().getName());
            }
            return result;
        });

        XposedInterface.Hooker popupHook = chain -> {
            Object result = chain.proceed();
            Object self = chain.getThisObject();
            if (self instanceof PopupWindow) {
                PopupWindow p = (PopupWindow) self;
                View content = p.getContentView();
                emitUi(content == null ? null : content.getContext(), pkg, EventTypes.POPUP,
                        TextUtil.collectText(content), p.getClass().getName());
            }
            return result;
        };
        hookNamed(PopupWindow.class, "showAsDropDown", popupHook);
        hookNamed(PopupWindow.class, "showAtLocation", popupHook);
    }

    private void installSnackbarHooks(ClassLoader cl, String pkg) {
        try {
            Class<?> snackbar = Class.forName("com.google.android.material.snackbar.Snackbar", false, cl);
            hookNamed(snackbar, "make", chain -> {
                Context context = null;
                String text = null;
                for (Object arg : chain.getArgs()) {
                    if (arg instanceof View && context == null) context = ((View) arg).getContext();
                    if (arg instanceof CharSequence) text = arg.toString();
                }
                Object result = chain.proceed();
                if (result != null && context != null && text != null && !text.isBlank()) {
                    PENDING.put(result, new PendingUiEvent(context, EventTypes.SNACKBAR, text, snackbar.getName()));
                }
                return result;
            });
            hookNamed(snackbar, "show", chain -> {
                Object self = chain.getThisObject();
                Object result = chain.proceed();
                PendingUiEvent pending = self == null ? null : PENDING.remove(self);
                if (pending != null) emitUi(pending.context, pkg, pending.type, pending.text, pending.className);
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void installSystemUiHooks(ClassLoader cl) {
        installHeadsUpHooks(cl);
        installBubbleHooks(cl);
        installFullScreenHooks(cl);
    }

    private void installHeadsUpHooks(ClassLoader cl) {
        String[] classes = {
                "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl",
                "com.android.systemui.statusbar.policy.HeadsUpManager",
                "com.android.systemui.statusbar.phone.HeadsUpManagerPhone"
        };
        for (String name : classes) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                for (String method : new String[]{"showNotification", "addAlertingNotification"}) {
                    hookNamed(c, method, chain -> {
                        Object result = chain.proceed();
                        emitSurfaceFromChain(chain, XposedEventReceiver.KIND_HEADS_UP, c.getName());
                        return result;
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    private void installBubbleHooks(ClassLoader cl) {
        String[] classes = {
                "com.android.systemui.bubbles.BubbleController",
                "com.android.wm.shell.bubbles.BubbleController",
                "com.android.wm.shell.bubbles.BubbleData"
        };
        String[] methods = {
                "expandStackAndSelectBubble",
                "expandStackAndSelectBubbleFromLauncher",
                "setSelectedBubble"
        };
        for (String name : classes) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                for (String method : methods) {
                    hookNamed(c, method, chain -> {
                        Object result = chain.proceed();
                        emitSurfaceFromChain(chain, XposedEventReceiver.KIND_BUBBLE, c.getName());
                        return result;
                    });
                }
            } catch (Throwable ignored) {}
        }
    }

    private void installFullScreenHooks(ClassLoader cl) {
        String[] launchers = {
                "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter",
                "com.android.systemui.statusbar.notification.NotificationActivityStarter"
        };
        for (String name : launchers) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                hookNamed(c, "launchFullScreenIntent", chain -> {
                    Object result = chain.proceed();
                    emitSurfaceFromChain(chain, XposedEventReceiver.KIND_FULL_SCREEN, c.getName());
                    return result;
                });
            } catch (Throwable ignored) {}
        }

        try {
            Class<?> provider = Class.forName(
                    "com.android.systemui.statusbar.notification.interruption.FullScreenIntentProvider", false, cl);
            hookNamed(provider, "shouldLaunchFullScreenIntentWhenAdded", chain -> {
                Object result = chain.proceed();
                if (result instanceof Boolean && (Boolean) result) {
                    emitSurfaceFromChain(chain, XposedEventReceiver.KIND_FULL_SCREEN, provider.getName());
                }
                return result;
            });
        } catch (Throwable ignored) {}
    }

    private void installSystemServerHooks(ClassLoader cl) {
        try {
            Class<?> nms = Class.forName("com.android.server.notification.NotificationManagerService", false, cl);
            hookNamed(nms, "enqueueTextToast", chain -> {
                Object result = chain.proceed();
                Context context = contextFromObject(chain.getThisObject());
                String pkg = packageArg(chain);
                String text = toastTextArg(chain, pkg);
                if (pkg != null && text != null && !text.isBlank()
                        && shouldEmitUi(pkg, EventTypes.TOAST, text, 750L)) {
                    emitUi(context, pkg, EventTypes.TOAST, text,
                            "com.android.server.notification.NotificationManagerService#enqueueTextToast");
                }
                return result;
            });

            hookNamed(nms, "enqueueToast", chain -> {
                Object result = chain.proceed();
                Context context = contextFromObject(chain.getThisObject());
                String pkg = packageArg(chain);
                String text = toastTextArg(chain, pkg);
                if (text == null || text.isBlank()) text = EventTypes.CUSTOM_TOAST_MARKER;
                if (pkg != null && shouldEmitUi(pkg, EventTypes.TOAST, text, 750L)) {
                    emitUi(context, pkg, EventTypes.TOAST, text,
                            "com.android.server.notification.NotificationManagerService#enqueueToast");
                }
                return result;
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "system_server toast hook unavailable: " + t);
        }
    }

    private void emitSurfaceFromChain(XposedInterface.Chain chain, String kind, String className) {
        String key = notificationKeyFromArgs(chain);
        if (key == null || key.isBlank()) return;
        String pkg = packageFromArgs(chain);
        if (pkg == null || pkg.isBlank()) pkg = packageFromNotificationKey(key);
        if (pkg == null || pkg.isBlank()) pkg = "com.android.systemui";
        emit(currentApplicationContext(), pkg, kind, EventTypes.NOTIFICATION, "", className, key);
    }

    private int hookNamed(Class<?> clazz, String name, XposedInterface.Hooker hooker) {
        int count = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            try {
                m.setAccessible(true);
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
                count++;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "skip " + clazz.getName() + "#" + name + ": " + t);
            }
        }
        return count;
    }

    private void emitUi(Context context, String pkg, String type, String text, String className) {
        emit(context, pkg, XposedEventReceiver.KIND_UI, type, text, className, "");
    }

    private void emit(Context context, String pkg, String kind, String type,
                      String text, String className, String notificationKey) {
        if (context == null || pkg == null || pkg.isBlank()) return;
        text = text == null ? "" : text.trim();
        if (XposedEventReceiver.KIND_UI.equals(kind) && text.isEmpty()) return;
        try {
            SharedPreferences prefs = runtimePrefs;
            String secret = prefs == null ? null : prefs.getString(YNotifyApp.KEY_SECRET, null);
            if (secret == null || secret.isEmpty()) return;

            long time = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString();
            String signature = HookAuth.sign(secret, pkg, kind, type, text, className, notificationKey, time, nonce);

            String host = hostPackage();
            Intent i = new Intent(XposedEventReceiver.ACTION);
            i.setClassName(
                    host,
                    YSUITE_PACKAGE.equals(host)
                            ? YSUITE_BRIDGE_RECEIVER
                            : "com.yagay.YNotify.collector.XposedEventReceiver");
            if (YSUITE_PACKAGE.equals(host)) i.putExtra("ysuite_plugin", "ynotify");
            i.putExtra("package", pkg);
            i.putExtra("kind", kind);
            i.putExtra("type", type);
            i.putExtra("text", text);
            i.putExtra("class", className);
            i.putExtra("notification_key", notificationKey);
            i.putExtra("time", time);
            i.putExtra("nonce", nonce);
            i.putExtra("signature", signature);
            context.sendBroadcast(i);
            markHeartbeat(pkg, pkg);
        } catch (Throwable ignored) {}
    }

    private String hostPackage() {
        try {
            SharedPreferences prefs = runtimePrefs;
            String host = prefs == null ? null : prefs.getString(YNotifyApp.KEY_HOST_PACKAGE, null);
            return host == null || host.isBlank() ? STANDALONE_PACKAGE : host;
        } catch (Throwable ignored) {
            return STANDALONE_PACKAGE;
        }
    }

    private void markHeartbeat(String process, String pkg) {
        try {
            SharedPreferences prefs = runtimePrefs;
            if (prefs == null) return;
            prefs.edit()
                    .putLong(YNotifyApp.KEY_HOOK_HEARTBEAT, System.currentTimeMillis())
                    .putString(YNotifyApp.KEY_HOOK_VERSION, BuildConfig.VERSION_NAME)
                    .putString(YNotifyApp.KEY_HOOK_PROCESS, process == null ? "" : process)
                    .putString(YNotifyApp.KEY_HOOK_PACKAGE, pkg == null ? "" : pkg)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static boolean shouldEmitUi(String pkg, String type, String text, long windowMs) {
        long now = System.currentTimeMillis();
        String key = pkg + '\n' + type + '\n' + text;
        Long previous = RECENT_UI.put(key, now);
        if (RECENT_UI.size() > 200) {
            long cutoff = now - 5_000L;
            RECENT_UI.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
        return previous == null || now - previous > windowMs;
    }

    private static Context argContext(XposedInterface.Chain chain) {
        for (Object arg : chain.getArgs()) if (arg instanceof Context) return (Context) arg;
        return null;
    }

    private static String packageArg(XposedInterface.Chain chain) {
        String fallback = null;
        for (Object arg : chain.getArgs()) {
            if (!(arg instanceof String)) continue;
            String s = ((String) arg).trim();
            if (s.isEmpty()) continue;
            if (fallback == null) fallback = s;
            if (s.indexOf('.') > 0 && s.indexOf(' ') < 0 && s.length() <= 255) return s;
        }
        return fallback;
    }

    private static String textArg(Context context, XposedInterface.Chain chain) {
        for (Object arg : chain.getArgs()) if (arg instanceof CharSequence) return arg.toString();
        if (context != null && chain.getArgs().size() > 1 && chain.getArg(1) instanceof Integer) {
            try { return context.getText((Integer) chain.getArg(1)).toString(); } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String toastTextArg(XposedInterface.Chain chain, String pkg) {
        for (Object arg : chain.getArgs()) {
            if (!(arg instanceof CharSequence)) continue;
            String value = arg.toString();
            if (value.isBlank()) continue;
            if (pkg != null && pkg.equals(value)) continue;
            return value;
        }
        return null;
    }

    private static StatusBarNotification extractSbn(Object entry) {
        if (entry == null) return null;
        if (entry instanceof StatusBarNotification) return (StatusBarNotification) entry;
        for (String method : new String[]{"getSbn", "getStatusBarNotification"}) {
            try {
                Method m = entry.getClass().getMethod(method);
                Object value = m.invoke(entry);
                if (value instanceof StatusBarNotification) return (StatusBarNotification) value;
            } catch (Throwable ignored) {}
        }
        for (String field : new String[]{"mSbn", "sbn"}) {
            try {
                Field f = findField(entry.getClass(), field);
                if (f == null) continue;
                f.setAccessible(true);
                Object value = f.get(entry);
                if (value instanceof StatusBarNotification) return (StatusBarNotification) value;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String extractKey(Object entry) {
        if (entry == null) return null;
        try {
            Method m = entry.getClass().getMethod("getKey");
            Object value = m.invoke(entry);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) { return null; }
    }

    private static String notificationKeyFromArgs(XposedInterface.Chain chain) {
        for (Object arg : chain.getArgs()) {
            StatusBarNotification sbn = extractSbn(arg);
            if (sbn != null && sbn.getKey() != null) return sbn.getKey();
            String key = extractKey(arg);
            if (looksLikeNotificationKey(key)) return key;
            if (arg instanceof String && looksLikeNotificationKey((String) arg)) return (String) arg;
        }
        return null;
    }

    private static String packageFromArgs(XposedInterface.Chain chain) {
        for (Object arg : chain.getArgs()) {
            StatusBarNotification sbn = extractSbn(arg);
            if (sbn != null && sbn.getPackageName() != null) return sbn.getPackageName();
        }
        return null;
    }

    private static boolean looksLikeNotificationKey(String value) {
        if (value == null || value.isBlank()) return false;
        int first = value.indexOf('|');
        return first >= 0 && value.indexOf('|', first + 1) > first;
    }

    private static String packageFromNotificationKey(String key) {
        if (!looksLikeNotificationKey(key)) return null;
        String[] parts = key.split("\\|", -1);
        return parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
    }

    private static Context contextFromObject(Object object) {
        if (object == null) return null;
        Class<?> c = object.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    if (!Context.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object value = f.get(object);
                    if (value instanceof Context) {
                        Context ctx = (Context) value;
                        Context app = ctx.getApplicationContext();
                        return app != null ? app : ctx;
                    }
                } catch (Throwable ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); }
            catch (Throwable ignored) { c = c.getSuperclass(); }
        }
        return null;
    }

    private static String currentProcessName() {
        try { return android.app.Application.getProcessName(); }
        catch (Throwable ignored) { return ""; }
    }

    private static Context currentApplicationContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method currentApplication = at.getDeclaredMethod("currentApplication");
            Object app = currentApplication.invoke(null);
            return app instanceof Context ? ((Context) app).getApplicationContext() : null;
        } catch (Throwable ignored) { return null; }
    }

    private static final class PendingUiEvent {
        final Context context;
        final String type;
        final String text;
        final String className;

        PendingUiEvent(Context context, String type, String text, String className) {
            Context app = context.getApplicationContext();
            this.context = app != null ? app : context;
            this.type = type;
            this.text = text;
            this.className = className;
        }
    }
}
