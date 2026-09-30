package com.yagay.ypower.hook;

import android.app.Application;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.yagay.ypower.model.AppProfile;

import org.json.JSONArray;

import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.net.ProxySelector;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Supplemental observe-only diagnostics that are intentionally kept separate from the
 * main YPowerModule. This keeps virtualization/network/service probes optional and avoids
 * changing any target app return value.
 */
public final class SupplementalSecurityModule extends XposedModule {
    private static final String TAG = "YPowerTrace";
    private static final String GROUP = "ypower";

    private String activePackageName = "";
    private ClassLoader targetClassLoader;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;

        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.ypower")) return;

        AppProfile profile = loadProfile(pkg);
        if (!profile.enabled || !profile.traceSecurityApis) return;

        activePackageName = pkg;
        try {
            targetClassLoader = param.getDefaultClassLoader();
        } catch (Throwable ignored) {
            targetClassLoader = null;
        }

        installVirtualizationHooks(profile);
        installNetworkEnvironmentHooks(profile);
        installLocalSocketHooks(profile);
        installServiceManagerHooks(profile);
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

    private void installVirtualizationHooks(AppProfile profile) {
        hookSystemProperties(profile);
        hookGlStrings(profile);
        hookTelephony(profile);
        hookSensors(profile);
    }

