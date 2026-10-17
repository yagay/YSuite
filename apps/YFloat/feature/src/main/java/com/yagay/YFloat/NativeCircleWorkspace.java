package com.yagay.YFloat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intent-first frozen-screen Circle workspace.
 *
 * Nothing moves the screenshot: it is painted once in immutable frame
 * coordinates. Taps and text highlights hand a bounded crop to Google's
 * native Lens OCR; closed circles remain an editable local screenshot crop.
 * Screenshot actions never launch Google. The Google OCR action is explicitly
 * available on a closed-circle image selection.
 */
final class NativeCircleWorkspace extends View {
    private static NativeCircleWorkspace active;

    private final Context app;
    private final FLCircleCapture.Frame frame;
    private final FlOverlayWindowHost host;
    private final Runnable closedCallback;
    private final NativeCircleState state = new NativeCircleState();
    private final float density;
    private final Paint screenshotPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activationBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dragPath = new Path();
    private final ArrayList<PointF> stroke = new ArrayList<>();
    private final RectF closeHit = new RectF();
    private final RectF[] imageHandles = {
            new RectF(), new RectF(), new RectF(), new RectF()
    };
    private final RectF imageDragOrigin = new RectF();

    private boolean closed, touching;
    private int handleDrag;
    private float downX, downY, minX, minY, maxX, maxY;
    private String status = "";

    static synchronized boolean show(Context context, FLCircleCapture.Frame frame,
                                     Runnable onClosed) {
        if (context == null || frame == null || frame.bitmap == null
                || frame.bitmap.isRecycled()) return false;
        dismissActive("replace");
        // Menus are not workflow-bound; a previous text menu must not be reused
        // with stale selection callbacks by the new frozen workspace.
        FloatActionMenu.dismiss();
        Context app = context.getApplicationContext();
        Rect display = ScreenGeometry.displayBounds(app);
        Rect bounds = frame.screenBounds;
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                Math.max(1, bounds.width()), Math.max(1, bounds.height()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START;
        layout.x = bounds.left - display.left;
        layout.y = bounds.top - display.top;
        FlOverlayWindowHost host = new FlOverlayWindowHost(app);
        NativeCircleWorkspace view = new NativeCircleWorkspace(app, host, frame, onClosed);
        if (!host.addStable(view, layout, "native_circle_workspace")) return false;
        active = view;
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        DiagnosticLog.i(app,"CIRCLE_NATIVE",
                "ready intent-first image="+frame.bitmap.getWidth()+"x"
                        +frame.bitmap.getHeight()
                        +" textRecognition=google_lens_on_demand"
                        +" screenshots=local_only");
        return true;
    }

    static synchronized void dismissActive(String reason) {
        NativeCircleWorkspace previous = active;
        active = null;
        if (previous != null) previous.close(reason);
    }

    static synchronized void promoteFocus(String reason) {
        if (active != null && !active.closed) active.requestFocus();
    }

    private NativeCircleWorkspace(Context context, FlOverlayWindowHost host,
                                  FLCircleCapture.Frame frame, Runnable onClosed) {
        super(context);
        app = context;
        this.host = host;
        this.frame = frame;
        closedCallback = onClosed;
        density = getResources().getDisplayMetrics().density;
        shadePaint.setColor(Color.BLACK);
        activationPaint.setStyle(Paint.Style.STROKE);
        activationPaint.setStrokeWidth(2f * density);
        activationPaint.setColor(YFloatOverlayStyle.selectionColor(app));
        activationBadgePaint.setColor(Color.argb(237, 23, 31, 45));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2.4f * density);
        outlinePaint.setColor(YFloatOverlayStyle.selectionColor(app));
        fillPaint.setColor(Color.argb(76, 64, 145, 255));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        toolbarPaint.setColor(Color.argb(241, 31, 34, 42));
        setFocusable(true);
        setClickable(true);
    }

