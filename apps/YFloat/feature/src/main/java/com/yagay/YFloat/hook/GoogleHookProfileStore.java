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
    private volatile Context context;
    private volatile String identity = "";
    private final Uri uri;
    private boolean manualRequested;

    GoogleHookProfileStore(XposedModule module) {
        this.module = module;
        String host=HostIdentity.compiledPackage(BuildConfig.HOST_PACKAGE,"com.yagay.YFloat");
        uri=Uri.parse("content://"+host+".googlebridge");
        Context app=refreshContext();
        module.log(Log.INFO,TAG,"Google matcher APK="+identity
                +" cacheHost="+host+" contextReady="+(app!=null));
    }

    /** The target app may not exist at the initial LSPosed lifecycle callback. */
    static boolean contextReady() { return googleApplication()!=null; }

    private static Context googleApplication() {
        try {
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Object value=HiddenApiBypass.invoke(thread,null,"currentApplication");
            if(value instanceof Context c) {
                Context app=c.getApplicationContext();
                if(GoogleCtsContract.GOOGLE_PACKAGE.equals(app.getPackageName()))return app;
            }
        }catch(Throwable ignored){}
        return null;
    }

    private Context refreshContext() {
        Context live=googleApplication();
        if(live!=null) {
            context=live;
            if(identity.isBlank())identity=apkIdentity(live);
        }
        return context;
    }

    String read() {
        Context context=refreshContext();
        if (context==null || identity.isBlank()) {
            module.log(Log.WARN,TAG,"Google profile read skipped: app context unavailable");
            return "";
        }
        try {
            Bundle req=request();
            Bundle res=context.getContentResolver().call(uri,
                    GoogleCtsContract.METHOD_READ_HOOK_PROFILE,null,req);
            if(res==null || !res.containsKey(GoogleCtsContract.EXTRA_HOOK_SCAN_MANUAL)) {
                module.log(Log.WARN,TAG,"Google profile Provider not acknowledged; uri="+uri
                        +" apk="+identity+" result="+(res==null?"null":"empty"));
            }
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
        Context context=refreshContext();
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

    /** Only manual scans send progress; throttle to avoid startup IPC storms. */
    private long lastProgressReport;
    void reportProgress(String phase,int done,int total) {
        if(!manualRequested)return;
        Context context=refreshContext();
        if(context==null || identity.isBlank())return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastProgressReport<220L && !("registering".equals(phase)))return;
        lastProgressReport=now;
        try {
            Bundle req=request();
            req.putString(GoogleCtsContract.EXTRA_HOOK_SCAN_PHASE,phase);
            req.putInt(GoogleCtsContract.EXTRA_HOOK_SCAN_DONE,done);
            req.putInt(GoogleCtsContract.EXTRA_HOOK_SCAN_TOTAL,total);
            context.getContentResolver().call(uri,
                    GoogleCtsContract.METHOD_REPORT_HOOK_SCAN_PROGRESS,null,req);
        }catch(Throwable t) {
            module.log(Log.WARN,TAG,"Google scan progress IPC failed "
                    +t.getClass().getSimpleName());
        }
    }

    void reportScan(boolean success,int installedHooks,String method) {
        Context context=refreshContext();
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
