package com.yagay.YSuite.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.github.libxposed.api.XposedModule;

/**
 * The only libxposed entry point packaged by YSuite.
 *
 * <p>Standalone feature repositories keep their original XposedModule classes for independent APK
 * builds. Inside YSuite those classes are instantiated as logical plugins and attached to a
 * YSuite-owned {@link SuitePluginXposedInterface}. Therefore every hook request passes through the
 * host registry and duplicate targets share one physical hook.</p>
 */
public final class SuiteXposedModule extends XposedModule {
    private static final String TAG = "YSuite.Xposed";

    private final List<PluginRuntime> runtimes = new ArrayList<>();
    private SuiteHookRegistry hookRegistry;
    private boolean initialized;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        if (initialized) return;
        initialized = true;
        hookRegistry = new SuiteHookRegistry(this);

        for (GeneratedXposedPlugins.Entry generated : GeneratedXposedPlugins.ENTRIES) {
            loadPlugin(new PluginSpec(generated.id(), generated.entryClassName()), param);
        }

        log(
                Log.INFO,
                TAG,
                "YSuite hook host loaded; process=" + param.getProcessName()
                        + " plugins=" + runtimes.size()
                        + " singleEntry=true");
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        dispatch("system_server", module -> module.onSystemServerStarting(param));
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        dispatch(
                "packageLoaded:" + param.getPackageName(),
                module -> module.onPackageLoaded(param));
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        dispatch(
                "packageReady:" + param.getPackageName(),
                module -> module.onPackageReady(param));
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        // YSuite owns hook generations. Feature-level hot reload would bypass the shared registry
        // and could leave logical handles from different plugin generations mixed together.
        log(
                Log.WARN,
                TAG,
                "framework hot reload rejected; use YSuite target reload so the complete shared "
                        + "hook graph is recreated atomically");
        return false;
    }

    private void loadPlugin(PluginSpec spec, ModuleLoadedParam param) {
        try {
            Class<?> raw = Class.forName(
                    spec.entryClassName,
                    true,
                    SuiteXposedModule.class.getClassLoader());
            if (!XposedModule.class.isAssignableFrom(raw)) {
                log(
                        Log.ERROR,
                        TAG,
                        "plugin entry is not XposedModule; plugin=" + spec.id
                                + " class=" + spec.entryClassName);
                return;
            }

            Constructor<?> constructor = raw.getDeclaredConstructor();
            constructor.setAccessible(true);
            XposedModule module = (XposedModule) constructor.newInstance();

            PluginRuntime runtime = new PluginRuntime(spec, module);
            SuitePluginXposedInterface proxy =
                    new SuitePluginXposedInterface(this, hookRegistry, spec.id);

            // attachFramework is intentionally used by the host as the plugin boundary. Feature code
            // sees a normal libxposed interface, while the proxy keeps physical hook ownership in
            // YSuite. Standalone APKs are still attached directly by LSPosed.
            module.attachFramework(proxy, runtime::detach);
            runtimes.add(runtime);

            try {
                module.onModuleLoaded(param);
                log(
                        Log.INFO,
                        TAG,
                        "plugin attached; plugin=" + spec.id
                                + " class=" + spec.entryClassName);
            } catch (Throwable error) {
                log(
                        Log.ERROR,
                        TAG,
                        "plugin onModuleLoaded failed; plugin=" + spec.id,
                        error);
            }
        } catch (Throwable error) {
            log(
                    Log.ERROR,
                    TAG,
                    "plugin load failed; plugin=" + spec.id
                            + " class=" + spec.entryClassName,
                    error);
        }
    }

    private void dispatch(String event, Consumer<XposedModule> callback) {
        for (PluginRuntime runtime : runtimes) {
            if (runtime.detached.get()) continue;
            try {
                callback.accept(runtime.module);
            } catch (Throwable error) {
                // A plugin lifecycle failure is isolated exactly like a protected shared hook.
                log(
                        Log.ERROR,
                        TAG,
                        "plugin lifecycle failed; event=" + event
                                + " plugin=" + runtime.spec.id,
                        error);
            }
        }
    }

    private record PluginSpec(String id, String entryClassName) {
    }

    private final class PluginRuntime {
        final PluginSpec spec;
        final XposedModule module;
        final AtomicBoolean detached = new AtomicBoolean(false);

        PluginRuntime(PluginSpec spec, XposedModule module) {
            this.spec = spec;
            this.module = module;
        }

        void detach() {
            if (detached.compareAndSet(false, true)) {
                log(Log.INFO, TAG, "plugin detached; plugin=" + spec.id);
            }
        }
    }
}