    private String t(String zh,String en) {
        return Locale.getDefault().getLanguage().equals("zh") ? zh : en;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (closed || frame.bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        canvas.drawBitmap(frame.bitmap, null, new Rect(0,0,w,h), screenshotPaint);
        // Mask ownership lives inside this single canvas, not in a second
        // screenshot layer. It clearly signals an active frozen session.
        int maskAlpha = NativeCircleVisualPolicy.baseScrimAlpha(state.phase);
        if (maskAlpha > 0) canvas.drawColor(Color.argb(maskAlpha, 0, 0, 0));

        if (state.phase == NativeCircleState.Phase.DRAWING) {
            canvas.drawPath(dragPath, outlinePaint);
        } else if (state.phase == NativeCircleState.Phase.IMAGE_READY) {
            drawImageRegion(canvas);
        } else if (state.phase == NativeCircleState.Phase.RESOLVING) {
            RectF box = selectedViewBox();
            if (!box.isEmpty()) {
                dimOutside(canvas, box, NativeCircleVisualPolicy.SELECTION_OUTSIDE_ALPHA);
                canvas.drawRoundRect(box, 10*density,10*density,outlinePaint);
            }
        }

        drawActiveIndicator(canvas);
        drawClose(canvas);
        // Commands are Android Views in FloatActionMenu, not text
        // painted onto the frozen screenshot. The overlay only draws the
        // selection, OCR highlights, handles and active-session indicator.
        if (state.phase == NativeCircleState.Phase.IDLE) {
            drawHint(canvas,t("圈画已开启 · 圈住图片或划选文字",
                    "Circle active · Draw around an image or text"));
        } else if (state.phase == NativeCircleState.Phase.RESOLVING || !status.isBlank()) {
            drawHint(canvas,status);
        }
    }

    private void drawActiveIndicator(Canvas canvas) {
        float margin=3f*density;
        float right=Math.max(margin+2f,getWidth()-margin);
        float bottom=Math.max(margin+2f,getHeight()-margin);
        canvas.drawRoundRect(new RectF(margin,margin,right,bottom),
                11f*density,11f*density,activationPaint);
        // Keep a persistent, highly visible but compact active-state pill.
        String text=t("圈画已开启","Circle active");
        float pillLeft=10f*density;
        float pillTop=12f*density;
        float pillWidth=(Locale.getDefault().getLanguage().equals("zh")?112f:127f)*density;
        RectF pill=new RectF(pillLeft,pillTop,pillLeft+pillWidth,pillTop+40f*density);
        canvas.drawRoundRect(pill,20f*density,20f*density,activationBadgePaint);
        float radius=4.5f*density;
        Paint dot=fillPaint;
        int before=dot.getColor();
        dot.setColor(YFloatOverlayStyle.selectionColor(app));
        canvas.drawCircle(pill.left+18f*density,pill.centerY(),radius,dot);
        dot.setColor(before);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12f*density);
        Paint.FontMetrics fm=textPaint.getFontMetrics();
        canvas.drawText(text,pill.centerX()+7f*density,
                pill.centerY()-(fm.ascent+fm.descent)*.5f,textPaint);
    }

    private static void dimOutside(Canvas canvas,RectF selection,int alpha) {
        if (selection==null||selection.isEmpty())return;
        canvas.save();
        canvas.clipOutRect(selection);
        canvas.drawColor(Color.argb(alpha,0,0,0));
        canvas.restore();
    }

    private void drawHint(Canvas canvas,String message) {
        if (message == null || message.isBlank()) return;
        float y=getHeight()-40*density;
        float width=Math.min(getWidth()-24*density,Math.max(180*density,
                message.length()*6.3f*density+32*density));
        RectF bg=new RectF((getWidth()-width)/2f,y-24*density,
                (getWidth()+width)/2f,y+20*density);
        toolbarPaint.setColor(Color.argb(225,28,31,40));
        canvas.drawRoundRect(bg,22*density,22*density,toolbarPaint);
        textPaint.setTextSize(12*density);
        canvas.drawText(message,bg.centerX(),bg.centerY()+4*density,textPaint);
    }

    private void drawClose(Canvas canvas) {
        float right=getWidth()-10*density;
        closeHit.set(right-42*density,12*density,right,54*density);
        toolbarPaint.setColor(Color.argb(226,28,31,40));
        canvas.drawRoundRect(closeHit,21*density,21*density,toolbarPaint);
        outlinePaint.setColor(Color.WHITE);
        outlinePaint.setStrokeWidth(2*density);
        float cx=closeHit.centerX(),cy=closeHit.centerY(),r=6*density;
        canvas.drawLine(cx-r,cy-r,cx+r,cy+r,outlinePaint);
        canvas.drawLine(cx-r,cy+r,cx+r,cy-r,outlinePaint);
        outlinePaint.setColor(YFloatOverlayStyle.selectionColor(app));
        outlinePaint.setStrokeWidth(2.4f*density);
    }

