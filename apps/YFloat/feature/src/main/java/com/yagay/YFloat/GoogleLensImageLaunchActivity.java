package com.yagay.YFloat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Explicit, non-exported YFloat caller for Google Lens image-input probing.
 * Chromium invokes its legacy Google Lens handoff with startActivityForResult
 * to preserve the initiating app identity; a Context.startActivity launcher
 * does not supply an Activity result identity.
 *
 * YUiWindowOptOut: no app controls are rendered; this translucent Android
 * caller must not install a full-screen YUI page around a foreign Activity.
 *
 * This class never receives an OCR result through onActivityResult;
 * OCR results, if any, still require the Hook bridge.
 */
public final class GoogleLensImageLaunchActivity extends Activity {
    static final String EXTRA_LENS_INTENT="yfloat_lens_launch_intent";
    static final String EXTRA_SESSION="yfloat_lens_launch_token";
    private static final int LENS_REQUEST=2106;
    private boolean dispatched;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState!=null)return;
        Intent request=getIntent();
        String token=request==null?null:request.getStringExtra(EXTRA_SESSION);
        Intent google=request==null?null:request.getParcelableExtra(
                EXTRA_LENS_INTENT,Intent.class);
        if(token==null||token.isBlank()||google==null
                ||!GoogleCtsContract.GOOGLE_PACKAGE.equals(google.getPackage())) {
            GoogleLensImageInputProbe.onLaunchFailed(this,token,
                    "caller_invalid_request");
            finish();
            return;
        }
        try {
            // Do not re-add FLAG_ACTIVITY_NEW_TASK here: it defeats caller
            // identity and result semantics for this explicit handoff.
            google.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityForResult(google,LENS_REQUEST);
            dispatched=true;
            GoogleLensImageInputProbe.onLaunchDispatched(this,token);
        }catch(Throwable failure) {
            GoogleLensImageInputProbe.onLaunchFailed(this,token,
                    failure.getClass().getSimpleName());
            finish();
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==LENS_REQUEST) {
            String token=getIntent().getStringExtra(EXTRA_SESSION);
            DiagnosticLog.i(this,"LENS_IMAGE_INPUT",
                    "caller_result_received session="+GoogleLensImageInputProbe.shortToken(token)
                    +" resultCode="+result+" returnsOcr=false");
            finish();
        }
    }

    @Override protected void onDestroy(){
        if(!dispatched) {
            DiagnosticLog.i(this,"LENS_IMAGE_INPUT","caller_finished_without_launch");
        }
        super.onDestroy();
    }
}
