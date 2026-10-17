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
import android.service.voice.VoiceInteractionSession;
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
    /** Host result handoff revokes Remote Preferences without resuming Lens. */
    private static final long OWNED_LEASE_CHECK_MS = 300L;
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
    private volatile boolean selectionCallbackObserved;
    private volatile boolean liveWordsObserved;
    private volatile boolean textSelectionObserved;
    private volatile boolean ignoredRegionOcrLogged;
    private int emptySelectionTraces;

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
        // Scope the Intent and chrome suppression to an authenticated YFloat
        // CTS session. A dynamic selection Hook can register but NEVER fire on
        // this Google release, leaving textMode=false and native Google search
        // active. Keep Lens result-query interception guarded by confirmed
        // selection to avoid blocking Google's OCR processing.
        nativeSearch=new GoogleV2SearchGuard(module,loader,
                this::active,()->active()&&(textMode||regionMode),this::logEvent);
        // Do not hide Google's text-selection chrome BEFORE recognition
        // completes. Search Intent guards still protect YFloat-owned sessions.
        nativeChrome=new GoogleV2SearchChrome(
                ()->active()&&(textMode||regionMode),
                ()->active()&&(textMode||regionMode),this::logEvent);
        profileStore=new GoogleHookProfileStore(module);
        selection=GoogleSelectionAdapter.resolve(loader,profileStore);
    }

    boolean install() {
        if (selection.available()) profileStore.reportProgress("registering",0,0);
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
        logEvent("LIVE_TEXT_PROFILE",words.installedSummary());
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
                    "selection=" + method + " source=" + selection.source()
                            + " status=installed_not_runtime_verified");
        }
        module.log(Log.INFO,TAG,"V2 installed hooks="+count
                +" selection="+selection.source()
                +" confidence="+selection.confidence()
                +" compatibility="+(selection.available() ? "selection_bound" : "selection_unavailable")
                +" detail="+selection.detail()
                +" installedBinding="+(selectionHooks>0)
                +" runtimeSelectionVerified=false"
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
            words.reset();
            viewport.reset();
            nativeChrome.reset();
            revision.incrementAndGet();
            textMode=false;regionMode=false;currentText="";currentBounds=null;
            selectionCallbackObserved=false;
            liveWordsObserved=false;
            textSelectionObserved=false;
            ignoredRegionOcrLogged=false;
            emptySelectionTraces=0;
        }
        boolean newSession=!key.equals(token);
        token=key;
        if(voiceOwner!=null)voice=voiceOwner;
        logEvent("SESSION","activated="+source);
        if(newSession) {
            // The host revokes its lease once the YFloat result screen owns
            // the output. Google does not always call Activity.onResume again,
            // so a token-bound watchdog must close the original CTS surface.
            scheduleOwnedLeaseWatch(key,revision.get());
            main.postDelayed(()->{
                if(active()&&key.equals(token)
                        &&!textSelectionObserved && !regionMode) {
                    logEvent("TEXT_SELECTION_NOT_DELIVERED",
                            "after=8s methodReached="+selectionCallbackObserved
                                    +" liveWordsObserved="+liveWordsObserved
                                    +" method="+(selection.method()==null?"none"
                                    :selection.method().getDeclaringClass().getName()
                                            +"."+selection.method().getName())
                                    +" boundsOnlyDoesNotProveText=true");
                }
            },8000L);
        }
    }

    /**
     * Recheck the exact authenticated token, never an unscoped Google window.
     * This is a short, low-cost Remote Preferences read while a YFloat-owned
     * session is alive, not a DEX scan or a search interception guess.
     */
    private void scheduleOwnedLeaseWatch(String expected,int generation) {
        main.postDelayed(()->{
            if(!expected.equals(token)||revision.get()!=generation)return;
            if(!provider.ownsGoogleCtsSession(expected)){
                closeOwnedGoogleSession(expected,"lease_revoked");
                return;
            }
            scheduleOwnedLeaseWatch(expected,generation);
        },OWNED_LEASE_CHECK_MS);
    }

    private int unused() { return revision.incrementAndGet(); }

    private void terminate(String reason) {
        if(token.isBlank())return;
        // The Google voice session may hide AFTER YFloat revoked the lease.
        // Local cleanup must not depend on a now-invalid Remote Pref token.
        if(active()){
            transport.sendEvent(GoogleCtsContract.EVENT_END,"","v2:"+reason,null);
            logEvent("SESSION","ended="+reason);
        }else module.log(Log.INFO,TAG,"SESSION ended="+reason+" after_remote_revoke");
        clearLocalSession();
    }

    /**
     * Clear local Java state after a true Google doHide or after the
     * remotely revoked YFloat session is closed on resume.
     */
    private void clearLocalSession() {
        token="";
        textMode=false;regionMode=false;currentText="";currentBounds=null;
        owner=null;voice=null;
        transport.reset();
        gestures.reset();
        words.reset();
        viewport.reset();
        nativeChrome.reset();
        revision.incrementAndGet();
    }

    private void closeRevokedLensOnResume(Activity activity) {
        if(activity==null||owner!=activity)return;
        final String expected=token;
        if(expected.isBlank()
                ||!activity.getClass().getName().endsWith("LensientActivity"))
            return;
        main.post(()->{
            if(owner!=activity||!expected.equals(token))return;
            closeOwnedGoogleSession(expected,"activity_resumed");
        });
    }

    /**
     * Once YFloat owns the result, do not leave Google's old FrozenImageView
     * interactive. Finish the exact old VoiceInteractionSession and Activity.
     * A newly armed CTS token takes priority, because Android may reuse the
     * same voice process/activity when YFloat is triggered again quickly.
     */
    private void closeOwnedGoogleSession(String expected,String source) {
        if(expected==null||expected.isBlank()||!expected.equals(token)
                ||provider.ownsGoogleCtsSession(expected))return;
        String replacement=provider.googleCtsArmedToken();
        if(!GoogleV2ScenePolicy.shouldCloseRevokedOwnedSession(
                true,true,false,
                !replacement.isBlank()&&!expected.equals(replacement))){
            module.log(Log.INFO,TAG,
                    "OWNED_SESSION_SUPERSEDED source="+source
                            +" replacementArmed="+!replacement.isBlank());
            // Leave the Google-owned view in place for the replacement token,
            // but stop all old YFloat listeners/selection data.
            clearLocalSession();
            return;
        }
        Activity oldActivity=owner;
        Object oldVoice=voice;
        boolean lens=oldActivity!=null
                &&oldActivity.getClass().getName().endsWith("LensientActivity")
                &&!oldActivity.isFinishing()&&!oldActivity.isDestroyed();
        module.log(Log.INFO,TAG,"OWNED_SESSION_FINISH source="+source
                +" lens="+lens+" voice="+(oldVoice instanceof VoiceInteractionSession));
        // Prevent asynchronous old-session callbacks from being misinterpreted
        // as selection events for the next session.
        clearLocalSession();
        if(oldVoice instanceof VoiceInteractionSession oldSession){
            try{oldSession.finish();}
            catch(Throwable t){module.log(Log.WARN,TAG,
                    "owned voice finish failed",t);}
        }
        if(lens){
            try{oldActivity.finish();}
            catch(Throwable t){module.log(Log.WARN,TAG,
                    "owned LensientActivity finish failed",t);}
        }
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
                        &&!"callActivityOnNewIntent".equals(method.getName())
                        &&!"callActivityOnResume".equals(method.getName()))continue;
                module.hook(method).intercept(chain->{
                    if(!(chain.getArg(0) instanceof Activity activity))
                        return chain.proceed();
                    if("callActivityOnResume".equals(method.getName())){
                        Object resumed=chain.proceed();
                        closeRevokedLensOnResume(activity);
                        return resumed;
                    }
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

    /**
     * Google 17.66's obfuscated controller can receive metadata before it
     * populates the selected text. Observe both sides of the SAME method
     * invocation. Never short-circuit Google's own selection processing.
     */
    private int hookSelection() {
        if(!selection.available()) {
            logEvent("CAPABILITY","selection unavailable "+selection.detail());return 0;
        }
        try {
            module.hook(selection.method()).intercept(chain->{
                boolean owned=active();
                Object metadata=chain.getArg(0);
                if(owned&&!selectionCallbackObserved) {
                    selectionCallbackObserved=true;
                    logEvent("SELECTION_METHOD_REACHED",
                            "method="+selection.method().getDeclaringClass().getName()
                                    +"."+selection.method().getName()
                                    +" metadata="+(metadata==null?"null"
                                    :metadata.getClass().getName()));
                }
                if(!owned&&!loggedInactiveSelection){
                    loggedInactiveSelection=true;
                    module.log(Log.WARN,TAG,"selection Hook reached without active YFloat session;"
                            +" provider="+provider.isActive()
                            +" tokenPresent="+!token.isBlank());
                }
                GoogleSelectionAdapter.Snapshot pre=null;
                if(owned) {
                    loggedInactiveSelection=false;
                    pre=selectionSnapshot(metadata,"before");
                    publishSelection(pre,"before");
                }
                // Proceed EXACTLY once, even if the pre-state was empty.
                Object result=chain.proceed();
                if(owned&&active()){
                    GoogleSelectionAdapter.Snapshot post=selectionSnapshot(metadata,"after");
                    if(!equivalentSnapshots(pre,post))publishSelection(post,"after");
                }
                return result;
            });
            return 1;
        }catch(Throwable t){module.log(Log.WARN,TAG,"V2 selection binding failed",t);return 0;}
    }

    private GoogleSelectionAdapter.Snapshot selectionSnapshot(Object metadata,String stage) {
        try {
            GoogleSelectionAdapter.Snapshot candidate=selection.snapshot(metadata,context());
            if(emptySelectionTraces<8 &&
                    (candidate.text().isBlank() && !candidate.directRegionCommit())) {
                emptySelectionTraces++;
                Rect bounds=candidate.bounds();
                logEvent("SELECTION_SNAPSHOT_EMPTY",
                        "stage="+stage
                        +" selectionClass="+candidate.selectionClass()
                        +" bounds="+(bounds==null?"none":bounds)
                        +" metadata="+(metadata==null?"null":metadata.getClass().getName())
                        +" detail="+candidate.detail()
                        +(emptySelectionTraces<=3
                            ?" shape="+selection.diagnosticShape(metadata):""));
            }
            return candidate;
        }catch(Throwable t) {
            if(emptySelectionTraces++<8)
                logEvent("SELECTION_ERROR","stage="+stage+" type="
                        +t.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean equivalentSnapshots(GoogleSelectionAdapter.Snapshot a,
            GoogleSelectionAdapter.Snapshot b) {
        if(a==b)return true;
        if(a==null||b==null)return false;
        return a.directRegionCommit()==b.directRegionCommit()
                &&a.text().equals(b.text())
                &&java.util.Objects.equals(a.bounds(),b.bounds())
                &&a.selectionClass().equals(b.selectionClass());
    }

    private void publishSelection(GoogleSelectionAdapter.Snapshot candidate,String stage) {
        if(candidate==null||!active())return;
        Rect bounds=candidate.bounds();
        if(candidate.directRegionCommit()&&bounds!=null&&!bounds.isEmpty()){
            if(regionMode&&bounds.equals(currentBounds))return;
            regionMode=true;textMode=false;
            currentBounds=new Rect(bounds);
            viewport.attach(owner);
            nativeChrome.onSelection();
            transport.sendEvent(GoogleCtsContract.EVENT_REGION_SELECTION,
                    "",candidate.detail()+" stage="+stage,bounds);
        }else{
            // A late model/OCR callback must not turn a region draw into a
            // text search, even if old words remain briefly cached.
            if(regionMode)return;
            String value=candidate.text();
            if(value.isBlank())value=words.recentText();
            Rect fromWords=words.recentBounds();
            if(fromWords!=null&&!fromWords.isEmpty())bounds=fromWords;
            if(!value.isBlank()){
                boolean changed=!textMode||!value.equals(currentText)
                        ||(bounds!=null&&!bounds.equals(currentBounds));
                textMode=true;regionMode=false;currentText=value;
                textSelectionObserved=true;
                if(bounds!=null&&!bounds.isEmpty())currentBounds=new Rect(bounds);
                if(!changed)return;
                logEvent("TEXT_SELECTION_READY","stage="+stage
                        +" chars="+value.length()
                        +" bounds="+(currentBounds!=null&&!currentBounds.isEmpty()));
                viewport.attach(owner);
                nativeChrome.onSelection();
                transport.sendEvent(GoogleCtsContract.EVENT_SELECTION,
                        value,candidate.detail()+" stage="+stage,currentBounds);
            }
        }
    }

    private void onWords(Rect bounds,String wordsText,String detail){
        if(!active())return;
        if(regionMode){
            if(!ignoredRegionOcrLogged){
                ignoredRegionOcrLogged=true;
                logEvent("REGION_IGNORED_LATE_OCR",
                        "ignored text update after region ownership"
                                +" chars="+(wordsText==null?0:wordsText.length()));
            }
            return;
        }
        if(wordsText!=null&&!wordsText.isBlank())textSelectionObserved=true;
        if(!liveWordsObserved) {
            liveWordsObserved=true;
            logEvent("LIVE_TEXT_CALLBACK",
                    "chars="+(wordsText==null?0:wordsText.length())
                            +" bounds="+(bounds!=null&&!bounds.isEmpty()));
        }
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