    private RectF selectedViewBox() {
        return frame.bitmapToView(
                new RectF(state.left,state.top,state.right,state.bottom),
                getWidth(),getHeight());
    }

    private void drawImageRegion(Canvas canvas) {
        RectF r=selectedViewBox();
        if(r.isEmpty())return;
        // Always show the rectangular crop that the screenshot result will
        // actually use; the transient hand-drawn lasso is never the crop mask.
        dimOutside(canvas,r,NativeCircleVisualPolicy.SELECTION_OUTSIDE_ALPHA);
        canvas.drawRoundRect(r,10*density,10*density,outlinePaint);
        float[] xs={r.left,r.right,r.right,r.left};
        float[] ys={r.top,r.top,r.bottom,r.bottom};
        for(int i=0;i<4;i++){
            canvas.drawCircle(xs[i],ys[i],5*density,fillPaint);
            canvas.drawCircle(xs[i],ys[i],5*density,outlinePaint);
            imageHandles[i].set(xs[i]-21*density,ys[i]-21*density,
                    xs[i]+21*density,ys[i]+21*density);
        }
    }

    /** Circle/image menus are independent from Google's native text toolbar. */
    private void syncPopup() {
        if(closed||touching||handleDrag!=0
                ||state.phase!=NativeCircleState.Phase.IMAGE_READY){
            FloatActionMenu.dismiss();
            return;
        }
        RectF selection=selectedViewBox();
        if(selection.isEmpty()){FloatActionMenu.dismiss();return;}
        int[] origin=new int[2];
        getLocationOnScreen(origin);
        Rect screenBounds=new Rect(
                Math.round(origin[0]+selection.left),
                Math.round(origin[1]+selection.top),
                Math.round(origin[0]+selection.right),
                Math.round(origin[1]+selection.bottom));
        FloatActionMenu.showImageAt(app,screenBounds,true,this::handleAction);
    }

