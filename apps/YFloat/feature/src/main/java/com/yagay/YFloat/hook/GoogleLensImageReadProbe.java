package com.yagay.YFloat.hook;

import android.content.ContentResolver;
import android.net.Uri;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import io.github.libxposed.api.XposedModule;

/**
 * Diagnostic-only: confirms whether Google's process actually opens YFloat's
 * temporary screenshot URI. This never reads, changes or copies the image.
 *
 * Google may use binder/native I/O that bypasses these Android Java methods.
 * Absence of a callback is NOT proof the provider was never accessed.
 */
final class GoogleLensImageReadProbe {
    private final XposedModule module;
    private final BooleanSupplier session;
    private final BiConsumer<String,String> log;

    GoogleLensImageReadProbe(XposedModule module,BooleanSupplier session,
                             BiConsumer<String,String> log) {
        this.module=module;
        this.session=session;
        this.log=log;
    }

    int install() {
        int installed=0;
        try {
            for(Executable e:HiddenApiBypass.getDeclaredMethods(ContentResolver.class)){
                if(!(e instanceof Method method))continue;
                String name=method.getName();
                if(!"openInputStream".equals(name)
                        &&!"openFileDescriptor".equals(name)
                        &&!"openAssetFileDescriptor".equals(name)
                        &&!"openTypedAssetFileDescriptor".equals(name))continue;
                Class<?>[] args=method.getParameterTypes();
                if(args.length<1||args[0]!=Uri.class)continue;
                module.hook(method).intercept(chain->{
                    if(!session.getAsBoolean())return chain.proceed();
                    Object arg=chain.getArg(0);
                    if(!(arg instanceof Uri uri)||!isYFloatImage(uri))
                        return chain.proceed();
                    log.accept("LENS_IMAGE_INPUT","google_image_open_attempt="+name
                            +" authority="+uri.getAuthority());
                    try {
                        Object result=chain.proceed();
                        log.accept("LENS_IMAGE_INPUT","google_image_open_result="+name
                                +" success="+(result!=null));
                        return result;
                    }catch(Throwable error) {
                        log.accept("LENS_IMAGE_INPUT","google_image_open_failed="+name
                                +" type="+error.getClass().getSimpleName());
                        throw error;
                    }
                });
                installed++;
            }
        }catch(Throwable t) {
            log.accept("LENS_IMAGE_INPUT","image_read_probe_unavailable="
                    +t.getClass().getSimpleName());
        }
        return installed;
    }

    private static boolean isYFloatImage(Uri uri) {
        String authority=uri.getAuthority();
        return "content".equals(uri.getScheme())
                &&("com.yagay.YSuite.files".equals(authority)
                ||"com.yagay.YFloat.files".equals(authority));
    }
}
