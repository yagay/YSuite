package com.yagay.YFloat.hook;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import com.yagay.suite.api.RuntimeHandoffGate;
import com.yagay.suite.api.RuntimeOwnerGate;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point for YFloat controlled providers. */
public final class YFloatModule extends XposedModule {
    private static final String TAG = "YFloat-LSPosed";
    private static final String FEATURE_ID = "yfloat";
    private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private boolean googleHookInstalled;
    private boolean googleHookInitializationPending;
    private LsposedRuntimeProvider runtimeProvider;
    private boolean suiteHost;
    private String processName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName() == null ? "" : param.getProcessName();
        suiteHost = RuntimeHandoffGate.isSuiteHost(getModuleApplicationInfo());
        if (suiteHost && !processName.isEmpty()) {
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID, processName);
        }

        runtimeProvider = new LsposedRuntimeProvider(this, processName);
        runtimeProvider.start();
        log(Log.INFO, TAG,
                "Module loaded in " + processName
                        + "; provider=" + (runtimeProvider.isActive() ? "enabled" : "disabled")
                        + "; owner=" + RuntimeOwnerGate.readOwner(FEATURE_ID)
                        + "; suiteHost=" + suiteHost);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        final String target = "system_server";
        if (suiteHost) {
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID, target);
        } else if (!RuntimeHandoffGate.shouldStandaloneFallback(FEATURE_ID, target)) {
            log(Log.INFO, TAG, "Standalone system_server hook passive; live YSuite hook acknowledged");
            return;
        }

        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) {
            log(Log.ERROR, TAG, "system_server provider missing; secure screenshot hook not installed");
            return;
        }
        try {
            new SecureScreenshotHook(this, provider, param.getClassLoader()).install();
        } catch (Throwable t) {
            // Never let an optional screenshot enhancement destabilize system_server startup.
            log(Log.ERROR, TAG, "Failed to install controlled secure screenshot hooks", t);
        }
        // Google CTS isolation is handled entirely inside YFloat-marked Google App sessions.
        // Never hook ContextualSearch in system_server: a process-wide component block can
        // interfere with the user's native Home/gesture Circle to Search session.
        log(Log.INFO, TAG,
                "Google contextual-search system_server blocker disabled; native CTS left untouched");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // Wait for PackageReady: Google needs its finalized application ClassLoader.
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (googleHookInstalled || !GOOGLE_PACKAGE.equals(param.getPackageName())) return;
        final String target=processName.isEmpty()?param.getPackageName():processName;
        log(Log.INFO,TAG,"Google PackageReady received process="+target
                +" suiteHost="+suiteHost);
        if(suiteHost) RuntimeHandoffGate.markSuiteActive(FEATURE_ID,target);
        else if(!RuntimeHandoffGate.shouldStandaloneFallback(FEATURE_ID,target)) {
            log(Log.INFO,TAG,"Google Hook passive; active YSuite owner="+target);
            return;
        }
        if(runtimeProvider==null || param.getClassLoader()==null) {
            log(Log.WARN,TAG,"Google PackageReady missing provider or classloader");
            return;
        }
        if(googleHookInitializationPending)return;
        googleHookInitializationPending=true;
        // Install lifecycle interception now; optional DEX search runs later.
        // Delaying ALL callbacks until Application Context exists previously
        // allowed Google's first voice session to bypass YFloat entirely.
        try {
            googleHookInstalled=new GoogleV2HookEngine(
                    this,runtimeProvider,param.getClassLoader()).install();
            log(googleHookInstalled?Log.INFO:Log.WARN,TAG,
                    "Google bootstrap installed="+googleHookInstalled
                    +" provider="+runtimeProvider.isActive()+" process="+target
                    +" semanticSelection=asynchronous");
        }catch(Throwable t){
            log(Log.ERROR,TAG,"Google bootstrap initialization failed; process="+target,t);
        }finally {
            googleHookInitializationPending=false;
        }
    }
}