    private void routeResolvedGesture(FLCircleSelection.Selection gesture,long token) {
        if(closed||gesture==null||state.phase!=NativeCircleState.Phase.RESOLVING
                ||token!=state.revision)return;
        if(NativeCircleIntentRouter.classify(gesture.kind.name(),false)
                ==NativeCircleIntentRouter.Route.IMAGE){
            resolveImage(gesture,token);
            return;
        }
        // The gesture denotes TEXT recognition, NOT a screenshot request.
        // Do not initialize the local full-screen OCR engine. Lens receives
        // only a readable neighbourhood of the tap/highlight, never the whole
        // frozen screen.
        int[] c=NativeCircleGoogleRecognitionCrop.forGesture(
                gesture.kind.name(),gesture.bounds.left,gesture.bounds.top,
                gesture.bounds.right,gesture.bounds.bottom,
                gesture.focus.x,gesture.focus.y,
                frame.bitmap.getWidth(),frame.bitmap.getHeight(),density);
        if(c==null){resolveImage(gesture,token);return;}
        Bitmap input=null;
        try {
            input=Bitmap.createBitmap(frame.bitmap,c[0],c[1],c[2]-c[0],c[3]-c[1]);
            if(input==frame.bitmap){
                input=frame.bitmap.copy(Bitmap.Config.ARGB_8888,false);
                if(input==null)throw new IllegalStateException("bitmap copy returned null");
            }
        }catch(Throwable failure){
            traceError("google_text_crop",failure);
            resolveImage(gesture,token);
            return;
        }
        DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                "selected_from=native_text_gesture kind="+gesture.kind
                +" crop="+input.getWidth()+"x"+input.getHeight()
                +" screenshots=local_only");
        dismissActive("google_text_recognition");
        GoogleLensImageInputProbe.launch(app,input);
    }

    private void resolveImage(FLCircleSelection.Selection gesture,long token) {
        if(gesture==null||state.phase!=NativeCircleState.Phase.RESOLVING
                ||state.revision!=token)return;
        Rect selection=FLCircleSelection.exactRectAndClamp(gesture.bounds,
                frame.bitmap.getWidth(),frame.bitmap.getHeight());
        if(gesture.kind==FLCircleSelection.Kind.TAP){
            int radius=Math.max(42,Math.round(64*density));
            int cx=Math.round(gesture.focus.x),cy=Math.round(gesture.focus.y);
            selection.set(Math.max(0,cx-radius),Math.max(0,cy-radius),
                    Math.min(frame.bitmap.getWidth(),cx+radius),
                    Math.min(frame.bitmap.getHeight(),cy+radius));
        }
        if(!state.resolveImage(token,selection.left,selection.top,
                selection.right,selection.bottom))return;
        // Image intent always resolves to a rectangle with four resize handles.
        status="";
        invalidate();
        syncPopup();
        DiagnosticLog.i(app,"CIRCLE_NATIVE","auto route=image kind="+gesture.kind);
    }

    private void finishStroke(){
        FLCircleSelection.Selection gesture=FLCircleSelection.fromStroke(
                stroke,frame.bitmap.getWidth(),frame.bitmap.getHeight(),
                9*density,24*density);
        if(gesture==null){state.clear();invalidate();return;}
        Rect region=FLCircleSelection.exactRectAndClamp(gesture.bounds,
                frame.bitmap.getWidth(),frame.bitmap.getHeight());
        state.bounds(region.left,region.top,region.right,region.bottom);
        if(!state.resolving())return;
        routeResolvedGesture(gesture,state.revision);
        invalidate();
    }

    private void handleAction(String action) {
        if(state.phase!=NativeCircleState.Phase.IMAGE_READY)return;
        if(ImageMenuSettings.RESET.equals(action)){
            resetSelection();
            return;
        }
        Bitmap image=selectedImage();
        if(image==null)return;
        if(ImageMenuSettings.GOOGLE_OCR.equals(action)){
            DiagnosticLog.i(app,"LENS_IMAGE_INPUT",
                    "selected_from=native_circle_image_menu screenshots=local_only");
            dismissActive("google_ocr_probe");
            GoogleLensImageInputProbe.launch(app,image);
            return;
        }
        if(ImageMenuSettings.DONE.equals(action)){
            Rect source=frame.bitmapRectToScreen(selectedBitmapRect());
            dismissActive("image_result");
            boolean shown=ResultSurfaceRouter.showScreenshot(app,image,source);
            if(!shown&&!image.isRecycled())image.recycle();
            return;
        }
        if(ImageMenuSettings.FIND.equals(action)||ImageMenuSettings.SHARE.equals(action)
                ||ImageCustomMenuStore.isCustom(action))
            dismissActive("image_external_action");
        try {
            if(ImageMenuSettings.FIND.equals(action))
                ImageShareUtils.openWith(app,image);
            else if(ImageMenuSettings.COPY.equals(action))
                ImageShareUtils.copyToClipboard(app,image);
            else if(ImageMenuSettings.SHARE.equals(action))
                ImageShareUtils.share(app,image);
            else if(ImageMenuSettings.SAVE.equals(action))
                ScreenshotController.save(app,image);
            else if(ImageCustomMenuStore.isCustom(action))
                ImageCustomMenuStore.launch(app,action,image);
        } finally {
            if(!image.isRecycled())image.recycle();
        }
    }

    private Rect selectedBitmapRect() {
        return FLCircleSelection.exactRectAndClamp(
                new RectF(state.left,state.top,state.right,state.bottom),
                frame.bitmap.getWidth(),frame.bitmap.getHeight());
    }

    private Bitmap selectedImage(){
        try {
            Rect rect=selectedBitmapRect();
            Bitmap cropped=Bitmap.createBitmap(frame.bitmap,rect.left,rect.top,
                    rect.width(),rect.height());
            if(cropped==frame.bitmap){
                Bitmap copy=frame.bitmap.copy(Bitmap.Config.ARGB_8888,false);
                if(copy==null)throw new IllegalStateException("image copy failed");
                return copy;
            }
            return cropped;
        }catch(Throwable failure){traceError("image_crop",failure);return null;}
    }

    private void traceError(String action,Throwable error){
        DiagnosticLog.i(app,"CIRCLE_NATIVE",
                action+" failed="+ScreenCaptureBackend.safeMessage(error));
    }
    private void resetSelection(){
        FloatActionMenu.dismiss();
        state.clear();status="";
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(closed)return false;
        int action=event.getActionMasked();
        float x=event.getX(),y=event.getY();
        if(action==MotionEvent.ACTION_DOWN){
            if(closeHit.contains(x,y)){dismissActive("close");return true;}
            // The menu owns its own touchable popup window. Any touch in the
            // canvas begins a handle adjustment or fresh selection instead.
            FloatActionMenu.dismiss();
            if(state.phase==NativeCircleState.Phase.IMAGE_READY){
                for(int i=0;i<imageHandles.length;i++){
                    if(imageHandles[i].contains(x,y)){
                        handleDrag=3+i;
                        imageDragOrigin.set(state.left,state.top,state.right,state.bottom);
                        return true;
                    }
                }
            }
            PointF bitmap=frame.viewToBitmap(x,y,getWidth(),getHeight());
            state.begin(bitmap.x,bitmap.y);
            downX=bitmap.x;downY=bitmap.y;
            minX=maxX=bitmap.x;minY=maxY=bitmap.y;
            touching=true;
            handleDrag=0;
            stroke.clear();stroke.add(bitmap);
            dragPath.reset();dragPath.moveTo(x,y);
            status="";
            invalidate();return true;
        }
        if(handleDrag!=0){
            if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
                adjustImageHandle(x,y);
                if(action==MotionEvent.ACTION_UP) {
                    handleDrag=0;
                    syncPopup();
                }
            }else if(action==MotionEvent.ACTION_CANCEL){
                handleDrag=0;
                syncPopup();
            }
            invalidate();
            return true;
        }
        if(!touching)return true;
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getHistorySize();i++)
                record(event.getHistoricalX(i),event.getHistoricalY(i));
            record(x,y);invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            touching=false;resetSelection();return true;
        }
        if(action==MotionEvent.ACTION_UP){
            record(x,y);
            touching=false;
            finishStroke();
            stroke.clear();dragPath.reset();
            invalidate();return true;
        }
        return true;
    }

    private void record(float x,float y){
        PointF bitmap=frame.viewToBitmap(x,y,getWidth(),getHeight());
        stroke.add(bitmap);
        minX=Math.min(minX,bitmap.x);minY=Math.min(minY,bitmap.y);
        maxX=Math.max(maxX,bitmap.x);maxY=Math.max(maxY,bitmap.y);
        state.bounds(minX,minY,maxX,maxY);
        dragPath.lineTo(x,y);
    }

    private void adjustImageHandle(float x,float y) {
        PointF p=frame.viewToBitmap(x,y,getWidth(),getHeight());
        float x1=imageDragOrigin.left,y1=imageDragOrigin.top;
        float x2=imageDragOrigin.right,y2=imageDragOrigin.bottom;
        switch(handleDrag){
            case 3 -> { x1=p.x; y1=p.y; }
            case 4 -> { x2=p.x; y1=p.y; }
            case 5 -> { x2=p.x; y2=p.y; }
            case 6 -> { x1=p.x; y2=p.y; }
            default -> { return; }
        }
        float l=Math.max(0f,Math.min(x1,x2));
        float t=Math.max(0f,Math.min(y1,y2));
        float r=Math.min(frame.bitmap.getWidth(),Math.max(x1,x2));
        float b=Math.min(frame.bitmap.getHeight(),Math.max(y1,y2));
        if(r-l<2f || b-t<2f)return;
        state.bounds(l,t,r,b);
    }

    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_BACK){
            dismissActive("back");return true;
        }
        return super.onKeyDown(code,event);
    }

    private void close(String reason) {
        if(closed)return;
        closed=true;state.close();
        FloatActionMenu.dismiss();
        host.remove(this,"native_circle_"+reason);
        if(closedCallback!=null)closedCallback.run();
        DiagnosticLog.i(app,"CIRCLE_NATIVE","closed "+reason);
    }
}
