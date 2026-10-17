package com.yagay.YFloat.hook;

import android.app.Instrumentation;
import android.content.Intent;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;

import com.yagay.YFloat.GoogleCtsContract;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedModule;

/**
 * V2-only Google native search boundary. Never blocks standard web searches
 * or any unowned Google app session.
 */
final class GoogleV2SearchGuard {
    private static final String TAG = "YFloat-GoogleV2";
    private final XposedModule module;
    private final ClassLoader loader;
    private final BooleanSupplier ownedSession;
    private final BooleanSupplier ownedSelection;
    private final BiConsumer<String,String> log;

    GoogleV2SearchGuard(XposedModule module, ClassLoader loader,
                        BooleanSupplier ownedSession,BooleanSupplier ownedSelection,
                        BiConsumer<String,String> log) {
        this.module=module;this.loader=loader;
        this.ownedSession=ownedSession;
        this.ownedSelection=ownedSelection;this.log=log;
    }

    int install() {
        int installed=0;
        installed+=hookInstrumentation();
        installed+=hookVoiceLaunch();
        installed+=hook1760LensResult();
        return installed;
    }

    private boolean block(Intent intent, String source) {
        if(intent==null)return false;
        android.net.Uri data=intent.getData();
        if(!GoogleV2ScenePolicy.suppressNativeSearch(
                ownedSession.getAsBoolean(),intent.getAction(),
                data==null?null:data.getHost(),data==null?null:data.getPath()))return false;
        log.accept("NATIVE_SEARCH_BLOCKED", "source="+source+" action="+intent.getAction());
        return true;
    }

    // 17.60 dsxb.q(dukp) delivers native Lens query results. Do not
    // interrupt selection or PendingLensQuery; block only post-selection
    // search result presentation in an authenticated YFloat session.
    // Unknown obfuscated names are not guessed: Intent guards still apply.
    private int hook1760LensResult() {
        if(loader==null)return 0;
        try {
            Class<?> cls=Class.forName(GoogleLens1760Profile.CONTROLLER,false,loader);
            int count=0;
            for(Executable executable:HiddenApiBypass.getDeclaredMethods(cls)) {
                if(!(executable instanceof Method method)
                        ||!GoogleLens1760Profile.isQueryResultMethod(method))continue;
                module.hook(method).intercept(chain->{
                    if(ownedSelection.getAsBoolean()) {
                        log.accept("NATIVE_SEARCH_BLOCKED","source=1760_lens_query_result");
                        return null;
                    }
                    return chain.proceed();
                });
                count++;
            }
            log.accept("NATIVE_SEARCH_GUARD","1760 query-result hooks="+count);
            return count;
        }catch(Throwable t){
            module.log(Log.INFO,TAG,"1760 query-result unavailable; intent guard retained");
            return 0;
        }
    }

    private int hookVoiceLaunch() {
        int count=0;
        try {
            Class<?> voice=Class.forName("android.service.voice.VoiceInteractionSession");
            for(Executable e:HiddenApiBypass.getDeclaredMethods(voice)) {
                if(!(e instanceof Method method))continue;
                if(!"startAssistantActivity".equals(method.getName())
                        &&!"startVoiceActivity".equals(method.getName()))continue;
                Class<?>[] args=method.getParameterTypes();
                if(args.length<1||args[0]!=Intent.class
                        ||method.getReturnType()!=void.class)continue;
                module.hook(method).intercept(chain->{
                    Intent intent=chain.getArg(0) instanceof Intent i?i:null;
                    if(block(intent,"voice_"+method.getName()))return null;
                    return chain.proceed();
                });
                count++;
            }
        }catch(Throwable t){module.log(Log.WARN,TAG,"V2 voice search guard unavailable",t);}
        return count;
    }

    private int hookInstrumentation() {
        int count=0;
        try {
            for(Executable e:HiddenApiBypass.getDeclaredMethods(Instrumentation.class)) {
                if(!(e instanceof Method method)
                        ||!"execStartActivity".equals(method.getName()))continue;
                Class<?>[] types=method.getParameterTypes();
                int index=-1;
                for(int i=0;i<types.length;i++)
                    if(types[i]==Intent.class){index=i;break;}
                if(index<0)continue;
                final int arg=index;
                module.hook(method).intercept(chain->{
                    Intent intent=chain.getArg(arg) instanceof Intent i?i:null;
                    if(block(intent,"instrumentation_execStartActivity"))return null;
                    return chain.proceed();
                });
                count++;
            }
        }catch(Throwable t){
            module.log(Log.WARN,TAG,"V2 instrumentation search guard unavailable",t);
        }
        log.accept("NATIVE_SEARCH_GUARD","installed="+count+" instrumentation hooks");
        return count;
    }
}