    private void hookSystemProperties(AppProfile profile) {
        for (Method method : System.class.getDeclaredMethods()) {
            if (!"getProperty".equals(method.getName()) || method.getParameterCount() < 1) continue;
            try {
                hook(method).intercept(chain -> {
                    String key = string(chain.getArg(0));
                    String rule = ruleForSystemProperty(key);
                    if (rule == null) return chain.proceed();

                    long startNs = System.nanoTime();
                    try {
                        Object result = chain.proceed();
                        String text = string(result);
                        String state = propertyState(rule, text);
                        trace(profile, "property", rule, key, safeValue(rule, text), state,
                                "System.getProperty", startNs, false);
                        return result;
                    } catch (Throwable t) {
                        trace(profile, "property", rule, key, "", "CHECKED",
                                "System.getProperty", startNs, true);
                        throw t;
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            for (Method method : properties.getDeclaredMethods()) {
                String name = method.getName();
                if (!("get".equals(name) || "getBoolean".equals(name)
                        || "getInt".equals(name) || "getLong".equals(name))) {
                    continue;
                }
                if (method.getParameterCount() < 1) continue;

                try {
                    hook(method).intercept(chain -> {
                        String key = string(chain.getArg(0));
                        String rule = ruleForSystemProperty(key);
                        if (rule == null) return chain.proceed();

                        long startNs = System.nanoTime();
                        Object result = chain.proceed();
                        String text = string(result);
                        trace(profile, "property", rule, key, safeValue(rule, text),
                                propertyState(rule, text), "SystemProperties." + name,
                                startNs, false);
                        return result;
                    });
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookGlStrings(AppProfile profile) {
        for (String className : new String[]{"android.opengl.GLES20", "android.opengl.GLES30"}) {
            Class<?> clazz = findClass(className);
            if (clazz == null) continue;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"glGetString".equals(method.getName()) || method.getParameterCount() != 1) continue;
                try {
                    hook(method).intercept(chain -> {
                        int name = intValue(chain.getArg(0), -1);
                        if (name != 0x1F00 && name != 0x1F01 && name != 0x1F02) {
                            return chain.proceed();
                        }
                        long startNs = System.nanoTime();
                        Object result = chain.proceed();
                        trace(profile, "property", DetectionRuleIds.VIRTUAL_GL_RENDERER_QUERY,
                                className + ".glGetString name=0x" + Integer.toHexString(name),
                                string(result), "CHECKED", className + ".glGetString",
                                startNs, false);
                        return result;
                    });
                } catch (Throwable ignored) {
                }
            }
        }

        Class<?> egl14 = findClass("android.opengl.EGL14");
        if (egl14 != null) {
            for (Method method : egl14.getDeclaredMethods()) {
                if (!"eglQueryString".equals(method.getName()) || method.getParameterCount() < 2) continue;
                try {
                    hook(method).intercept(chain -> {
                        long startNs = System.nanoTime();
                        Object result = chain.proceed();
                        trace(profile, "property", DetectionRuleIds.VIRTUAL_GL_RENDERER_QUERY,
                                "EGL14.eglQueryString name=" + string(chain.getArg(1)),
                                string(result), "CHECKED", "EGL14.eglQueryString",
                                startNs, false);
                        return result;
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void hookTelephony(AppProfile profile) {
        for (Method method : TelephonyManager.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!("getNetworkOperatorName".equals(name)
                    || "getSimOperatorName".equals(name)
                    || "getPhoneType".equals(name)
                    || "getNetworkCountryIso".equals(name)
                    || "getSimCountryIso".equals(name))) {
                continue;
            }
            if (method.getParameterCount() != 0) continue;
            try {
                hook(method).intercept(chain -> {
                    if (!looksSecurityStack()) return chain.proceed();
                    long startNs = System.nanoTime();
                    Object result = chain.proceed();
                    String text = string(result);
                    trace(profile, "property", DetectionRuleIds.VIRTUAL_TELEPHONY_QUERY,
                            "TelephonyManager." + name,
                            "present=" + !text.isBlank() + " length=" + text.length(),
                            "CHECKED", "TelephonyManager." + name, startNs, false);
                    return result;
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private void hookSensors(AppProfile profile) {
        try {
            Method method = SensorManager.class.getDeclaredMethod("getSensorList", int.class);
            hook(method).intercept(chain -> {
                if (!looksSecurityStack()) return chain.proceed();
                long startNs = System.nanoTime();
                Object result = chain.proceed();
                int size = result instanceof List<?> ? ((List<?>) result).size() : -1;
                trace(profile, "property", DetectionRuleIds.VIRTUAL_SENSOR_QUERY,
                        "SensorManager.getSensorList type=" + string(chain.getArg(0)),
                        "count=" + size, "CHECKED", "SensorManager.getSensorList",
                        startNs, false);
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private void installNetworkEnvironmentHooks(AppProfile profile) {
        try {
            Method hasTransport = NetworkCapabilities.class.getDeclaredMethod("hasTransport", int.class);
            hook(hasTransport).intercept(chain -> {
                int transport = intValue(chain.getArg(0), -1);
                if (transport != NetworkCapabilities.TRANSPORT_VPN) return chain.proceed();
                long startNs = System.nanoTime();
                Object result = chain.proceed();
                boolean vpn = Boolean.TRUE.equals(result);
                trace(profile, "property", DetectionRuleIds.VPN_TRANSPORT_QUERY,
                        "NetworkCapabilities.hasTransport(VPN)", String.valueOf(vpn),
                        vpn ? "HIT" : "NOT_HIT", "NetworkCapabilities.hasTransport",
                        startNs, false);
                return result;
            });
        } catch (Throwable ignored) {
        }

        for (Method method : NetworkInterface.class.getDeclaredMethods()) {
            String name = method.getName();
            if (!("getByName".equals(name) || "getNetworkInterfaces".equals(name))) continue;
            try {
                hook(method).intercept(chain -> {
                    if ("getByName".equals(name)) {
                        String queried = string(chain.getArg(0));
                        if (!looksVpnInterface(queried)) return chain.proceed();
                        long startNs = System.nanoTime();
                        Object result = chain.proceed();
                        trace(profile, "property", DetectionRuleIds.VPN_INTERFACE_QUERY,
                                "NetworkInterface.getByName " + queried,
                                result == null ? "false" : "true",
                                result == null ? "NOT_HIT" : "HIT",
                                "NetworkInterface.getByName", startNs, false);
                        return result;
                    }

                    if (!looksSecurityStack()) return chain.proceed();
                    long startNs = System.nanoTime();
                    Object result = chain.proceed();
                    trace(profile, "property", DetectionRuleIds.VPN_INTERFACE_QUERY,
                            "NetworkInterface.getNetworkInterfaces", "enumeration",
                            "CHECKED", "NetworkInterface.getNetworkInterfaces",
                            startNs, false);
                    return result;
                });
            } catch (Throwable ignored) {
            }
        }

        try {
            Method getDefault = ProxySelector.class.getDeclaredMethod("getDefault");
            hook(getDefault).intercept(chain -> {
                if (!looksSecurityStack()) return chain.proceed();
                long startNs = System.nanoTime();
                Object result = chain.proceed();
                trace(profile, "property", DetectionRuleIds.PROXY_SELECTOR_QUERY,
                        "ProxySelector.getDefault", result == null ? "null" : result.getClass().getName(),
                        "CHECKED", "ProxySelector.getDefault", startNs, false);
                return result;
            });
        } catch (Throwable ignored) {
        }
    }

    private void installLocalSocketHooks(AppProfile profile) {
        try {
            Method connect = LocalSocket.class.getDeclaredMethod("connect", LocalSocketAddress.class);
            hook(connect).intercept(chain -> {
                LocalSocketAddress address = chain.getArg(0) instanceof LocalSocketAddress
                        ? (LocalSocketAddress) chain.getArg(0) : null;
                String name = localSocketName(address);
                String lower = name.toLowerCase(Locale.ROOT);
                boolean rootRelated = lower.contains("magisk") || lower.contains("zygisk")
                        || lower.contains("kernelsu") || lower.contains("ksu")
                        || lower.contains("apatch");
                if (!rootRelated && !looksSecurityStack()) return chain.proceed();

                String rule = rootRelated
                        ? DetectionRuleIds.MAGISK_UNIX_SOCKET_QUERY
                        : DetectionRuleIds.UNIX_SOCKET_QUERY;
                long startNs = System.nanoTime();
                try {
                    Object result = chain.proceed();
                    trace(profile, "file", rule, "LocalSocket.connect " + name,
                            "connected", rootRelated ? "HIT" : "CHECKED",
                            "LocalSocket.connect", startNs, false);
                    return result;
                } catch (Throwable t) {
                    trace(profile, "file", rule, "LocalSocket.connect " + name,
                            t.getClass().getSimpleName(), rootRelated ? "NOT_HIT" : "CHECKED",
                            "LocalSocket.connect", startNs, true);
                    throw t;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void installServiceManagerHooks(AppProfile profile) {
        Class<?> serviceManager = findClass("android.os.ServiceManager");
        if (serviceManager == null) return;

        for (Method method : serviceManager.getDeclaredMethods()) {
            String name = method.getName();
            boolean named = "getService".equals(name) || "checkService".equals(name)
                    || "waitForService".equals(name) || "isDeclared".equals(name);
            boolean list = "listServices".equals(name);
            if (!named && !list) continue;

            try {
                hook(method).intercept(chain -> {
                    String input = named && method.getParameterCount() > 0
                            ? string(chain.getArg(0)) : "";
                    if (named && !looksSecurityService(input) && !looksSecurityStack()) {
                        return chain.proceed();
                    }
                    if (list && !looksSecurityStack()) return chain.proceed();

                    long startNs = System.nanoTime();
                    Object result = chain.proceed();
                    trace(profile, "package",
                            list ? DetectionRuleIds.SERVICE_LIST_QUERY : DetectionRuleIds.SERVICE_MANAGER_QUERY,
                            "ServiceManager." + name + (input.isBlank() ? "" : " " + input),
                            summarize(result), "CHECKED", "ServiceManager." + name,
                            startNs, false);
                    return result;
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private String ruleForSystemProperty(String key) {
        String s = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (s.equals("http.proxyhost") || s.equals("https.proxyhost")
                || s.equals("socksproxyhost") || s.equals("http.proxyport")
                || s.equals("https.proxyport") || s.equals("socksproxyport")) {
            return DetectionRuleIds.PROXY_PROPERTY_QUERY;
        }
        if (s.contains("qemu") || s.contains("goldfish") || s.contains("ranchu")
                || s.contains("emulator") || s.contains("hypervisor")
                || s.contains("virtual")) {
            return DetectionRuleIds.VIRTUAL_PROPERTY_QUERY;
        }
        return null;
    }

    private String propertyState(String rule, String result) {
        if (DetectionRuleIds.PROXY_PROPERTY_QUERY.equals(rule)) {
            return result == null || result.isBlank() || "0".equals(result)
                    ? "NOT_HIT" : "HIT";
        }
        return "CHECKED";
    }

    private String safeValue(String rule, String value) {
        if (DetectionRuleIds.PROXY_PROPERTY_QUERY.equals(rule)) {
            return value == null || value.isBlank() ? "empty" : "present length=" + value.length();
        }
        return value == null ? "" : value;
    }

    private boolean looksSecurityStack() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String s = (frame.getClassName() + "." + frame.getMethodName())
                    .toLowerCase(Locale.ROOT);
            if (s.contains("security") || s.contains("integrity") || s.contains("risk")
                    || s.contains("detect") || s.contains("check") || s.contains("guard")
                    || s.contains("root") || s.contains("emulator") || s.contains("virtual")
                    || s.contains("proxy") || s.contains("vpn") || s.contains("tamper")) {
                return true;
            }
        }
        return false;
    }

    private boolean looksSecurityService(String service) {
        String s = service == null ? "" : service.toLowerCase(Locale.ROOT);
        return s.contains("keystore") || s.contains("package") || s.contains("activity")
                || s.contains("integrity") || s.contains("security") || s.contains("device_identifiers")
                || s.contains("gatekeeper") || s.contains("authsecret");
    }

    private boolean looksVpnInterface(String name) {
        String s = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return s.startsWith("tun") || s.startsWith("ppp") || s.startsWith("wg")
                || s.contains("tailscale") || s.contains("wireguard") || s.contains("vpn");
    }

    private String localSocketName(LocalSocketAddress address) {
        if (address == null) return "";
        try {
            Method getName = LocalSocketAddress.class.getDeclaredMethod("getName");
            Object name = getName.invoke(address);
            return String.valueOf(name);
        } catch (Throwable ignored) {
            return String.valueOf(address);
        }
    }

    private Class<?> findClass(String className) {
        try {
            if (targetClassLoader != null) {
                return Class.forName(className, false, targetClassLoader);
            }
            return Class.forName(className);
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
            long startNs,
            boolean exception
    ) {
        long ts = System.currentTimeMillis();
        long durationNs = Math.max(0L, System.nanoTime() - startNs);
        int pid = android.os.Process.myPid();
        int tid = android.os.Process.myTid();
        Thread thread = Thread.currentThread();
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
                + ",\"exception\":\"" + (exception ? "observed" : "") + "\""
                + ",\"source\":\"" + escape(source) + "\""
                + ",\"pid\":" + pid
                + ",\"tid\":" + tid
                + ",\"thread\":\"" + escape(thread.getName()) + "\""
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
                    || clazz.equals(SupplementalSecurityModule.class.getName())) {
                continue;
            }
            if (added++ >= max) break;
            if (out.length() > 0) out.append(" <- ");
            out.append(clazz).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        return out.toString();
    }

    private static String summarize(Object result) {
        if (result == null) return "null";
        if (result instanceof Boolean || result instanceof Number || result instanceof CharSequence) {
            return String.valueOf(result);
        }
        if (result instanceof Object[]) return "array(length=" + ((Object[]) result).length + ")";
        if (result instanceof List<?>) return "list(size=" + ((List<?>) result).size() + ")";
        return result.getClass().getName();
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
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
