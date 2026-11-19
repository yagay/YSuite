package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tracks only installed framework hooks. App-only OCR/UI updates never require
 * a Google App restart, and Google App is not a YFloat injection target.
 */
final class HookReloadManager {
    private static final String PREF="yfloat_hook_reload";
    private static final String K_SYSTEMUI="systemui_hook_fingerprint_applied_v2";
    private static final String K_SYSTEM="system_server_hook_fingerprint_applied_v2";
    private static final String K_BOOT="hook_fingerprint_boot_id_v2";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"YFloat-hook-reload");
        t.setDaemon(true);return t;
    });
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private static final AtomicBoolean AUTO=new AtomicBoolean();
    private static volatile Context appContext;

    static final class Result {
        final boolean success;
        final boolean systemUiReloaded;
        final boolean systemServerNeedsReboot;
        final String detail;
        Result(boolean success,boolean systemUiReloaded,boolean systemServerNeedsReboot,
               String detail){
            this.success=success;this.systemUiReloaded=systemUiReloaded;
            this.systemServerNeedsReboot=systemServerNeedsReboot;
            this.detail=detail==null?"":detail;
        }
        String userMessage(Context c){
            if(!success)return detail.isBlank()
                    ?c.getString(R.string.yfloat_hook_reload_failed):detail;
            StringBuilder msg=new StringBuilder(systemUiReloaded
                    ?c.getString(R.string.yfloat_hook_systemui_reloaded)
                    :c.getString(R.string.yfloat_hook_no_changes));
            if(systemServerNeedsReboot)msg.append('\n')
                    .append(c.getString(R.string.yfloat_hook_system_reboot_required));
            return msg.toString();
        }
    }
    private HookReloadManager(){}

    static void initialize(Context context){
        if(context==null)return;
        Context app=context.getApplicationContext();
        appContext=app;
        SharedPreferences prefs=preferences(app);
        String boot=readBootId();
        String previous=prefs.getString(K_BOOT,"");
        boolean first=!prefs.contains(K_SYSTEMUI)||!prefs.contains(K_SYSTEM);
        boolean rebooted=!boot.isBlank()&&!previous.isBlank()&&!boot.equals(previous);
        SharedPreferences.Editor edit=prefs.edit();
        if(first||rebooted)edit.putString(K_SYSTEMUI,BuildConfig.SYSTEMUI_HOOK_FINGERPRINT)
                .putString(K_SYSTEM,BuildConfig.SYSTEM_SERVER_HOOK_FINGERPRINT);
        if(!boot.isBlank())edit.putString(K_BOOT,boot);
        edit.apply();
        DiagnosticLog.i(app,"HOOK_RELOAD","init first="+first+" rebooted="+rebooted+
                " systemui="+shortHash(BuildConfig.SYSTEMUI_HOOK_FINGERPRINT)+
                " system="+shortHash(BuildConfig.SYSTEM_SERVER_HOOK_FINGERPRINT));
    }

    static void autoReloadChangedTargets(Context context,LsposedStatusManager.Snapshot snapshot){
        if(context==null||snapshot==null||!snapshot.serviceConnected)return;
        FloatSettings s=new FloatSettings(context);
        if(!s.enhancedMode()||!s.lsposedEnabled()||!s.canUseRoot()
                ||!systemUiNeedsReload(context,snapshot)
                ||!AUTO.compareAndSet(false,true))return;
        boolean started=reloadChangedTargetsAsync(context,result->{
            AUTO.set(false);
            DiagnosticLog.i(context,"HOOK_RELOAD","auto systemui success="+result.success);
        });
        if(!started)AUTO.set(false);
    }

    static boolean fingerprintNeedsReload(String current,String applied,boolean running){
        return current!=null&&!current.isBlank()&&!current.equals(applied)&&running;
    }
    static boolean systemUiNeedsReload(Context context,LsposedStatusManager.Snapshot snapshot){
        if(context==null||snapshot==null||!snapshot.systemUiScopeEnabled)return false;
        boolean running=hasProcess(snapshot.runningProcesses,"com.android.systemui");
        if(!fingerprintNeedsReload(BuildConfig.SYSTEMUI_HOOK_FINGERPRINT,
                preferences(context).getString(K_SYSTEMUI,""),running))return false;
        return true;
    }
    static boolean systemServerNeedsReboot(Context context){
        return context!=null&&!BuildConfig.SYSTEM_SERVER_HOOK_FINGERPRINT.equals(
                preferences(context).getString(K_SYSTEM,""));
    }
    static boolean systemServerHookCurrent(){
        return appContext!=null&&!systemServerNeedsReboot(appContext);
    }
    static boolean systemUiHookCurrent(Context context){
        return context!=null&&BuildConfig.SYSTEMUI_HOOK_FINGERPRINT.equals(
                preferences(context).getString(K_SYSTEMUI,""));
    }
    static boolean reloadChangedTargetsAsync(Context context,Consumer<Result> callback){
        if(context==null||!BUSY.compareAndSet(false,true))return false;
        Context app=context.getApplicationContext();
        LsposedStatusManager.Snapshot current=LsposedStatusManager.snapshot();
        boolean reload=systemUiNeedsReload(app,current);
        boolean systemReboot=systemServerNeedsReboot(app);
        if(!reload){
            BUSY.set(false);
            deliver(app,callback,new Result(true,false,systemReboot,""));
            return true;
        }
        IO.execute(()->{
            Result outcome;
            try{
                if(!new FloatSettings(app).canUseRoot()){
                    outcome=new Result(false,false,systemReboot,
                            app.getString(R.string.yfloat_hook_reload_requires_root));
                }else{
                    RootCommandExecutor.Result cmd=RootCommandExecutor.runText(
                            killSystemUiScript(),10L,24*1024);
                    if(cmd.success())preferences(app).edit()
                            .putString(K_SYSTEMUI,BuildConfig.SYSTEMUI_HOOK_FINGERPRINT).apply();
                    outcome=new Result(cmd.success(),cmd.success(),systemReboot,
                            cmd.success()?"":cmd.failureMessage(
                                    app.getString(R.string.yfloat_hook_reload_timeout)));
                }
            }catch(Throwable error){
                outcome=new Result(false,false,systemReboot,error.toString());
            }finally{BUSY.set(false);}
            LsposedStatusManager.refreshAsync();
            deliver(app,callback,outcome);
        });
        return true;
    }

    static String statusSummary(Context context,LsposedStatusManager.Snapshot snapshot){
        if(context==null)return "";
        boolean ui=systemUiNeedsReload(context,snapshot);
        boolean system=systemServerNeedsReboot(context);
        return "SystemUI: "+context.getString(ui
                ?R.string.yfloat_hook_state_reload:R.string.yfloat_hook_state_current)
                +"\nSystem server: "+context.getString(system
                ?R.string.yfloat_hook_state_reboot:R.string.yfloat_hook_state_current);
    }
    private static String killSystemUiScript(){
        return "COUNT=0; for PID in $(ps -A -o PID,NAME 2>/dev/null | "
                +"awk '$2 ~ /^com[.]android[.]systemui(:|$)/ {print $1}'); "
                +"do kill -9 \"$PID\" >/dev/null 2>&1 || true; COUNT=$((COUNT+1)); done; "
                +"echo YFLOAT_SYSTEMUI_KILLED=$COUNT";
    }
    private static String readBootId(){
        try(BufferedReader br=new BufferedReader(new FileReader(
                "/proc/sys/kernel/random/boot_id"))){
            String value=br.readLine();return value==null?"":value.trim();
        }catch(Throwable ignored){return "";}
    }
    private static SharedPreferences preferences(Context ctx){
        return ctx.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private static boolean hasProcess(List<String> processes,String name){
        if(processes==null)return false;
        for(String process:processes)
            if(process!=null&&(process.startsWith(name+"[")
                    ||process.startsWith(name+":")||process.equals(name)))return true;
        return false;
    }
    private static void deliver(Context app,Consumer<Result> callback,Result result){
        if(callback!=null)app.getMainExecutor().execute(()->callback.accept(result));
    }
    private static String shortHash(String value){
        return value==null||value.isBlank()?"none":value.substring(0,Math.min(10,value.length()));
    }
}
