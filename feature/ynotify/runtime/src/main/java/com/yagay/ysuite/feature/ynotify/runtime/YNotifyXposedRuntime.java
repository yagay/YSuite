package com.yagay.ysuite.feature.ynotify.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class YNotifyXposedRuntime implements XposedServiceHelper.OnServiceListener {
    public static final String GROUP="ynotify_runtime";
    public static final String KEY_SECRET="event_secret";
    public static final String KEY_HOST_PACKAGE="host_package";
    public static final String KEY_HEARTBEAT="hook_heartbeat";
    public static final String KEY_HOOK_PACKAGE="hook_package";
    private static volatile YNotifyXposedRuntime instance;
    private final Context context;
    private YNotifyXposedRuntime(Context context){this.context=context.getApplicationContext();XposedServiceHelper.registerListener(this);}
    public static void ensure(Context context){
        if(instance!=null)return;
        synchronized(YNotifyXposedRuntime.class){if(instance==null)instance=new YNotifyXposedRuntime(context);}
    }
    @Override public void onServiceBind(XposedService service){
        try{
            if(service.getApiVersion()<102)return;
            if((service.getFrameworkProperties()&XposedService.PROP_CAP_REMOTE)==0)return;
            SharedPreferences remote=service.getRemotePreferences(GROUP);
            remote.edit().putString(KEY_SECRET,YNotifyHookAuth.ensureLocalSecret(context))
                    .putString(KEY_HOST_PACKAGE,context.getPackageName())
                    .putLong("app_sync_at",System.currentTimeMillis()).commit();
        }catch(Throwable ignored){}
    }
    @Override public void onServiceDied(XposedService service){}
}
