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
import com.yagay.YFloat.GoogleLensShareEventPolicy;
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
    /** The lifecycle hooks must be live before slow obfuscated DEX discovery. */
    private volatile GoogleSelectionAdapter.Binding selection;
    private volatile boolean selectionResolverStarted;
    private final GoogleHookProfileStore profileStore;
    private final AtomicInteger revision = new AtomicInteger();
    private volatile boolean loggedInactiveSelection;
    private volatile boolean loggedRejectedActivation;
    private volatile String token = "";
    private volatile String hookReadySentToken = "";
    private volatile Activity owner;
    private volatile Object voice;
    private volatile boolean textMode;
    private volatile boolean regionMode;
    /** Image-input experiment never suppresses Google's Lens search/UI. */
    private volatile boolean lensImageInputProbe;
    private volatile Rect currentBounds;
    private volatile int[] currentTextRows;
    private volatile String currentText = "";
    private volatile boolean selectionCallbackObserved;
    private volatile boolean liveWordsObserved;
    private volatile boolean textSelectionObserved;
    private volatile boolean ignoredRegionOcrLogged;
    /** A Lens image-share request must not become YFloat's full-screen region UI. */
    private volatile boolean ignoredProbeRegionLogged;
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
        viewport=new GoogleV2Viewport(module,
                ()->active()&&GoogleLensShareEventPolicy.mayProtectViewport(lensImageInputProbe),
                ()->textMode,this::logEvent);
        // Scope the Intent and chrome suppression to an authenticated YFloat
        // CTS session. A dynamic selection Hook can register but NEVER fire on
        // this Google release, leaving textMode=false and native Google search
        // active. Keep Lens result-query interception guarded by confirmed
        // selection to avoid blocking Google's OCR processing.
        nativeSearch=new GoogleV2SearchGuard(module,loader,
                ()->!lensImageInputProbe&&ownsNativeSearchBoundary(),
                ()->!lensImageInputProbe&&ownsNativeSearchBoundary()
                        &&(textMode||regionMode),this::logEvent);
        // Do not hide Google's text-selection chrome BEFORE recognition
        // completes. Search Intent guards still protect YFloat-owned sessions.
        nativeChrome=new GoogleV2SearchChrome(
                // Suppress default Google result/search surfaces as soon as
                // the authenticated YFloat session starts, not only after OCR.
                ()->!lensImageInputProbe&&ownsNativeSearchBoundary(),
                ()->!lensImageInputProbe&&active()
                        &&(textMode||regionMode),this::logEvent);
        profileStore=new GoogleHookProfileStore(module);
    }

    /**
     * Bootstrap must never be blocked by Google's changing semantic DEX symbols.
     * First install stable OS/Google view callbacks, THEN discover the optional
     * selection controller on a worker. The Hook remains capable of owning and
     * closing YFloat's session even if selection discovery fails.
     */
    boolean install() {
        int count=0;
        count+=hookVoiceShow();
        count+=hookVoiceFrame();
        count+=hookActivities();
        count+=new GoogleLensImageReadProbe(module,
                ()->active()&&lensImageInputProbe,this::logEvent).install();
        count+=viewport.install();
        count+=nativeSearch.install();
        count+=frames.install();
        count+=words.install();
        logEvent("LIVE_TEXT_PROFILE",words.installedSummary());
        count+=gestures.install();
        logEvent("BOOTSTRAP","installed="+count+" semanticSelection=pending");
        if(count>0)startSelectionResolver(count);
        return count>0;
    }

    private void startSelectionResolver(int baselineCount) {
        if(selectionResolverStarted)return;
        selectionResolverStarted=true;
        Thread worker=new Thread(()->{
            try {
                // PackageReady precedes ActivityThread.currentApplication on
                // several Google versions. Only the optional symbol resolver
                // waits for context, never the lifecycle or search guards.
                for(int attempt=0;attempt<25
                        &&!GoogleHookProfileStore.contextReady();attempt++){
                    Thread.sleep(200L);
                }
                GoogleSelectionAdapter.Binding resolved=
                        GoogleSelectionAdapter.resolve(loader,profileStore);
                main.post(()->installSelectionBinding(resolved,baselineCount));
            } catch(Throwable error){
                module.log(Log.WARN,TAG,
                        "Optional Google selection discovery failed",error);
                logEvent("CAPABILITY","semantic_selection=unavailable error="
                        +error.getClass().getSimpleName());
            }
        },"YFloat-Google-Selection-Resolver");
        worker.setDaemon(true);
        worker.start();
    }

    private void installSelectionBinding(GoogleSelectionAdapter.Binding resolved,
                                         int baselineCount) {
        selection=resolved;
        if(resolved==null||!resolved.available()){
            logEvent("CAPABILITY","semantic_selection=unavailable bootstrap_alive=true detail="
                    +(resolved==null?"null":resolved.detail()));
            if(resolved!=null && (GoogleSelectionAdapter.scanned(resolved)
                    ||profileStore.manualRequested()))
                profileStore.reportScan(false,baselineCount,resolved.detail());
            return;
        }
        profileStore.reportProgress("registering",0,0);
        int selectionHooks=hookSelection();
        if(selectionHooks>0)GoogleSelectionAdapter.recordInstalled(resolved,profileStore);
        if(GoogleSelectionAdapter.scanned(resolved)||profileStore.manualRequested()){
            String method=resolved.method()==null?"no selection method"
                    :resolved.method().getDeclaringClass().getSimpleName()
                        +"."+resolved.method().getName();
            profileStore.reportScan(selectionHooks>0,baselineCount+selectionHooks,
                    "selection="+method+" source="+resolved.source()
                            +" status=installed_not_runtime_verified");
        }
        logEvent("CAPABILITY","semantic_selection_hooks="+selectionHooks
                +" bootstrap_alive=true source="+resolved.source()
                +" confidence="+resolved.confidence());
    }

    private boolean active() {
        String key=token;
        return provider.isActive()&&!key.isBlank()&&provider.ownsGoogleCtsSession(key);
    }

    /** A remote lease can be revoked while Google's own Lens Activity is still
     * alive. Keep the native-search exit guarded until that exact owner is
     * actually finished (at most the 300ms lease-watch interval).
     * Never guard unrelated Google searches after local session cleanup. */
    private boolean ownsNativeSearchBoundary() {
        String key=token;
        if(key.isBlank()||!provider.isActive())return false;
        if(provider.ownsGoogleCtsSession(key))return true;
        Activity tracked=owner;
        return (tracked!=null&&!tracked.isFinishing()&&!tracked.isDestroyed()
                &&tracked.getClass().getName().endsWith("LensientActivity"))
                ||voice instanceof VoiceInteractionSession;
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
            textMode=false;regionMode=false;lensImageInputProbe=false;
            currentText="";currentBounds=null;currentTextRows=null;
            selectionCallbackObserved=false;
            liveWordsObserved=false;
            textSelectionObserved=false;
            ignoredRegionOcrLogged=false;
            ignoredProbeRegionLogged=false;
            emptySelectionTraces=0;
        }
        boolean newSession=!key.equals(token);
        token=key;
        if(voiceOwner!=null)voice=voiceOwner;
        logEvent("SESSION","activated="+source);
        // Do not acknowledge on DEX scan alone: Google reached our live
        // LSPosed activation callback with the exact Remote Pref token.
        // Retry on Activity entry if Application Context was not ready in doShow.
        if(!key.equals(hookReadySentToken)
                &&transport.sendEvent(GoogleCtsContract.EVENT_HOOK_READY,
                        "","owned_callback="+source,null)) {
            hookReadySentToken=key;
        }
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
                                    +" method="+(selection==null||selection.method()==null?"pending"
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

    /**
     * Do not discard the tracked LensientActivity as soon as Android pauses
     * it for YFloat's screenshot result dialog. That used to cancel the
     * 300-ms lease watchdog, leaving Google's old frozen screenshot active
     * behind the dialog. Once the result revokes this token, finish the exact
     * Google voice session and Lens Activity. Real Back/Hide still cancels
     * the session if no screenshot handoff occurred.
     */
    private void scheduleOwnedExit(String expected,String reason) {
        // Visual teardown is immediate. Keep the existing delayed, token-bound
        // final release so lifecycle races cannot close a replacement session.
        if(expected!=null && expected.equals(token) && active())
            transport.sendEvent(GoogleCtsContract.EVENT_VISUAL_EXIT,
                    "",reason,null);
        int generation=revision.get();
        main.postDelayed(()->{
            if(!expected.equals(token)||generation!=revision.get())return;
            if(!provider.ownsGoogleCtsSession(expected)) {
                closeOwnedGoogleSession(expected,reason+"_lease_revoked");
            } else {
                terminate(reason);
            }
        },700L);
    }

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
        hookReadySentToken="";
        textMode=false;regionMode=false;lensImageInputProbe=false;
        currentText="";currentBounds=null;currentTextRows=null;
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
                            scheduleOwnedExit(token,"voice_hide");
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
                        &&!"callActivityOnResume".equals(method.getName())
                        &&!"callActivityOnStop".equals(method.getName()))continue;
                module.hook(method).intercept(chain->{
                    if(!(chain.getArg(0) instanceof Activity activity))
                        return chain.proceed();
                    if("callActivityOnResume".equals(method.getName())){
                        Object resumed=chain.proceed();
                        closeRevokedLensOnResume(activity);
                        return resumed;
                    }
                    if("callActivityOnStop".equals(method.getName())){
                        Object stopped=chain.proceed();
                        // A screenshot dialog backgrounds Lens without ending
                        // the selection synchronously. Preserve the owner for
                        // token-scoped remote release instead of dropping it now.
                        if(owner==activity&&active()&&(textMode||regionMode))
                            scheduleOwnedExit(token,"activity_stopped");
                        return stopped;
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
                        if(!key.isBlank()) {
                            activate(key,null,"activity");
                            // Explicit marker survives direct Google Lens image
                            // launch. It is not inferred from a normal Google
                            // search or an unrelated image-sharing Intent.
                            if(key.equals(token)&&extras!=null
                                    &&extras.getBoolean(
                                            GoogleCtsContract.K_LENS_IMAGE_INPUT_PROBE,false)){
                                if(!lensImageInputProbe)logEvent("LENS_IMAGE_INPUT",
                                        "marked_activity="+activity.getClass().getName());
                                lensImageInputProbe=true;
                            }
                        }
                    }
                    if(active()){
                        String activityName=activity.getClass().getName();
                        if(lensImageInputProbe) {
                            // Observe the *actual* Lens processing Activity,
                            // not only the GoogleAppGatewayActivity redirect.
                            android.net.Uri data=incoming==null?null:incoming.getData();
                            logEvent("LENS_IMAGE_INPUT","google_activity="+activityName
                                    +" event="+method.getName()
                                    +" scheme="+(data==null?"none":data.getScheme()));
                            if(activityName.toLowerCase(java.util.Locale.ROOT)
                                    .contains("lens")
                                    &&!activityName.endsWith("GoogleAppGatewayActivity"))
                                owner=activity;
                        }
                        if(activityName.endsWith("LensientActivity")
                                ||owner==null)owner=activity;
                        frames.captureFromIntent(incoming);
                        // This Activity is a search handoff, not the initial
                        // LensientActivity. Keep Google on the owned selection.
                        if("callActivityOnNewIntent".equals(method.getName())
                                && !lensImageInputProbe
                                && (textMode||regionMode) && incoming!=null
                                && GoogleV2ScenePolicy.suppressNativeSearch(
                                        true,incoming.getAction())) {
                            logEvent("NATIVE_SEARCH_BLOCKED","source=new_intent");
                            return null;
                        }
                    }
                    Object result=chain.proceed();
                    if(active()&&owner==activity){
                        if(!lensImageInputProbe){
                            viewport.attach(activity);
                            nativeChrome.attach(activity);
                        }
                        int current=revision.get();
                        for(long wait:new long[]{160L,470L,950L}){
                            main.postDelayed(()->{
                                if(active()&&revision.get()==current&&owner==activity){
                                    if(!lensImageInputProbe){
                                        viewport.attach(activity);
                                        nativeChrome.attach(activity);
                                    }
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
            // On ACTION_SEND Lens operates on a crop, not the whole screen.
            // Its image-space rectangle is NOT a screen-space screenshot region.
            // Let Google keep its native selection UI; YFloat only observes
            // selected OCR text from this experiment.
            if(!GoogleLensShareEventPolicy.mayForwardRegion(lensImageInputProbe)){
                if(!ignoredProbeRegionLogged){
                    ignoredProbeRegionLogged=true;
                    logEvent("LENS_IMAGE_INPUT",
                            "native_region_not_forwarded bounds="+bounds
                                    +" reason=share_image_coordinates");
                }
                return;
            }
            if(regionMode&&bounds.equals(currentBounds))return;
            regionMode=true;textMode=false;
            currentTextRows=null;
            currentBounds=new Rect(bounds);
            if(!lensImageInputProbe){
                viewport.attach(owner);
                nativeChrome.onSelection();
            }
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
                if(bounds!=null&&!bounds.isEmpty()){
                    if(currentBounds==null||!currentBounds.equals(bounds))
                        currentTextRows=null;
                    currentBounds=new Rect(bounds);
                }
                if(!changed)return;
                logEvent("TEXT_SELECTION_READY","stage="+stage
                        +" chars="+value.length()
                        +" bounds="+(currentBounds!=null&&!currentBounds.isEmpty()));
                if(!lensImageInputProbe){
                    viewport.attach(owner);
                    nativeChrome.onSelection();
                }
                transport.sendEventWithRows(GoogleCtsContract.EVENT_SELECTION,
                        value,candidate.detail()+" stage="+stage,
                        currentBounds,currentTextRows);
            }
        }
    }

    private void onWords(Rect bounds,String wordsText,String detail,int[] rows){
        if(!active())return;
        if(regionMode&&!lensImageInputProbe){
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
            currentTextRows=rows==null?null:rows.clone();
            if(!lensImageInputProbe){
                viewport.attach(owner);
                nativeChrome.onSelection();
            }
            transport.sendEventWithRows(GoogleCtsContract.EVENT_SELECTION,
                    wordsText,detail,currentBounds,currentTextRows);
        }else if(textMode&&bounds!=null&&!bounds.isEmpty()){
            currentBounds=new Rect(bounds);
            currentTextRows=rows==null?null:rows.clone();
            transport.sendEventWithRows(GoogleCtsContract.EVENT_TEXT_BOUNDS,
                    "",detail,bounds,currentTextRows);
        }
    }

    private void onGesture(Boolean adjusting,String detail){
        if(!active())return;
        if(lensImageInputProbe){
            // Keep Google's own gesture/touch hierarchy on image-share
            // experiments; YFloat has no screen region to confirm.
            if(textMode)words.onSelectionGesture(adjusting);
            return;
        }
        if(regionMode)transport.sendEvent(adjusting
                        ?GoogleCtsContract.EVENT_REGION_GESTURE_START
                        :GoogleCtsContract.EVENT_REGION_GESTURE_END,
                "",detail,currentBounds);
        else if(textMode){
            words.onSelectionGesture(adjusting);
            transport.sendEvent(adjusting
                            ?GoogleCtsContract.EVENT_TEXT_GESTURE_START
                            :GoogleCtsContract.EVENT_TEXT_GESTURE_END,
                    "",detail,currentBounds);
        }
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
