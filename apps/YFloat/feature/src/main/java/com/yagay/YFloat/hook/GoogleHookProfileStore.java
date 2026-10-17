package com.yagay.YFloat.hook;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.yagay.YFloat.BuildConfig;
import com.yagay.YFloat.GoogleCtsContract;
import com.yagay.suite.api.HostIdentity;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import io.github.libxposed.api.XposedModule;

/** Read/write versioned symbol profiles via YFloat's UID-checked host Provider. */
final class GoogleHookProfileStore {
    private static final String TAG = "YFloat-GoogleV2";
    private final XposedModule module;
    private final Context context;
    private final String identity;
    private final Uri uri;
    private boolean manualRequested;

    GoogleHookProfileStore(XposedModule module) {
        this.module = module;
        Context app = null;
        try {
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Object value=HiddenApiBypass.invoke(thread,null,"currentApplication");
            if(value instanceof Context c) app=c.getApplicationContext();
        } catch(Throwable ignored) { }
        context=app;
        identity=apkIdentity(app);
        String host=HostIdentity.compiledPackage(BuildConfig.HOST_PACKAGE,"com.yagay.YFloat");
        uri=Uri.parse("content://"+host+".googlebridge");
        module.log(Log.INFO,TAG,"Google matcher APK="+identity
                +" cacheHost="+host+" contextReady="+(context!=null));
    }

    String read() {
        if (context==null || identity.isBlank()) return "";
        try {
            Bundle req=request();
            Bundle res=context.getContentResolver().call(uri,
                    GoogleCtsContract.METHOD_READ_HOOK_PROFILE,null,req);
            manualRequested=res!=null
                    && res.getBoolean(GoogleCtsContract.EXTRA_HOOK_SCAN_MANUAL,false);
            if (manualRequested) {
                module.log(Log.INFO,TAG,"manual 17.60 semantic scan requested: cache bypassed");
                return "";
            }
            return res==null?"":res.getString(GoogleCtsContract.EXTRA_HOOK_SYMBOLS,"");
        } catch(Throwable t) {
            module.log(Log.WARN,TAG,"Google cached matcher read failed "
                    +t.getClass().getSimpleName());
            return "";
        }
    }

    boolean save(String symbols) {
        if(context==null || identity.isBlank() || symbols==null || symbols.isBlank()) return false;
        try {
            Bundle req=request();
            req.putString(GoogleCtsContract.EXTRA_HOOK_SYMBOLS,symbols);
            Bundle res=context.getContentResolver().call(uri,
                    GoogleCtsContract.METHOD_WRITE_HOOK_PROFILE,null,req);
            boolean saved=res!=null && res.getBoolean("saved",false);
            module.log(saved?Log.INFO:Log.WARN,TAG,
                    "Google matcher cache commit="+saved+" APK="+identity);
            return saved;
        } catch(Throwable t) {
            module.log(Log.WARN,TAG,"Google cached matcher write failed "
                    +t.getClass().getSimpleName());
            return false;
        }
    }

    boolean manualRequested() { return manualRequested; }

    void reportScan(boolean success,int installedHooks,String method) {
        if(context==null || identity.isBlank()) {
            module.log(Log.WARN,TAG,"Google scan Toast unavailable: process context missing");
            return;
        }
        try {
            Bundle req=request();
            req.putBoolean(GoogleCtsContract.EXTRA_HOOK_SCAN_SUCCESS,success);
            req.putInt(GoogleCtsContract.EXTRA_HOOK_SCAN_COUNT,installedHooks);
            req.putString(GoogleCtsContract.EXTRA_HOOK_SCAN_METHOD,
                    method==null?"":method);
            Bundle res=context.getContentResolver().call(uri,
                    GoogleCtsContract.METHOD_REPORT_HOOK_SCAN,null,req);
            module.log(Log.INFO,TAG,"Google scan completion reported="+
                    (res!=null&&res.getBoolean("reported",false))
                    +" success="+success+" hooks="+installedHooks);
        } catch(Throwable t) {
            module.log(Log.WARN,TAG,"Google scan completion Toast report failed "
                    +t.getClass().getSimpleName());
        }
    }

    String identity() { return identity; }

    private Bundle request() {
        Bundle result=new Bundle();
        result.putString(GoogleCtsContract.EXTRA_HOOK_APK_IDENTITY,identity);
        result.putString(GoogleCtsContract.EXTRA_HOOK_FINGERPRINT,
                BuildConfig.GOOGLE_HOOK_FINGERPRINT);
        return result;
    }

    private static String apkIdentity(Context context) {
        if(context==null) return "";
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(
                    GoogleCtsContract.GOOGLE_PACKAGE,0);
            String name=info.versionName==null?"unknown":info.versionName;
            return name+"/"+info.getLongVersionCode()+"@"+info.lastUpdateTime;
        }catch(Throwable ignored){return "";}
    }
}
