package com.yagay.YFloat.hook;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import com.yagay.YFloat.LsposedRuntimeConfig;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Framework Remote Preferences for the secure-screenshot hook only. */
final class LsposedRuntimeProvider {
    private final XposedModule module;
    private final String processName;
    private volatile boolean active;
    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    LsposedRuntimeProvider(XposedModule module,String processName){
        this.module=module;
        this.processName=processName==null?"":processName;
    }

    void start(){
        if((module.getFrameworkProperties()&XposedInterface.PROP_CAP_REMOTE)==0L){
            module.log(Log.WARN,"YFloat-LSPosed","Remote preferences unavailable");
            active=false;
            return;
        }
        try{
            preferences=module.getRemotePreferences(LsposedRuntimeConfig.GROUP);
            listener=(prefs,key)->{
                if(LsposedRuntimeConfig.K_SCHEMA_VERSION.equals(key)
                        ||LsposedRuntimeConfig.K_ENHANCED_MODE.equals(key)
                        ||LsposedRuntimeConfig.K_LSPOSED_ENABLED.equals(key)
                        ||LsposedRuntimeConfig.K_SECURE_SCREENSHOT_ENABLED.equals(key)
                        ||LsposedRuntimeConfig.K_SECURE_CAPTURE_ARMED_UNTIL.equals(key))
                    refresh();
            };
            preferences.registerOnSharedPreferenceChangeListener(listener);
            refresh();
        }catch(Throwable error){
            active=false;
            module.log(Log.WARN,"YFloat-LSPosed",
                    "Secure capture provider unavailable in "+processName,error);
        }
    }
    boolean isActive(){return active;}
    boolean isSecureCaptureArmed(){
        if(!active||preferences==null)return false;
        try{
            return LsposedRuntimeConfig.isSecureCaptureActive(
                    preferences,SystemClock.elapsedRealtime());
        }catch(Throwable ignored){return false;}
    }
    private void refresh(){
        try{active=LsposedRuntimeConfig.isEnabled(preferences);}
        catch(Throwable ignored){active=false;}
    }
}
