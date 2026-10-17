package com.yagay.YFloat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Image input contract used by Chromium's Lens Android integration.
 *
 * This is an INPUT contract only. A successful ACTION_VIEW does NOT imply
 * that Google returns recognized text or guarantees an OCR-only mode.
 */
final class GoogleLensImageInputContract {
    static final String INPUT_KEY = "LensBitmapUriKey";
    static final String GOOGLEAPP_SCHEME = "googleapp://lens";
    static final String DIRECT_SCHEME = "google://lens";

    /**
     * An explicit setComponent() target may have Intent.getPackage()==null.
     * Either routing form is valid only if every supplied destination agrees
     * on the real Google package. Never trust a foreign ComponentName.
     */
    static boolean isGoogleTarget(String declaredPackage,String componentPackage,
                                  String googlePackage) {
        if(googlePackage==null||googlePackage.isBlank())return false;
        if(declaredPackage!=null&&!googlePackage.equals(declaredPackage))return false;
        if(componentPackage!=null&&!googlePackage.equals(componentPackage))return false;
        return declaredPackage!=null||componentPackage!=null;
    }

    static boolean isHostImageUri(String value, String hostPackage) {
        if(value==null||hostPackage==null||hostPackage.isBlank())return false;
        try {
            URI uri=URI.create(value);
            return "content".equalsIgnoreCase(uri.getScheme())
                    && (hostPackage+".files").equals(uri.getAuthority())
                    && uri.getRawPath()!=null && uri.getRawPath().length()>1
                    && uri.getRawFragment()==null && uri.getRawUserInfo()==null;
        }catch(IllegalArgumentException e){return false;}
    }

    static String buildLensUri(String imageUri,long launchNanos,boolean direct) {
        if(imageUri==null||imageUri.isBlank()||launchNanos<=0)
            throw new IllegalArgumentException("image URI and elapsed timestamp required");
        String root=direct?DIRECT_SCHEME:GOOGLEAPP_SCHEME;
        return root+"?"+INPUT_KEY+"="+encode(imageUri)
                +"&AccountNameUriKey="
                +"&IncognitoUriKey=false"
                +"&ActivityLaunchTimestampNanos="+launchNanos;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s,StandardCharsets.UTF_8);
    }
    private GoogleLensImageInputContract(){}
}
