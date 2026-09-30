package com.yagay.ypower.hook;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.yagay.ypower.model.AppProfile;

import org.json.JSONArray;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Observe-only Binder/socket diagnostics. No return values or Parcel data are modified. */
public final class DeepObservationModule extends XposedModule {
    private static final String TAG = "YPowerTrace";
    private static final String GROUP = "ypower";

    private final Map<Object, String> binderDescriptors =
            Collections.synchronizedMap(new WeakHashMap<>());

    private String activePackageName = "";
    private ClassLoader targetClassLoader;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.ypower")) return;

        AppProfile profile = loadProfile(pkg);
        if (!profile.enabled) return;

        activePackageName = pkg;
        try {
            targetClassLoader = param.getDefaultClassLoader();
        } catch (Throwable ignored) {
            targetClassLoader = null;
        }

        if (profile.traceSecurityApis) {
            installBinderHooks(profile);
        }
        if (profile.traceNative) {
            boolean enabled = NativeSocketTraceBridge.enable(pkg, profile.diagnosticSessionId);
            trace(profile, "provider", DetectionRuleIds.UNKNOWN,
                    "native-af-unix=" + (enabled ? "enabled" : "unavailable"),
                    "", "CHECKED", "NativeSocketTraceBridge", System.nanoTime());
        }
    }

    private AppProfile loadProfile(String packageName) {
        try {
            SharedPreferences prefs = getRemotePreferences(GROUP);
            String enabledRaw = prefs.getString("enabledPackages", "[]");
            boolean enabled = false;
            JSONArray arr = new JSONArray(enabledRaw == null ? "[]" : enabledRaw);
            for (int i = 0; i < arr.length(); i++) {
                if (packageName.equals(arr.optString(i))) {
                    enabled = true;
                    break;
                }
            }
            AppProfile p = AppProfile.fromJson(
                    prefs.getString("profile:" + packageName, null),
                    packageName
            );
            p.enabled = enabled && p.enabled;
            return p;
        } catch (Throwable t) {
            return new AppProfile(packageName);
        }
    }

    private void installBinderHooks(AppProfile profile) {
        Class<?> binderProxy = findClass("android.os.BinderProxy");
        if (binderProxy == null) return;

        for (Method method : binderProxy.getDeclaredMethods()) {
            String name = method.getName();
            if ("getInterfaceDescriptor".equals(name) && method.getParameterCount() == 0) {
                hookDescriptor(profile, method);
            } else if ("transact".equals(name) && method.getParameterCount() >= 1) {
                hookTransact(profile, method);
            }
        }
    }

    private void hookDescriptor(AppProfile profile, Method method) {
        try {
            hook(method).intercept(chain -> {
                long startNs = System.nanoTime();
                Object result = chain.proceed();
                String descriptor = string(result);
                Object binder = chain.getThisObject();
                if (binder != null && !descriptor.isBlank()) {
                    binderDescriptors.put(binder, descriptor);
                }

                if (looksSecurityDescriptor(descriptor) || looksSecurityStack()) {
                    trace(profile, "package", SupplementalRuleIds.BINDER_DESCRIPTOR_QUERY,
                            "BinderProxy.getInterfaceDescriptor",
                            descriptor, "CHECKED", "BinderProxy.getInterfaceDescriptor", startNs);
                }
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private void hookTransact(AppProfile profile, Method method) {
        try {
            hook(method).intercept(chain -> {
                Object binder = chain.getThisObject();
                String descriptor = binder == null ? "" : binderDescriptors.getOrDefault(binder, "");
                boolean interesting = looksSecurityDescriptor(descriptor) || looksSecurityStack();
                if (!interesting) return chain.proceed();

                int code = intValue(chain.getArg(0), -1);
                int flags = method.getParameterCount() > 3
                        ? intValue(chain.getArg(3), 0) : 0;
                long startNs = System.nanoTime();
                try {
                    Object result = chain.proceed();
                    trace(profile, "package", SupplementalRuleIds.BINDER_TRANSACT_QUERY,
                            "descriptor=" + descriptor + " code=" + code + " flags=" + flags,
                            summarize(result), "CHECKED", "BinderProxy.transact", startNs);
                    return result;
                } catch (Throwable t) {
                    trace(profile, "package", SupplementalRuleIds.BINDER_TRANSACT_QUERY,
                            "descriptor=" + descriptor + " code=" + code + " flags=" + flags,
                            t.getClass().getName(), "CHECKED", "BinderProxy.transact", startNs);
                    throw t;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private boolean looksSecurityDescriptor(String descriptor) {
        String s = descriptor == null ? "" : descriptor.toLowerCase(Locale.ROOT);
        return s.contains("keystore") || s.contains("keymint") || s.contains("gatekeeper")
                || s.contains("package") || s.contains("integrity") || s.contains("security")
                || s.contains("attest") || s.contains("authsecret") || s.contains("drm");
    }

    private boolean looksSecurityStack() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String s = (frame.getClassName() + "." + frame.getMethodName())
                    .toLowerCase(Locale.ROOT);
            if (s.contains("security") || s.contains("integrity") || s.contains("risk")
                    || s.contains("detect") || s.contains("check") || s.contains("guard")
                    || s.contains("root") || s.contains("attest") || s.contains("keystore")
                    || s.contains("emulator") || s.contains("virtual") || s.contains("tamper")) {
                return true;
            }
        }
        return false;
    }

    private Class<?> findClass(String name) {
        try {
            if (targetClassLoader != null) return Class.forName(name, false, targetClassLoader);
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void trace(
            AppProfile profile,
            String type,
            String ruleId,
            String input,
            String result,
            String hitState,
            String source,
            long startNs
    ) {
        long ts = System.currentTimeMillis();
        long durationNs = Math.max(0L, System.nanoTime() - startNs);
        int pid = android.os.Process.myPid();
        int tid = android.os.Process.myTid();
        String process;
        try {
            process = Application.getProcessName();
        } catch (Throwable ignored) {
            process = "";
        }
        String sessionId = profile == null || profile.diagnosticSessionId == null
                ? "" : profile.diagnosticSessionId;
        String stack = profile != null && profile.traceStacks ? captureStack(14) : "";
        boolean matched = "HIT".equals(hitState);

        String json = "{\"ts\":" + ts
                + ",\"package\":\"" + escape(activePackageName) + "\""
                + ",\"sessionId\":\"" + escape(sessionId) + "\""
                + ",\"type\":\"" + escape(type) + "\""
                + ",\"ruleId\":\"" + escape(ruleId) + "\""
                + ",\"input\":\"" + escape(input) + "\""
                + ",\"value\":\"" + escape(input) + "\""
                + ",\"result\":\"" + escape(result) + "\""
                + ",\"matched\":" + matched
                + ",\"hitState\":\"" + escape(hitState) + "\""
                + ",\"exception\":\"\""
                + ",\"source\":\"" + escape(source) + "\""
                + ",\"pid\":" + pid
                + ",\"tid\":" + tid
                + ",\"thread\":\"" + escape(Thread.currentThread().getName()) + "\""
                + ",\"process\":\"" + escape(process) + "\""
                + ",\"durationNs\":" + durationNs
                + ",\"stack\":\"" + escape(stack) + "\"}";
        log(Log.INFO, TAG, json);
        Log.i(TAG, json);
    }

    private String captureStack(int max) {
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String clazz = frame.getClassName();
            if (clazz.startsWith("java.lang.Thread")
                    || clazz.equals(DeepObservationModule.class.getName())) continue;
            if (added++ >= max) break;
            if (out.length() > 0) out.append(" <- ");
            out.append(clazz).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        return out.toString();
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String summarize(Object result) {
        if (result == null) return "null";
        if (result instanceof Boolean || result instanceof Number || result instanceof CharSequence) {
            return String.valueOf(result);
        }
        return result.getClass().getName();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
