package com.yagay.YFloat;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EXPERIMENTAL path: YFloat's own frozen-window crop -> Google Lens image
 * input. No private Google model is invoked. Results are *observed* by the
 * already-existing LSPosed live-selection bridge, not promised by ACTION_VIEW.
 *
 * Only the explicit Google OCR experiment actions in native image selection
 * and the screenshot result dialog invoke this class. The original Google
 * Circle-to-Search trigger and generic Find action remain unaffected.
 */
final class GoogleLensImageInputProbe {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long RETENTION_MS=120_000L;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"YFloat-LensImageInput");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String,ImageShareUtils.LensShare> SHARES=
            new ConcurrentHashMap<>();
    private static final Map<String,NativeResult> RESULTS=
            new ConcurrentHashMap<>();

    /** In-memory, exact-session pairing of Google's actual OCR with YFloat's own crop. */
    static final class NativeResult {
        final String sessionToken;
        final String selectedText;
        final Rect selectedBounds;
        final Uri originalInputImage;
        NativeResult(String sessionToken,String text,Rect bounds,Uri image){
            this.sessionToken=sessionToken;
            this.selectedText=text;
            this.selectedBounds=bounds==null?null:new Rect(bounds);
            this.originalInputImage=image;
        }
    }

    static NativeResult result(String token) {
        return RESULTS.get(token);
    }

    static boolean ownsPendingImage(String token) {
        return token!=null&&SHARES.containsKey(token);
    }

    /** Takes ownership of the supplied crop and recycles it after encoding. */
    static void launch(Context context,Bitmap ownedImage) {
        if(context==null||ownedImage==null||ownedImage.isRecycled()){
            if(ownedImage!=null&&!ownedImage.isRecycled())ownedImage.recycle();
            return;
        }
        Context app=context.getApplicationContext();
        DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                "encode_begin bitmap="+ownedImage.getWidth()+"x"+ownedImage.getHeight());
        IO.execute(()->{
            ImageShareUtils.LensShare share=null;
            try{
                share=ImageShareUtils.prepareGoogleLensImage(app,ownedImage);
                ImageShareUtils.LensShare ready=share;
                MAIN.post(()->start(app,ready));
            }catch(Throwable error){
                if(share!=null)share.close(app);
                DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                        "encode_failed="+error.getClass().getSimpleName());
                MAIN.post(()->Toast.makeText(app,
                        "Google Lens image preparation failed",Toast.LENGTH_SHORT).show());
            }finally{
                if(!ownedImage.isRecycled())ownedImage.recycle();
            }
        });
    }

    private static void start(Context app,ImageShareUtils.LensShare share) {
        Uri imageUri=share.uri;
        if(!GoogleLensImageInputContract.isHostImageUri(
                imageUri.toString(),app.getPackageName())){
            share.close(app);
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT","reject_not_host_file_provider");
            return;
        }
        String token=UUID.randomUUID().toString();
        long now=SystemClock.elapsedRealtime();
        for(String old:SHARES.keySet())release(app,old,"new_image_input");
        GoogleCtsSessionCoordinator.cancelCurrent(app,"new_lens_image_input");
        WorkflowSessionManager.Session workflow=
                GoogleCtsSessionCoordinator.begin(app,token,now);
        if(workflow==null){share.close(app);return;}
        try{
            app.grantUriPermission(GoogleCtsContract.GOOGLE_PACKAGE,imageUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            long nanos=SystemClock.elapsedRealtimeNanos();
            Intent normal=makeIntent(imageUri,token,now,nanos,false);
            Intent direct=makeIntent(imageUri,token,now,nanos,true);
            ComponentName gateway=normal.resolveActivity(app.getPackageManager());
            ComponentName directReceiver=direct.resolveActivity(app.getPackageManager());
            // Google 17.66.12 APK declares this exported ACTION_SEND receiver
            // with image/png and image/jpeg filters. Its onCreate checks
            // ACTION_SEND + image/* + EXTRA_STREAM, unlike the URI gateway.
            ComponentName shareReceiver=new ComponentName(
                    GoogleCtsContract.GOOGLE_PACKAGE,
                    "com.google.android.apps.search.lens.LensShareEntryPointActivity");
            boolean shareAvailable=false;
            try {
                android.content.pm.ActivityInfo info=app.getPackageManager()
                        .getActivityInfo(shareReceiver,0);
                shareAvailable=info!=null&&info.exported
                        &&GoogleCtsContract.GOOGLE_PACKAGE.equals(info.packageName);
            }catch(android.content.pm.PackageManager.NameNotFoundException ignored){
                // Only the actual Google 17.66 package declares this route;
                // fallback to the original gateway on other versions.
            }
            if(!shareAvailable&&gateway==null&&directReceiver==null)
                throw new IllegalStateException("No supported Google Lens image activity");
            Intent launch=shareAvailable
                    ?makeSharedImageIntent(imageUri,token,now,shareReceiver)
                    :(gateway!=null?normal:direct);
            String contract=shareAvailable?"apk_verified_image_share"
                    :(launch==direct?"direct_fallback":"gateway_default");
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "resolvers share="+shareAvailable
                    +" gateway="+(gateway==null?"none":gateway.getClassName())
                    +" direct="+(directReceiver==null?"none":directReceiver.getClassName())
                    +" chosen="+contract);
            SHARES.put(token,share);
            MAIN.postDelayed(()->release(app,token,"retention_timeout"),RETENTION_MS);
            Intent host=new Intent(app,GoogleLensImageLaunchActivity.class)
                    .putExtra(GoogleLensImageLaunchActivity.EXTRA_SESSION,token)
                    .putExtra(GoogleLensImageLaunchActivity.EXTRA_LENS_INTENT,launch)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(host);
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "caller_started session="+shortToken(token)
                    +" via=YFloatActivity startActivityForResult_pending"
                    +" image_bytes="+share.file.length());
        }catch(Throwable error){
            release(app,token,"launch_failed");
            share.close(app);
            GoogleCtsSessionCoordinator.end(app,token,"lens_input_launch_failed",false);
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "launch_failed="+error.getClass().getSimpleName());
            Toast.makeText(app,"Google Lens image input unavailable",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static Intent makeIntent(Uri image,String token,long now,long nanos,
                                     boolean direct) {
        Uri lens=Uri.parse(GoogleLensImageInputContract.buildLensUri(
                image.toString(),nanos,direct));
        Intent intent=new Intent(Intent.ACTION_VIEW,lens)
                .setPackage(GoogleCtsContract.GOOGLE_PACKAGE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(GoogleCtsContract.K_TRIGGER,true)
                .putExtra(GoogleCtsContract.K_SESSION_TOKEN,token)
                .putExtra(GoogleCtsContract.K_INVOCATION_TIME,now)
                .putExtra(GoogleCtsContract.K_LENS_IMAGE_INPUT_PROBE,true);
        intent.setClipData(ClipData.newRawUri("YFloat Lens image",image));
        return intent;
    }

    private static Intent makeSharedImageIntent(Uri image,String token,long now,
                                                ComponentName receiver) {
        Intent intent=new Intent(Intent.ACTION_SEND)
                .setComponent(receiver)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM,image)
                .putExtra(GoogleCtsContract.K_TRIGGER,true)
                .putExtra(GoogleCtsContract.K_SESSION_TOKEN,token)
                .putExtra(GoogleCtsContract.K_INVOCATION_TIME,now)
                .putExtra(GoogleCtsContract.K_LENS_IMAGE_INPUT_PROBE,true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("YFloat Lens image",image));
        return intent;
    }

    static void onLaunchDispatched(Context app,String token) {
        if(!SHARES.containsKey(token))return;
        DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                "intent_dispatched session="+shortToken(token)
                +" caller=startActivityForResult awaiting_image_read_and_ocr=true");
        GoogleCtsSessionCoordinator.launchResult(app,token,true);
    }

    static void onLaunchFailed(Context app,String token,String reason) {
        if(token!=null&&!token.isBlank()){
            release(app,token,"caller_launch_failed");
            GoogleCtsSessionCoordinator.end(app,token,"lens_image_launch_failed",false);
        }
        DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                "caller_launch_failed session="+shortToken(token)
                +" reason="+reason);
        MAIN.post(()->Toast.makeText(app,
                "Google Lens cannot accept this image",Toast.LENGTH_SHORT).show());
    }

    static void onCtsWorkflowEnd(Context app,String token,String reason) {
        // Navigation from the general Lens gateway is not proof the image was
        // read. Preserve the grant until TTL so later Google processes can read.
        if(SHARES.containsKey(token)
                &&("top_package_changed".equals(reason)
                  ||"system_navigation".equals(reason)
                  ||"activity_stopped".equals(reason))) {
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "workflow_ended_input_retained session="+shortToken(token)
                    +" reason="+reason+" ttlMs="+RETENTION_MS);
            return;
        }
        release(app,token,"workflow_"+reason);
    }

    static void onNativeText(Context app,String token,String text,Rect bounds){
        ImageShareUtils.LensShare share=SHARES.get(token);
        if(share==null||text==null||text.isBlank())return;
        RESULTS.put(token,new NativeResult(token,text,bounds,share.uri));
        DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                "native_ocr_callback session="+shortToken(token)
                +" chars="+text.length()+" bounds="+(bounds!=null)
                +" paired_with_host_image=true source=google_live_selection");
    }

    static void release(Context context,String token,String reason) {
        RESULTS.remove(token);
        ImageShareUtils.LensShare share=SHARES.remove(token);
        if(share==null)return;
        share.close(context.getApplicationContext());
        DiagnosticLog.i(context,"LENS_IMAGE_INPUT",
                "image_released reason="+reason+" session="+shortToken(token));
    }

    static String shortToken(String token) {
        if(token==null||token.isBlank())return "none";
        return token.substring(0,Math.min(8,token.length()));
    }
    private GoogleLensImageInputProbe(){}
}
