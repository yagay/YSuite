package com.yagay.YFloat.hook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.yagay.YFloat.GoogleCtsContract;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;

/**
 * YFloat Google V2, from-scratch runtime owner.
 *
 * The former GoogleCtsRuntimeInspector, CanonicalFrameLayer, LensViewportHook,
 * LegacyLifecycleHooks and menu/gesture controllers are NOT instantiated.
 * Existing GoogleSelectionAdapter, GoogleTextSelectionLiveHook,
 * GoogleLensFrameCapture and GoogleBridgeSender are input/IPC adapters ONLY;
 * they never own a UI session or mutate the Google frozen screen.
 */
final class GoogleV2HookEngine {
    private static final String TAG = "YFloat-GoogleV2";
    private final XposedModule module;
    private final LsposedRuntimeProvider provider;
    private final ClassLoader loader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final GoogleBridgeSender transport;
    private final GoogleLensFrameCapture frames;
    private final GoogleTextSelectionLiveHook words;
    private final GoogleRegionGestureHook gestures;
    private final GoogleV2Viewport viewport;
    private final GoogleV2SearchGuard nativeSearch;
    private final GoogleV2SearchChrome nativeChrome;
    private final GoogleSelectionAdapter.Binding selection;
    private final GoogleHookProfileStore profileStore;
    private final AtomicInteger revision = new AtomicInteger();
    private volatile boolean loggedInactiveSelection;
    private volatile boolean loggedRejectedActivation;
    private volatile String token = "";
    private volatile Activity owner;
    private volatile Object voice;
    private volatile boolean textMode;
    private volatile boolean regionMode;
    private volatile Rect currentBounds;
    private volatile String currentText = "";

    GoogleV2HookEngine(XposedModule module, LsposedRuntimeProvider provider,
                       ClassLoader loader) {
        this.module=module;this.provider=provider;this.loader=loader;
        transport=new GoogleBridgeSender(module,this::context,()->token,
                this::active,provider::diagnosticsEnabled);
        frames=new GoogleLensFrameCapture(module,loader,this::active,
                this::onOriginalFrame,this::logEvent,this::context);
        words=new GoogleTextSelectionLiveHook(module,loader,this::active,
                ()->owner,this::onWords);
        gestures=new GoogleRegionGestureHook(module,loader,this::active,
                this::onGesture,(action,x,y,detail)->{});
        viewport=new GoogleV2Viewport(module,this::active,this::logEvent);
        nativeSearch=new GoogleV2SearchGuard(module,loader,
                ()->active()&&(textMode||regionMode),this::logEvent);
        nativeChrome=new GoogleV2SearchChrome(
                ()->active()&&(textMode||regionMode),this::logEvent);
        profileStore=new GoogleHookProfileStore(module);
        selection=GoogleSelectionAdapter.resolve(loader,profileStore);
    }

    boolean install() {
        if (!selection.available()) {
            module.log(Log.WARN,TAG,"Google selection Hook unresolved: "+selection.detail());
            if (GoogleSelectionAdapter.scanned(selection)
                    || profileStore.manualRequested())
                profileStore.reportScan(false,0,selection.detail());
            return false;
        }
        int count=0;
        count+=hookVoiceShow();
        count+=hookVoiceFrame();
        count+=hookActivities();
        count+=viewport.install();
        count+=nativeSearch.install();
        count+=frames.install();
        count+=words.install();
        count+=gestures.install();
        int selectionHooks=hookSelection();
        count+=selectionHooks;
        if(selectionHooks>0)
            GoogleSelectionAdapter.recordInstalled(selection,profileStore);
        if (GoogleSelectionAdapter.scanned(selection)
                || profileStore.manualRequested()) {
            String method = selection.method() == null ? "no selection method"
                    : selection.method().getDeclaringClass().getSimpleName()
                            + "." + selection.method().getName();
            profileStore.reportScan(selectionHooks>0,count,
                    "selection=" + method + " source=" + selection.source());
        }
        module.log(Log.INFO,TAG,"V2 installed hooks="+count
                +" selection="+selection.source()
                +" confidence="+selection.confidence()
                +" compatibility="+(selection.available() ? "selection_bound" : "selection_unavailable")
                +" detail="+selection.detail()
                +" verifiedBinding="+(selectionHooks>0)
                +" old_runtime_not_installed=true");
        return selectionHooks>0;
    }

    private boolean active() {
        String key=token;
        return provider.isActive()&&!key.isBlank()&&provider.ownsGoogleCtsSession(key);
    }

    private void activate(String key, Object voiceOwner, String source) {
        if(key==null||key.isBlank()||!provider.isActive()
                ||!provider.ownsGoogleCtsSession(key)) {
            if(!loggedRejectedActivation) {
                loggedRejectedActivation=true;
                module.log(Log.WARN,TAG,"Google CTS activation rejected"
                        +" provider="+provider.isActive()
                        +" tokenPresent="+(key!=null&&!key.isBlank()));
            }
            return;
        }
        loggedRejectedActivation=false;
        if(!key.equals(token)){
            transport.reset();
            gestures.reset();
            viewport.reset();
            nativeChrome.reset();
            revision.incrementAndGet();
            textMode=false;regionMode=false;currentText="";currentBounds=null;
        }
        token=key;
        if(voiceOwner!=null)voice=voiceOwner;
        logEvent("SESSION","activated="+source);
    }

