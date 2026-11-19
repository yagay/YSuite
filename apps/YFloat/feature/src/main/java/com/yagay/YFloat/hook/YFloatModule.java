package com.yagay.YFloat.hook;

import android.util.Log;
import com.yagay.suite.api.RuntimeHandoffGate;
import com.yagay.suite.api.RuntimeOwnerGate;
import io.github.libxposed.api.XposedModule;

/**
 * YFloat hooks only framework secure screenshot capture.
 * Google App/Lens recognition and process injection have been removed.
 */
public final class YFloatModule extends XposedModule {
    private static final String TAG="YFloat-LSPosed";
    private static final String FEATURE_ID="yfloat";
    private LsposedRuntimeProvider runtimeProvider;
    private boolean suiteHost;

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        String process=param.getProcessName()==null?"":param.getProcessName();
        suiteHost=RuntimeHandoffGate.isSuiteHost(getModuleApplicationInfo());
        if(suiteHost&&!process.isEmpty())
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID,process);
        runtimeProvider=new LsposedRuntimeProvider(this,process);
        runtimeProvider.start();
        log(Log.INFO,TAG,"YFloat secure-capture provider="+runtimeProvider.isActive()
                +" owner="+RuntimeOwnerGate.readOwner(FEATURE_ID));
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        final String target="system_server";
        if(suiteHost)RuntimeHandoffGate.markSuiteActive(FEATURE_ID,target);
        else if(!RuntimeHandoffGate.shouldStandaloneFallback(FEATURE_ID,target))return;
        if(runtimeProvider==null)return;
        try {
            new SecureScreenshotHook(this,runtimeProvider,param.getClassLoader()).install();
        }catch(Throwable t){
            log(Log.ERROR,TAG,"Secure screenshot hook unavailable",t);
        }
    }
}
