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
 * Native Circle canvas. Exactly one immutable bitmap and one gesture/menu
 * surface for the entire session. No Google app, Hook, Lens viewport or native
 * Google search controller participates. Only the existing screenshot capture
 * and OCR engine supply data to this surface.
 */
final class NativeCircleWorkspace extends View {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static NativeCircleWorkspace active;

    private final Context app;
    private final FLCircleCapture.Frame frame;
    private final FlOverlayWindowHost host;
    private final WindowManager.LayoutParams window;
    private final Runnable closedCallback;
    private final NativeCircleState state = new NativeCircleState();
    private final Paint screenshot = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint select = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tool = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path strokePath = new Path();
    private final ArrayList<PointF> points = new ArrayList<>();
    private final ArrayList<Action> topActions = new ArrayList<>();
    private final ArrayList<Action> bottomActions = new ArrayList<>();
    private final float density;

    private OcrDocument fullDocument;
    private boolean ocrBusy;
    private FLCircleSelection.Selection pendingGesture;
    private boolean closed;
    private String status = "";
    private boolean touching;
    private float startBitmapX;
    private float startBitmapY;

    private static final class Action {
        final String title;
        final RectF hit;
        final Runnable run;
        Action(String title, RectF hit, Runnable run) {
            this.title = title; this.hit = hit; this.run = run;
        }
    }

