package com.yagay.YSuite.xposed;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Objects;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Per-plugin view of the Xposed runtime.
 *
 * <p>All non-hook operations are forwarded to the YSuite host. Hook registrations are intercepted
 * by {@link SuiteHookRegistry}, which turns multiple plugin registrations for the same target into
 * one physical framework hook.</p>
 */
final class SuitePluginXposedInterface implements XposedInterface {
    private final XposedModule host;
    private final SuiteHookRegistry hookRegistry;
    private final String pluginId;

    SuitePluginXposedInterface(
            XposedModule host,
            SuiteHookRegistry hookRegistry,
            String pluginId) {
        this.host = Objects.requireNonNull(host);
        this.hookRegistry = Objects.requireNonNull(hookRegistry);
        this.pluginId = Objects.requireNonNull(pluginId);
    }

    @Override
    public int getApiVersion() {
        return host.getApiVersion();
    }

    @Override
    public String getFrameworkName() {
        return host.getFrameworkName();
    }

    @Override
    public String getFrameworkVersion() {
        return host.getFrameworkVersion();
    }

    @Override
    public long getFrameworkVersionCode() {
        return host.getFrameworkVersionCode();
    }

    @Override
    public long getFrameworkProperties() {
        return host.getFrameworkProperties();
    }

    @Override
    public HookBuilder hook(Executable origin) {
        return hookRegistry.hookBuilder(pluginId, origin);
    }

    @Override
    public HookBuilder hookClassInitializer(Class<?> origin) {
        return hookRegistry.classInitializerBuilder(pluginId, origin);
    }

    @Override
    public boolean deoptimize(Executable executable) {
        return host.deoptimize(executable);
    }

    @Override
    public Invoker<?, Method> getInvoker(Method method) {
        return host.getInvoker(method);
    }

    @Override
    public <T> CtorInvoker<T> getInvoker(Constructor<T> constructor) {
        return host.getInvoker(constructor);
    }

    @Override
    public void log(int priority, String tag, String msg) {
        host.log(priority, tag, prefix(msg));
    }

    @Override
    public void log(int priority, String tag, String msg, Throwable tr) {
        host.log(priority, tag, prefix(msg), tr);
    }

    @Override
    public SharedPreferences getRemotePreferences(String group) {
        return host.getRemotePreferences(group);
    }

    @Override
    public ApplicationInfo getModuleApplicationInfo() {
        return host.getModuleApplicationInfo();
    }

    @Override
    public String[] listRemoteFiles() {
        return host.listRemoteFiles();
    }

    @Override
    public ParcelFileDescriptor openRemoteFile(String name) throws FileNotFoundException {
        return host.openRemoteFile(name);
    }

    private String prefix(String message) {
        return "[" + pluginId + "] " + message;
    }
}
