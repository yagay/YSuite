package com.yagay.ysuite.feature.ynotify.runtime;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import com.yagay.ysuite.runtime.RuntimeOwnerGate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YNotifyModule extends XposedModule {
    private static final Map<Object,PendingUiEvent>PENDING=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ConcurrentHashMap<String,Long>RECENT_UI=new ConcurrentHashMap<>();
    private volatile SharedPreferences runtimePrefs;
    private volatile String moduleHostPackage="";
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param){
        try{
            if(getModuleApplicationInfo()!=null&&getModuleApplicationInfo().packageName!=null){
                moduleHostPackage=getModuleApplicationInfo().packageName;
            }
        }catch(Throwable ignored){moduleHostPackage="";}
        RuntimeOwnerGate.announce("ynotify",moduleHostPackage);
        try{runtimePrefs=getRemotePreferences(YNotifyXposedRuntime.GROUP);}catch(Throwable ignored){}
    }
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param){
        String pkg=param.getPackageName();
        if(!param.isFirstPackage()||pkg==null||pkg.equals(hostPackage()))return;
        if(!RuntimeOwnerGate.shouldRun("ynotify",moduleHostPackage))return;
        try{
            ClassLoader cl=param.getClassLoader();
            if("android".equals(pkg))installSystemServerHooks(cl);else{
                installFrameworkHooks(pkg);installSnackbarHooks(cl,pkg);
                if("com.android.systemui".equals(pkg))installSystemUiHooks(cl);
            }
            markHeartbeat(pkg);
        }catch(Throwable ignored){}
    }
    @Override public boolean onHotReloading(XposedModuleInterface.HotReloadingParam param){return true;}
    @Override public void onHotReloaded(XposedModuleInterface.HotReloadedParam param){
        for(var h:param.getOldHookHandles())try{h.unhook();}catch(Throwable ignored){}
        try{runtimePrefs=getRemotePreferences(YNotifyXposedRuntime.GROUP);}catch(Throwable ignored){}
    }
    private void installFrameworkHooks(String pkg){
        hookNamed(Toast.class,"makeText",chain->{Context context=argContext(chain);String text=textArg(context,chain);Object result=chain.proceed();if(result instanceof Toast&&context!=null&&text!=null&&!text.isBlank())PENDING.put(result,new PendingUiEvent(context,"toast",text,Toast.class.getName()));return result;});
        hookNamed(Toast.class,"show",chain->{Object self=chain.getThisObject();Object result=chain.proceed();if(self instanceof Toast){PendingUiEvent pending=PENDING.remove(self);if(pending!=null)emitUi(pending.context,pkg,pending.type,pending.text,pending.className);else{Toast toast=(Toast)self;View view=toast.getView();emitUi(view==null?null:view.getContext(),pkg,"toast",collectText(view),toast.getClass().getName());}}return result;});
        hookNamed(Dialog.class,"show",chain->{Object result=chain.proceed();Object self=chain.getThisObject();if(self instanceof Dialog){Dialog d=(Dialog)self;View root=d.getWindow()==null?null:d.getWindow().getDecorView();emitUi(d.getContext(),pkg,"dialog",collectText(root),d.getClass().getName());}return result;});
        XposedInterface.Hooker popupHook=chain->{Object result=chain.proceed();Object self=chain.getThisObject();if(self instanceof PopupWindow){PopupWindow p=(PopupWindow)self;View content=p.getContentView();emitUi(content==null?null:content.getContext(),pkg,"popup",collectText(content),p.getClass().getName());}return result;};
        hookNamed(PopupWindow.class,"showAsDropDown",popupHook);hookNamed(PopupWindow.class,"showAtLocation",popupHook);
    }
    private void installSnackbarHooks(ClassLoader cl,String pkg){
        try{Class<?> snackbar=Class.forName("com.google.android.material.snackbar.Snackbar",false,cl);
            hookNamed(snackbar,"make",chain->{Context context=null;String text=null;for(Object arg:chain.getArgs()){if(arg instanceof View&&context==null)context=((View)arg).getContext();if(arg instanceof CharSequence)text=arg.toString();}Object result=chain.proceed();if(result!=null&&context!=null&&text!=null&&!text.isBlank())PENDING.put(result,new PendingUiEvent(context,"snackbar",text,snackbar.getName()));return result;});
            hookNamed(snackbar,"show",chain->{Object self=chain.getThisObject();Object result=chain.proceed();PendingUiEvent p=self==null?null:PENDING.remove(self);if(p!=null)emitUi(p.context,pkg,p.type,p.text,p.className);return result;});
        }catch(Throwable ignored){}
    }
    private void installSystemUiHooks(ClassLoader cl){
        String[] heads={"com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl","com.android.systemui.statusbar.policy.HeadsUpManager","com.android.systemui.statusbar.phone.HeadsUpManagerPhone"};
        for(String name:heads)try{Class<?> c=Class.forName(name,false,cl);for(String method:new String[]{"showNotification","addAlertingNotification"})hookNamed(c,method,chain->{Object r=chain.proceed();emitSurface(chain,"heads_up");return r;});}catch(Throwable ignored){}
        String[] bubbles={"com.android.systemui.bubbles.BubbleController","com.android.wm.shell.bubbles.BubbleController","com.android.wm.shell.bubbles.BubbleData"};
        for(String name:bubbles)try{Class<?> c=Class.forName(name,false,cl);for(String method:new String[]{"expandStackAndSelectBubble","expandStackAndSelectBubbleFromLauncher","setSelectedBubble"})hookNamed(c,method,chain->{Object r=chain.proceed();emitSurface(chain,"bubble");return r;});}catch(Throwable ignored){}
        for(String name:new String[]{"com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter","com.android.systemui.statusbar.notification.NotificationActivityStarter"})try{Class<?> c=Class.forName(name,false,cl);hookNamed(c,"launchFullScreenIntent",chain->{Object r=chain.proceed();emitSurface(chain,"full_screen");return r;});}catch(Throwable ignored){}
    }
    private void installSystemServerHooks(ClassLoader cl){
        try{Class<?> nms=Class.forName("com.android.server.notification.NotificationManagerService",false,cl);
            hookNamed(nms,"enqueueTextToast",chain->{Object result=chain.proceed();Context context=contextFromObject(chain.getThisObject());String pkg=packageArg(chain),text=toastTextArg(chain,pkg);if(pkg!=null&&text!=null&&!text.isBlank()&&shouldEmitUi(pkg,"toast",text,750L))emitUi(context,pkg,"toast",text,nms.getName()+"#enqueueTextToast");return result;});
            hookNamed(nms,"enqueueToast",chain->{Object result=chain.proceed();Context context=contextFromObject(chain.getThisObject());String pkg=packageArg(chain),text=toastTextArg(chain,pkg);if(text==null||text.isBlank())text="custom toast";if(pkg!=null&&shouldEmitUi(pkg,"toast",text,750L))emitUi(context,pkg,"toast",text,nms.getName()+"#enqueueToast");return result;});
        }catch(Throwable ignored){}
    }
    private int hookNamed(Class<?> clazz,String name,XposedInterface.Hooker hooker){int count=0;for(Method m:clazz.getDeclaredMethods()){if(!m.getName().equals(name))continue;try{m.setAccessible(true);hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);count++;}catch(Throwable ignored){}}return count;}
    private void emitUi(Context context,String pkg,String type,String text,String className){emit(context,pkg,"ui",type,text,className,"");}
    private void emitSurface(XposedInterface.Chain chain,String kind){String key=notificationKeyFromArgs(chain);if(key==null||key.isBlank())return;String pkg=packageFromArgs(chain);if(pkg==null||pkg.isBlank())pkg=packageFromNotificationKey(key);if(pkg==null||pkg.isBlank())pkg="com.android.systemui";emit(currentApplicationContext(),pkg,kind,"notification","",chain.getThisObject()==null?"":chain.getThisObject().getClass().getName(),key);}
    private void emit(Context context,String pkg,String kind,String type,String text,String className,String notificationKey){
        if(context==null||pkg==null||pkg.isBlank())return;text=text==null?"":text.trim();if("ui".equals(kind)&&text.isEmpty())return;
        try{SharedPreferences prefs=runtimePrefs;String secret=prefs==null?null:prefs.getString(YNotifyXposedRuntime.KEY_SECRET,null);if(secret==null||secret.isEmpty())return;long time=System.currentTimeMillis();String nonce=UUID.randomUUID().toString();String signature=YNotifyHookAuth.sign(secret,pkg,kind,type,text,className,notificationKey,time,nonce);Intent i=new Intent(YNotifyHookReceiver.ACTION);i.setClassName(hostPackage(),"com.yagay.ysuite.feature.ynotify.runtime.YNotifyHookReceiver");i.putExtra("package",pkg);i.putExtra("kind",kind);i.putExtra("type",type);i.putExtra("text",text);i.putExtra("class",className);i.putExtra("notification_key",notificationKey);i.putExtra("time",time);i.putExtra("nonce",nonce);i.putExtra("signature",signature);context.sendBroadcast(i);markHeartbeat(pkg);}catch(Throwable ignored){}
    }
    private String hostPackage(){try{SharedPreferences p=runtimePrefs;String host=p==null?null:p.getString(YNotifyXposedRuntime.KEY_HOST_PACKAGE,null);return host==null||host.isBlank()?"com.yagay.ysuite":host;}catch(Throwable ignored){return "com.yagay.ysuite";}}
    private void markHeartbeat(String pkg){try{if(runtimePrefs!=null)runtimePrefs.edit().putLong(YNotifyXposedRuntime.KEY_HEARTBEAT,System.currentTimeMillis()).putString(YNotifyXposedRuntime.KEY_HOOK_PACKAGE,pkg).apply();}catch(Throwable ignored){}}
    private static boolean shouldEmitUi(String pkg,String type,String text,long windowMs){long now=System.currentTimeMillis();String key=pkg+'\n'+type+'\n'+text;Long prev=RECENT_UI.put(key,now);if(RECENT_UI.size()>200){long cutoff=now-5000L;RECENT_UI.entrySet().removeIf(e->e.getValue()<cutoff);}return prev==null||now-prev>windowMs;}
    private static Context argContext(XposedInterface.Chain chain){for(Object a:chain.getArgs())if(a instanceof Context)return(Context)a;return null;}
    private static String textArg(Context c,XposedInterface.Chain chain){for(Object a:chain.getArgs())if(a instanceof CharSequence)return a.toString();if(c!=null&&chain.getArgs().size()>1&&chain.getArg(1) instanceof Integer)try{return c.getText((Integer)chain.getArg(1)).toString();}catch(Throwable ignored){}return null;}
    private static String packageArg(XposedInterface.Chain chain){String fallback=null;for(Object a:chain.getArgs())if(a instanceof String){String s=((String)a).trim();if(s.isEmpty())continue;if(fallback==null)fallback=s;if(s.indexOf('.')>0&&s.indexOf(' ')<0&&s.length()<=255)return s;}return fallback;}
    private static String toastTextArg(XposedInterface.Chain chain,String pkg){for(Object a:chain.getArgs())if(a instanceof CharSequence){String v=a.toString();if(v.isBlank()||v.equals(pkg))continue;return v;}return null;}
    private static String collectText(View root){LinkedHashSet<String>out=new LinkedHashSet<>();collect(root,out,0);return String.join("\n",out);}
    private static void collect(View v,LinkedHashSet<String>out,int depth){if(v==null||depth>16)return;if(v instanceof TextView){CharSequence cs=((TextView)v).getText();if(cs!=null&&!cs.toString().trim().isEmpty())out.add(cs.toString().trim());}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<Math.min(g.getChildCount(),100);i++)collect(g.getChildAt(i),out,depth+1);}}
    private static StatusBarNotification extractSbn(Object entry){if(entry==null)return null;if(entry instanceof StatusBarNotification)return(StatusBarNotification)entry;for(String mname:new String[]{"getSbn","getStatusBarNotification"})try{Method m=entry.getClass().getMethod(mname);Object v=m.invoke(entry);if(v instanceof StatusBarNotification)return(StatusBarNotification)v;}catch(Throwable ignored){}for(String fname:new String[]{"mSbn","sbn"})try{Field f=findField(entry.getClass(),fname);if(f!=null){f.setAccessible(true);Object v=f.get(entry);if(v instanceof StatusBarNotification)return(StatusBarNotification)v;}}catch(Throwable ignored){}return null;}
    private static String extractKey(Object entry){if(entry==null)return null;try{Method m=entry.getClass().getMethod("getKey");Object v=m.invoke(entry);return v==null?null:v.toString();}catch(Throwable ignored){return null;}}
    private static String notificationKeyFromArgs(XposedInterface.Chain chain){for(Object a:chain.getArgs()){StatusBarNotification s=extractSbn(a);if(s!=null&&s.getKey()!=null)return s.getKey();String k=extractKey(a);if(looksLikeKey(k))return k;if(a instanceof String&&looksLikeKey((String)a))return(String)a;}return null;}
    private static String packageFromArgs(XposedInterface.Chain chain){for(Object a:chain.getArgs()){StatusBarNotification s=extractSbn(a);if(s!=null&&s.getPackageName()!=null)return s.getPackageName();}return null;}
    private static boolean looksLikeKey(String v){if(v==null||v.isBlank())return false;int f=v.indexOf('|');return f>=0&&v.indexOf('|',f+1)>f;}
    private static String packageFromNotificationKey(String key){if(!looksLikeKey(key))return null;String[]p=key.split("\\|",-1);return p.length>1&&!p[1].isBlank()?p[1]:null;}
    private static Field findField(Class<?>t,String name){Class<?>c=t;while(c!=null&&c!=Object.class){try{return c.getDeclaredField(name);}catch(Throwable ignored){c=c.getSuperclass();}}return null;}
    private static Context contextFromObject(Object o){if(o==null)return null;Class<?>c=o.getClass();while(c!=null&&c!=Object.class){for(Field f:c.getDeclaredFields())try{if(Context.class.isAssignableFrom(f.getType())){f.setAccessible(true);Object v=f.get(o);if(v instanceof Context){Context x=(Context)v;return x.getApplicationContext()!=null?x.getApplicationContext():x;}}}catch(Throwable ignored){}c=c.getSuperclass();}return null;}
    private static Context currentApplicationContext(){try{Class<?>at=Class.forName("android.app.ActivityThread");Method m=at.getDeclaredMethod("currentApplication");Object app=m.invoke(null);return app instanceof Context?((Context)app).getApplicationContext():null;}catch(Throwable ignored){return null;}}
    private static final class PendingUiEvent{final Context context;final String type;final String text;final String className;PendingUiEvent(Context c,String t,String x,String n){Context app=c.getApplicationContext();context=app!=null?app:c;type=t;text=x;className=n;}}
}
