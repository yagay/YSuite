package com.yagay.YFloat;

import android.content.ClipData;
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
 * Only the explicit image Find action in this branch invokes this class.
 * The original Google Circle-to-Search trigger is completely unaffected.
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

    /** Takes ownership of the supplied crop and recycles it after encoding. */
    static void launch(Context context,Bitmap ownedImage) {
        if(context==null||ownedImage==null||ownedImage.isRecycled()){
            if(ownedImage!=null&&!ownedImage.isRecycled())ownedImage.recycle();
            return;
        }
        Context app=context.getApplicationContext();
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
            Intent launch=normal.resolveActivity(app.getPackageManager())!=null
                    ?normal:direct;
            if(launch.resolveActivity(app.getPackageManager())==null)
                throw new IllegalStateException("No supported Google Lens image activity");
            SHARES.put(token,share);
            app.startActivity(launch);
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "intent_dispatched contract="+(launch==normal?"googleapp":"google")
                    +" session="+shortToken(token)
                    +" image_bytes="+share.file.length()
                    +" awaiting_google_read_and_ocr=true");
            GoogleCtsSessionCoordinator.launchResult(app,token,true);
            MAIN.postDelayed(()->release(app,token,"retention_timeout"),RETENTION_MS);
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        |Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(GoogleCtsContract.K_TRIGGER,true)
                .putExtra(GoogleCtsContract.K_SESSION_TOKEN,token)
                .putExtra(GoogleCtsContract.K_INVOCATION_TIME,now)
                .putExtra(GoogleCtsContract.K_LENS_IMAGE_INPUT_PROBE,true);
        intent.setClipData(ClipData.newRawUri("YFloat Lens image",image));
        return intent;
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

    private static String shortToken(String token) {
        return token.substring(0,Math.min(8,token.length()));
    }
    private GoogleLensImageInputProbe(){}
}
