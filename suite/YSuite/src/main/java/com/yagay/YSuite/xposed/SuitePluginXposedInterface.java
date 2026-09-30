package com.yagay.YSuite.xposed;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import com.yagay.suite.core.SuiteContract;

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
    private final String featureId;
    private final SharedPreferences featureState;

    SuitePluginXposedInterface(
            XposedModule host,
            SuiteHookRegistry hookRegistry,
            String pluginId) {
        this.host = Objects.requireNonNull(host);
        this.hookRegistry = Objects.requireNonNull(hookRegistry);
        this.pluginId = Objects.requireNonNull(pluginId);
        int slash = pluginId.indexOf('/');
        this.featureId = slash >= 0 ? pluginId.substring(0, slash) : pluginId;
        this.featureState = host.getRemotePreferences(SuiteContract.FEATURE_STATE_REMOTE_GROUP);
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
        return gated(hookRegistry.hookBuilder(pluginId, origin));
    }

    @Override
    public HookBuilder hookClassInitializer(Class<?> origin) {
        return gated(hookRegistry.classInitializerBuilder(pluginId, origin));
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

    private HookBuilder gated(HookBuilder delegate) {
        return new FeatureGateHookBuilder(delegate);
    }

    private boolean isFeatureEnabled() {
        try {
            return featureState.getBoolean(
                    SuiteContract.FEATURE_STATE_KEY_PREFIX + featureId,
                    true);
        } catch (Throwable ignored) {
            // Remote preferences may be briefly unavailable during framework reconnect. Preserve the
            // previous all-enabled behavior instead of breaking target processes.
            return true;
        }
    }

    private String prefix(String message) {
        return "[" + pluginId + "] " + message;
    }

    /**
     * Keeps the physical/logical hook registration intact while making execution obey the current
     * YSuite feature switch. Re-enabling therefore does not create a second hook generation.
     */
    private final class FeatureGateHookBuilder implements HookBuilder {
        private final HookBuilder delegate;

        FeatureGateHookBuilder(HookBuilder delegate) {
            this.delegate = delegate;
        }

        @Override
        public HookBuilder setPriority(int priority) {
            delegate.setPriority(priority);
            return this;
        }

        @Override
        public HookBuilder setExceptionMode(ExceptionMode mode) {
            delegate.setExceptionMode(mode);
            return this;
        }

        @Override
        public HookBuilder setId(String id) {
            delegate.setId(id);
            return this;
        }

        @Override
        public HookHandle intercept(Hooker hooker) {
            Objects.requireNonNull(hooker);
            return delegate.intercept(chain -> {
                if (!isFeatureEnabled()) return chain.proceed();
                return hooker.intercept(chain);
            });
        }
    }
}