    static synchronized boolean show(Context c, FLCircleCapture.Frame frame,
                                     Runnable onClosed) {
        if (c == null || frame == null || frame.bitmap == null
                || frame.bitmap.isRecycled()) return false;
        dismissActive("replace");
        Context app = c.getApplicationContext();
        Rect screen = frame.screenBounds;
        Rect display = ScreenGeometry.displayBounds(app);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.max(1, screen.width()), Math.max(1, screen.height()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = screen.left - display.left;
        lp.y = screen.top - display.top;
        FlOverlayWindowHost host = new FlOverlayWindowHost(app);
        NativeCircleWorkspace view = new NativeCircleWorkspace(app, host, lp,
                frame, onClosed);
        if (!host.addStable(view, lp, "native_circle_workspace")) return false;
        active = view;
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.startOcr();
        DiagnosticLog.i(app, "CIRCLE_NATIVE",
                "created fixed-frame=" + screen
                        + " bitmap=" + frame.bitmap.getWidth() + "x"
                        + frame.bitmap.getHeight() + " googleHook=false");
        return true;
    }

    static synchronized void dismissActive(String reason) {
        NativeCircleWorkspace prior = active;
        active = null;
        if (prior != null) prior.close(reason);
    }

    static synchronized void promoteFocus(String reason) {
        if (active != null && !active.closed) active.requestFocus();
    }

    private NativeCircleWorkspace(Context app, FlOverlayWindowHost host,
                                  WindowManager.LayoutParams lp,
                                  FLCircleCapture.Frame frame, Runnable onClosed) {
        super(app);
        this.app = app;
        this.host = host;
        this.window = lp;
        this.frame = frame;
        this.closedCallback = onClosed;
        density = getResources().getDisplayMetrics().density;
        scrim.setColor(Color.argb(70, 0, 0, 0));
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(3f * density);
        shape.setColor(Color.rgb(58, 153, 255));
        select.setStyle(Paint.Style.STROKE);
        select.setColor(Color.rgb(35, 151, 255));
        select.setStrokeWidth(2f * density);
        label.setTextSize(13f * density);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        tool.setColor(Color.argb(228, 24, 29, 41));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
        setClickable(true);
    }

    private String title(String zh, String en) {
        return Locale.getDefault().getLanguage().equals("zh") ? zh : en;
    }

    private void startOcr() {
        if (ocrBusy) return;
        ocrBusy = true;
        status = title("正在识别文字…", "Recognizing text…");
        CircleStableOcr.recognizeFullScreenSelected(app, frame.bitmap,
                new OcrEngine.DocumentCallback() {
            @Override public void onSuccess(OcrDocument document) {
                MAIN.post(() -> {
                    if (closed) return;
                    ocrBusy = false;
                    fullDocument = frame.transform.documentBitmapToScreen(document);
                    status = "";
                    if (pendingGesture != null) resolveText(pendingGesture);
                    invalidate();
                });
            }
            @Override public void onFailure(Throwable error) {
                MAIN.post(() -> {
                    if (closed) return;
                    ocrBusy = false;
                    status = title("文字识别不可用，可继续截图",
                            "OCR unavailable; image selection still works");
                    DiagnosticLog.i(app, "CIRCLE_NATIVE",
                            "ocr failure=" + ScreenCaptureBackend.safeMessage(error));
                    invalidate();
                });
            }
        });
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (closed || frame.bitmap.isRecycled()) return;
        int w = getWidth(), h = getHeight();
        canvas.drawBitmap(frame.bitmap, null,
                new Rect(0, 0, Math.max(1,w), Math.max(1,h)), screenshot);

        if (state.phase == NativeCircleState.Phase.DRAWING) {
            canvas.drawPath(strokePath, shape);
        } else if (state.phase == NativeCircleState.Phase.IMAGE_READY
                || state.phase == NativeCircleState.Phase.TEXT_READY
                || state.phase == NativeCircleState.Phase.TEXT_PENDING) {
            RectF box = frame.bitmapToView(
                    new RectF(state.left, state.top, state.right, state.bottom), w, h);
            if (!box.isEmpty()) {
                canvas.save();
                canvas.clipOutRect(box);
                canvas.drawColor(Color.argb(50,0,0,0));
                canvas.restore();
                canvas.drawRoundRect(box, 7f*density,7f*density,select);
                float corner=5f*density;
                canvas.drawCircle(box.left,box.top,corner,select);
                canvas.drawCircle(box.right,box.bottom,corner,select);
            }
        }
        drawActions(canvas, topActions, topLabels(), 12*density, true);
        if (state.phase == NativeCircleState.Phase.TEXT_READY) {
            drawActions(canvas, bottomActions,
                    new String[]{title("复制","Copy"), title("翻译","Translate"),
                            title("搜索","Search"),title("分享","Share"),
                            title("重选","Reset")},
                    h-62*density,false);
        } else if (state.phase == NativeCircleState.Phase.IMAGE_READY) {
            drawActions(canvas,bottomActions,
                    new String[]{title("完成","Done"),title("重选","Reset")},
                    h-62*density,false);
        } else {
            bottomActions.clear();
        }

        if (!status.isBlank()) {
            label.setTextSize(12*density);
            canvas.drawText(status,w/2f,Math.max(91*density,h-82*density),label);
        }
    }

    private String[] topLabels() {
        return new String[]{title("文字","Text"),title("圈画","Circle"),
                title("截图","Region"),title("关闭","Close")};
    }

    private void drawActions(Canvas canvas, List<Action> output, String[] names,
                             float top, boolean mainBar) {
        output.clear();
        float gap=5*density;
        float left=8*density;
        float width=Math.max(1f,(getWidth()-16*density-gap*(names.length-1))/names.length);
        for(int i=0;i<names.length;i++){
            RectF area=new RectF(left,top,left+width,top+43*density);
            boolean selected=mainBar&&(i==0&&state.mode==NativeCircleState.Mode.TEXT
                    ||i==1&&state.mode==NativeCircleState.Mode.LASSO
                    ||i==2&&state.mode==NativeCircleState.Mode.RECTANGLE);
            tool.setColor(selected?Color.argb(242,24,104,192):Color.argb(229,25,30,40));
            canvas.drawRoundRect(area,10*density,10*density,tool);
            label.setTextSize(12*density);
            Paint.FontMetrics m=label.getFontMetrics();
            canvas.drawText(names[i],area.centerX(),
                    area.centerY()-(m.ascent+m.descent)/2,label);
            final int index=i;
            output.add(new Action(names[i],area,
                    ()->{if(mainBar)topAction(index);else bottomAction(index);}));
            left+=width+gap;
        }
    }

    private void topAction(int index) {
        if (index == 3) { dismissActive("user_close");return; }
        state.changeMode(index==0?NativeCircleState.Mode.TEXT:
                index==1?NativeCircleState.Mode.LASSO:
                        NativeCircleState.Mode.RECTANGLE);
        pendingGesture=null;
        status="";
        invalidate();
    }

    private void bottomAction(int index) {
        if (state.phase == NativeCircleState.Phase.IMAGE_READY) {
            if(index==0)finishRegion();
            else state.clear();
        } else if(state.phase==NativeCircleState.Phase.TEXT_READY) {
            if(index==4){state.clear();invalidate();return;}
            String text=state.selectedText;
            if(index==0){
                ClipboardManager clipboard=(ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("YFloat",text));
                Toast.makeText(app,title("已复制","Copied"),Toast.LENGTH_SHORT).show();
            }else if(index==1){
                String target=Locale.getDefault().getLanguage().equals("zh")?"en":"zh-CN";
                // Release the full-screen touch owner before opening any
                // external activity; otherwise the browser is obscured.
                dismissActive("translate_action");
                openWeb("https://translate.google.com/?sl=auto&tl="+target
                        +"&text="+encode(text)+"&op=translate");
            }else if(index==2){
                dismissActive("search_action");
                openWeb("https://www.google.com/search?q="+encode(text));
            }else if(index==3){
                dismissActive("share_action");
                try{
                    Intent share=new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT,text);
                    share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(Intent.createChooser(share,title("分享","Share"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }catch(Throwable t){DiagnosticLog.i(app,"CIRCLE_NATIVE","share failed="+t);}
            }
        }
        invalidate();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value,StandardCharsets.UTF_8);
    }

    private void openWeb(String url) {
        try {
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(intent);
        }catch(Throwable t){
            DiagnosticLog.i(app,"CIRCLE_NATIVE","external action failed="+t);
            Toast.makeText(app,title("没有可用浏览器","No browser available"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void finishRegion() {
        if(!state.canFinishImage())return;
        Rect crop=FLCircleSelection.exactRectAndClamp(
                new RectF(state.left,state.top,state.right,state.bottom),
                frame.bitmap.getWidth(),frame.bitmap.getHeight());
        if(crop.isEmpty())return;
        try {
            Bitmap cut=Bitmap.createBitmap(frame.bitmap,crop.left,crop.top,
                    crop.width(),crop.height());
            Rect source=frame.bitmapRectToScreen(crop);
            dismissActive("image_committed");
            boolean ok=ResultSurfaceRouter.showScreenshot(app,cut,source);
            if(!ok&&!cut.isRecycled())cut.recycle();
            DiagnosticLog.i(app,"CIRCLE_NATIVE",
                    "screenshot selection="+source+" shown="+ok);
        }catch(Throwable failure){
            DiagnosticLog.i(app,"CIRCLE_NATIVE",
                    "crop failed="+ScreenCaptureBackend.safeMessage(failure));
        }
    }

    private void resolveText(FLCircleSelection.Selection gesture) {
        if(closed||gesture==null)return;
        pendingGesture=gesture;
        if(fullDocument==null){
            if(!ocrBusy)status=title("未识别到文字","No recognized text");
            invalidate();
            return;
        }
        List<CircleGestureTextSelector.GroupHit> selected=
                CircleGestureTextSelector.hitGroups(app,frame,gesture,fullDocument,10f,10f);
        if(selected.isEmpty()){
            state.clear();
            status=title("此处没有识别到文字","No text at selection");
            invalidate();
            return;
        }
        StringBuilder text=new StringBuilder();
        Rect region=new Rect();
        for(CircleGestureTextSelector.GroupHit g:selected) {
            if(g.text.isBlank())continue;
            if(text.length()>0)text.append(' ');
            text.append(g.text);
            region.union(g.bounds);
        }
        Rect bitmap=frame.screenRectToBitmap(region);
        boolean accepted=state.textReady(state.revision,text.toString(),
                bitmap.left,bitmap.top,bitmap.right,bitmap.bottom);
        if(accepted)status="";
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (closed) return false;
        int action=event.getActionMasked();
        float x=event.getX(),y=event.getY();
        if(action==MotionEvent.ACTION_DOWN){
            for(Action command:topActions)if(command.hit.contains(x,y)){
                command.run.run();return true;
            }
            for(Action command:bottomActions)if(command.hit.contains(x,y)){
                command.run.run();return true;
            }
            PointF p=frame.viewToBitmap(x,y,getWidth(),getHeight());
            state.begin(p.x,p.y);
            startBitmapX=p.x;
            startBitmapY=p.y;
            touching=true;points.clear();points.add(p);
            strokePath.reset();strokePath.moveTo(x,y);
            status="";
            invalidate();return true;
        }
        if(!touching)return true;
        if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getHistorySize();i++){
                PointF prior=frame.viewToBitmap(event.getHistoricalX(i),
                        event.getHistoricalY(i),getWidth(),getHeight());
                points.add(prior);
                strokePath.lineTo(event.getHistoricalX(i),event.getHistoricalY(i));
            }
            PointF p=frame.viewToBitmap(x,y,getWidth(),getHeight());
            points.add(p);
            strokePath.lineTo(x,y);
            state.bounds(startBitmapX,startBitmapY,p.x,p.y);
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){touching=false;state.clear();invalidate();return true;}
        if(action==MotionEvent.ACTION_UP){
            touching=false;
            PointF p=frame.viewToBitmap(x,y,getWidth(),getHeight());
            points.add(p);strokePath.lineTo(x,y);
            FLCircleSelection.Selection selection=FLCircleSelection.fromStroke(points,
                    frame.bitmap.getWidth(),frame.bitmap.getHeight(),
                    9*density,24*density);
            points.clear();strokePath.reset();
            if(selection==null){state.clear();invalidate();return true;}
            Rect bounds=FLCircleSelection.exactRectAndClamp(selection.bounds,
                    frame.bitmap.getWidth(),frame.bitmap.getHeight());
            state.bounds(bounds.left,bounds.top,bounds.right,bounds.bottom);
            if(state.mode==NativeCircleState.Mode.RECTANGLE){
                if(bounds.width()<4||bounds.height()<4){state.clear();invalidate();return true;}
            }
            boolean done=state.finish(true);
            if(done&&state.mode==NativeCircleState.Mode.TEXT)resolveText(selection);
            else invalidate();
            return true;
        }
        return true;
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if(code==KeyEvent.KEYCODE_BACK){dismissActive("back");return true;}
        return super.onKeyDown(code,event);
    }

    private void close(String reason) {
        if(closed)return;
        closed=true;
        state.close();
        host.remove(this,"native_circle_workspace_"+reason);
        if(closedCallback!=null)closedCallback.run();
        DiagnosticLog.i(app,"CIRCLE_NATIVE","closed reason="+reason);
    }
}