    private int unused() { return revision.incrementAndGet(); }

    private void terminate(String reason) {
        if(!active())return;
        transport.sendEvent(GoogleCtsContract.EVENT_END,"","v2:"+reason,null);
        logEvent("SESSION","ended="+reason);
        token="";
        textMode=false;regionMode=false;currentText="";currentBounds=null;
        owner=null;voice=null;
        transport.reset();
        gestures.reset();
        viewport.reset();
        nativeChrome.reset();
        revision.incrementAndGet();
    }

    private int hookVoiceShow() {
        int count=0;
        try {
            Class<?> cls=Class.forName("android.service.voice.VoiceInteractionSession");
            for(Executable e:HiddenApiBypass.getDeclaredMethods(cls)){
                if(!(e instanceof Method method))continue;
                if("doShow".equals(method.getName())
                        && method.getParameterCount()>=2
                        && method.getParameterTypes()[0]==Bundle.class){
                    module.hook(method).intercept(chain->{
                        Bundle args=(Bundle)chain.getArg(0);
                        if(provider.isActive()){
                            String key=GoogleCtsContract.isYFloatSession(args)
                                    ?args.getString(GoogleCtsContract.K_SESSION_TOKEN,"")
                                    :provider.googleCtsArmedToken(args);
                            if(!key.isBlank())activate(key,chain.getThisObject(),"voice_show");
                        }
                        return chain.proceed();
                    });
                    count++;
                } else if("doHide".equals(method.getName())
                        && method.getParameterCount()==0) {
                    module.hook(method).intercept(chain->{
                        Object result=chain.proceed();
                        if(active()&&(voice==null||voice==chain.getThisObject()))
                            terminate("voice_hide");
                        return result;
                    });
                    count++;
                }
            }
        }catch(Throwable t){module.log(Log.WARN,TAG,"voice marker unavailable",t);}
        return count;
    }

    private int hookVoiceFrame() {
        int count=0;
        for(String clsName:new String[]{"android.service.voice.VoiceInteractionSession",
                "android.service.voice.VoiceInteractionSession$MyCallbacks"}){
            try {
                Class<?> cls=Class.forName(clsName);
                for(Executable e:HiddenApiBypass.getDeclaredMethods(cls)){
                    if(!(e instanceof Method method)||method.getParameterCount()!=1
                            ||method.getParameterTypes()[0]!=Bitmap.class
                            ||(!method.getName().equals("onHandleScreenshot")
                            &&!method.getName().equals("handleScreenshot")))continue;
                    module.hook(method).intercept(chain->{
                        if(active()&&chain.getArg(0) instanceof Bitmap frame)
                            onOriginalFrame(frame);
                        return chain.proceed();
                    });
                    count++;
                }
            }catch(Throwable t){module.log(Log.INFO,TAG,
                    "optional voice screenshot callback unavailable "+clsName);}
        }
        return count;
    }

    private int hookActivities() {
        int count=0;
        try {
            for(Executable e:HiddenApiBypass.getDeclaredMethods(Instrumentation.class)){
                if(!(e instanceof Method method))continue;
                if(!"callActivityOnCreate".equals(method.getName())
                        &&!"callActivityOnNewIntent".equals(method.getName()))continue;
                module.hook(method).intercept(chain->{
                    if(!(chain.getArg(0) instanceof Activity activity))
                        return chain.proceed();
                    Intent incoming=activity.getIntent();
                    if("callActivityOnNewIntent".equals(method.getName())){
                        Class<?>[] args=method.getParameterTypes();
                        for(int i=1;i<args.length;i++) {
                            if(Intent.class.isAssignableFrom(args[i])
                                    &&chain.getArg(i) instanceof Intent intent){
                                incoming=intent;break;
                            }
                        }
                    }
                    Bundle extras=incoming==null?null:incoming.getExtras();
                    if(provider.isActive()){
                        String key=GoogleCtsContract.isYFloatSession(extras)
                                ?extras.getString(GoogleCtsContract.K_SESSION_TOKEN,"")
                                :provider.googleCtsArmedToken(extras);
                        if(!key.isBlank())activate(key,null,"activity");
                    }
                    if(active()){
                        if(activity.getClass().getName().endsWith("LensientActivity")
                                ||owner==null)owner=activity;
                        frames.captureFromIntent(incoming);
                        // This Activity is a search handoff, not the initial
                        // LensientActivity. Keep Google on the owned selection.
                        if("callActivityOnNewIntent".equals(method.getName())
                                && (textMode||regionMode) && incoming!=null
                                && GoogleV2ScenePolicy.suppressNativeSearch(
                                        true,incoming.getAction())) {
                            logEvent("NATIVE_SEARCH_BLOCKED","source=new_intent");
                            return null;
                        }
                    }
                    Object result=chain.proceed();
                    if(active()&&owner==activity){
                        viewport.attach(activity);
                        nativeChrome.attach(activity);
                        int current=revision.get();
                        for(long wait:new long[]{160L,470L,950L}){
                            main.postDelayed(()->{
                                if(active()&&revision.get()==current&&owner==activity){
                                    viewport.attach(activity);
                                    nativeChrome.attach(activity);
                                    frames.probeFrozenView(activity,"v2_activity_"+wait);
                                }
                            },wait);
                        }
                    }
                    return result;
                });
                count++;
            }
        }catch(Throwable t){module.log(Log.WARN,TAG,"activity tracking unavailable",t);}
        return count;
    }

    private int hookSelection() {
        if(!selection.available()) {
            logEvent("CAPABILITY","selection unavailable "+selection.detail());return 0;
        }
        try {
            module.hook(selection.method()).intercept(chain->{
                if(!active()&&!loggedInactiveSelection){
                    loggedInactiveSelection=true;
                    module.log(Log.WARN,TAG,"selection Hook reached without active YFloat session;"
                            +" provider="+provider.isActive()
                            +" tokenPresent="+!token.isBlank());
                }
                if(active()){
                    loggedInactiveSelection=false;
                    try{
                        GoogleSelectionAdapter.Snapshot candidate=
                                selection.snapshot(chain.getArg(0),context());
                        Rect bounds=candidate.bounds();
                        if(candidate.directRegionCommit()&&bounds!=null&&!bounds.isEmpty()){
                            regionMode=true;textMode=false;
                            currentBounds=new Rect(bounds);
                            viewport.attach(owner);
                            nativeChrome.onSelection();
                            transport.sendEvent(GoogleCtsContract.EVENT_REGION_SELECTION,
                                    "",candidate.detail(),bounds);
                        } else {
                            String value=candidate.text();
                            if(value.isBlank())value=words.recentText();
                            Rect fromWords=words.recentBounds();
                            if(fromWords!=null&&!fromWords.isEmpty())bounds=fromWords;
                            if(!value.isBlank()){
                                textMode=true;regionMode=false;currentText=value;
                                if(bounds!=null&&!bounds.isEmpty())currentBounds=new Rect(bounds);
                                viewport.attach(owner);
                                nativeChrome.onSelection();
                                transport.sendEvent(GoogleCtsContract.EVENT_SELECTION,
                                        value,candidate.detail(),currentBounds);
                            }
                        }
                    }catch(Throwable t){logEvent("SELECTION_ERROR",t.getClass().getSimpleName());}
                }
                return chain.proceed();
            });
            return 1;
        } catch(Throwable t) {module.log(Log.WARN,TAG,"V2 selection binding failed",t);return 0;}
    }

    private void onWords(Rect bounds,String wordsText,String detail){
        if(!active())return;
        if(wordsText!=null&&!wordsText.isBlank()){
            textMode=true;regionMode=false;currentText=wordsText;
            if(bounds!=null&&!bounds.isEmpty())currentBounds=new Rect(bounds);
            viewport.attach(owner);
            nativeChrome.onSelection();
            transport.sendEvent(GoogleCtsContract.EVENT_SELECTION,wordsText,detail,currentBounds);
        }else if(textMode&&bounds!=null&&!bounds.isEmpty()){
            currentBounds=new Rect(bounds);
            transport.sendEvent(GoogleCtsContract.EVENT_TEXT_BOUNDS,"",detail,bounds);
        }
    }

    private void onGesture(Boolean adjusting,String detail){
        if(!active())return;
        if(regionMode)transport.sendEvent(adjusting
                        ?GoogleCtsContract.EVENT_REGION_GESTURE_START
                        :GoogleCtsContract.EVENT_REGION_GESTURE_END,
                "",detail,currentBounds);
        else if(textMode)transport.sendEvent(adjusting
                        ?GoogleCtsContract.EVENT_TEXT_GESTURE_START
                        :GoogleCtsContract.EVENT_TEXT_GESTURE_END,
                "",detail,currentBounds);
    }

    private void onOriginalFrame(Bitmap frame){
        if(active()&&frame!=null&&!frame.isRecycled()) {
            if(transport.sendFrame(frame))logEvent("FRAME",
                    "original="+frame.getWidth()+"x"+frame.getHeight());
        }
    }
    private void logEvent(String event,String detail){
        module.log(Log.INFO,TAG,event+" "+detail);
        transport.sendTrace("V2 "+event+" "+detail);
    }
    private Context context(){
        Activity activity=owner;
        if(activity!=null)return activity.getApplicationContext();
        try{
            Class<?> thread=Class.forName("android.app.ActivityThread");
            Object app=HiddenApiBypass.invoke(thread,null,"currentApplication");
            return app instanceof Context c?c.getApplicationContext():null;
        }catch(Throwable ignored){return null;}
    }
}
