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
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Toast;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Intent-first frozen-screen Circle workspace.
 *
 * Nothing moves the screenshot: it is painted once in immutable frame
 * coordinates. YFloat resolves taps and text highlights in place via its
 * stable full-screen OCR document, preserving draggable character handles,
 * live selection and the native text action menu. Closed circles and scribbles
 * remain local screenshot regions. Paddle V2 is the preferred on-device
 * experiment from the circle image menu, never an automatic text redirect.
 */
final class NativeCircleWorkspace extends View {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
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
    private final Paint selectionBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toolbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dragPath = new Path();
    private final Path awaitingTextPath = new Path();
    private final ArrayList<PointF> stroke = new ArrayList<>();
    private final RectF closeHit = new RectF();
    private final RectF firstHandle = new RectF();
    private final RectF lastHandle = new RectF();
    private final RectF[] imageHandles = {
            new RectF(), new RectF(), new RectF(), new RectF()
    };
    private final RectF imageDragOrigin = new RectF();

    private OcrDocument fullDocument;
    private Rect recognizedRoi;
    private List<OcrDocument.CharUnit> cachedChars=List.of();
    private final IdentityHashMap<OcrDocument.CharUnit,Integer> charOrdinals=
            new IdentityHashMap<>();
    private CircleCharSpatialIndex charIndex;
    private PaddleRequestToken activeOcr;
    private int ocrSequence;
    private boolean selectAllAfterFullOcr;
    // Character boxes are only a selection surface. Source text and its
    // original whitespace belong to the recognized OcrDocument.
    private final IdentityHashMap<OcrDocument.CharUnit,OcrSelectionText.Span>
            sourceSpans=new IdentityHashMap<>();
    private ArrayList<OcrDocument.CharUnit> chosenChars = new ArrayList<>();
    private int firstChar = -1, lastChar = -1;
    /** -1 means explicit Select all; otherwise hands stay on initial OCR line. */
    private int selectedLine=-1;
    private boolean ocrBusy, closed, touching;
    private int handleDrag;
    private long gestureRevision;
    private FLCircleSelection.Selection pendingGesture;
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
        // No full-screen OCR at open: image selection requires no recognizer.
        DiagnosticLog.i(app,"CIRCLE_NATIVE",
                "ready intent-first image="+frame.bitmap.getWidth()+"x"
                        +frame.bitmap.getHeight()
                        +" ocrEngine=local"
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
        fillPaint.setColor(YFloatOverlayStyle.accentWithAlpha(app,76));
        // OCR only supplies hit geometry. Never paint a blue glyph layer:
        // selected pixels are restored from the frozen screenshot verbatim.
        selectionBorderPaint.setStyle(Paint.Style.STROKE);
        selectionBorderPaint.setStrokeWidth(1.6f*density);
        selectionBorderPaint.setColor(YFloatOverlayStyle.selectionWithAlpha(
                app,NativeCircleVisualPolicy.TEXT_HIGHLIGHT_BORDER_ALPHA));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        toolbarPaint.setColor(Color.argb(241, 31, 34, 42));
        setFocusable(true);
        setClickable(true);
    }

    private String t(String zh,String en) {
        return Locale.getDefault().getLanguage().equals("zh") ? zh : en;
    }

    private void startOcr(Rect sourceRegion,boolean expandForSelectAll) {
        if(closed)return;
        if(activeOcr!=null)activeOcr.cancel();
        final PaddleRequestToken request=new PaddleRequestToken();
        activeOcr=request;
        final int sequence=++ocrSequence;
        final Rect roi=sourceRegion==null
                ? new Rect(0,0,frame.bitmap.getWidth(),frame.bitmap.getHeight())
                : new Rect(sourceRegion);
        final boolean full=roi.left==0&&roi.top==0&&
                roi.width()==frame.bitmap.getWidth()&&
                roi.height()==frame.bitmap.getHeight();
        selectAllAfterFullOcr=expandForSelectAll;
        // The frozen frame may be recycled as soon as the user finishes a crop.
        // OCR runs asynchronously and must own its bitmap until the recognizer
        // has reported completion. Never pass that soon-to-be-recycled frame.
        final Bitmap input;
        try {
            // Crop the frozen source first. Paddle detector then operates
            // on local pixels while its returned geometry is mapped by the
            // authoritative ScreenBitmapTransform.
            input=full?frame.bitmap.copy(Bitmap.Config.ARGB_8888,false)
                :Bitmap.createBitmap(frame.bitmap,roi.left,roi.top,
                    roi.width(),roi.height());
            if (input == null) throw new IllegalStateException("OCR bitmap copy failed");
        } catch (Throwable error) {
            ocrBusy=false;
            if(activeOcr==request)activeOcr=null;
            selectAllAfterFullOcr=false;
            status=t("文字识别不可用，截图仍可使用",
                    "Text recognition unavailable; image crops still work");
            DiagnosticLog.i(app,"CIRCLE_NATIVE",
                    "ocr snapshot failed="+ScreenCaptureBackend.safeMessage(error));
            FLCircleSelection.Selection pending=pendingGesture;
            if(pending!=null && state.phase==NativeCircleState.Phase.RESOLVING){
                if(!NativeCircleIntentRouter.permitsImageFallback(pending.kind.name()))
                    rejectTextGesture(pending,gestureRevision,"snapshot_error");
                else resolveImage(pending,gestureRevision);
            }
            invalidate();
            return;
        }
        final int localWidth=input.getWidth(),localHeight=input.getHeight();
        ocrBusy = true;
        status = t("正在识别屏幕文字", "Recognizing screen text");
        try {
            DiagnosticLog.i(app,"PADDLE_SCHED",
                    "circle_roi="+roi.toShortString()+" full="+full+
                    " selectAll="+expandForSelectAll);
            CircleStableOcr.recognizeFullScreenSelected(app, input,request,
                    new OcrEngine.DocumentCallback() {
                @Override public void onSuccess(OcrDocument result) {
                    recycleOcrBitmap(input);
                    MAIN.post(() -> {
                        if (closed || request!=activeOcr || sequence!=ocrSequence)return;
                        try {
                            fullDocument = full
                                ? frame.transform.documentBitmapToScreen(result)
                                : frame.transform.documentLocalToScreen(result,roi,
                                        localWidth,localHeight,"circle_roi:");
                            recognizedRoi=new Rect(roi);
                            rebuildTextOffsets();
                        } catch (Throwable error) {
                            fullDocument = null;
                            sourceSpans.clear();
                            cachedChars=List.of();charIndex=null;
                            DiagnosticLog.i(app, "CIRCLE_NATIVE",
                                    "ocr coordinates failed="
                                            + ScreenCaptureBackend.safeMessage(error));
                        }
                        ocrBusy = false;
                        status = "";
                        FLCircleSelection.Selection pending = pendingGesture;
                        if(fullDocument==null && pending!=null){
                            if(!full && !NativeCircleIntentRouter.permitsImageFallback(
                                    pending.kind.name())){
                                DiagnosticLog.i(app,"PADDLE_SCHED",
                                        "retry_full=1 reason=roi_coordinate_map");
                                startOcr(null,false);
                            }else if(!NativeCircleIntentRouter.permitsImageFallback(
                                    pending.kind.name()))
                                rejectTextGesture(pending,gestureRevision,"ocr_coordinates");
                            else resolveImage(pending,gestureRevision);
                            invalidate();
                            return;
                        }
                        if(selectAllAfterFullOcr && fullDocument!=null){
                            selectAllAfterFullOcr=false;
                            List<OcrDocument.CharUnit> allChars=fullDocument.chars();
                            if(!allChars.isEmpty() &&
                                state.phase==NativeCircleState.Phase.TEXT_READY){
                                selectedLine=-1;
                                firstChar=0;lastChar=allChars.size()-1;
                                applyTextIndices(state.revision,false);
                            }
                        }else if(pending!=null)
                            routeResolvedGesture(pending,gestureRevision);
                        invalidate();
                    });
                }
                @Override public void onFailure(Throwable failure) {
                    recycleOcrBitmap(input);
                    MAIN.post(() -> {
                        if (closed || request!=activeOcr || sequence!=ocrSequence)return;
                        ocrBusy = false;
                        if(selectAllAfterFullOcr){
                            selectAllAfterFullOcr=false;
                            status=t("全屏选字扩展失败，可继续使用当前选区",
                                    "Full-screen OCR unavailable; current text remains");
                            invalidate();
                            return;
                        }
                        FLCircleSelection.Selection pending = pendingGesture;
                        if(pending!=null && !full &&
                                !NativeCircleIntentRouter.permitsImageFallback(
                                    pending.kind.name())){
                            DiagnosticLog.i(app,"PADDLE_SCHED",
                                    "retry_full=1 reason=roi_inference_error");
                            startOcr(null,false);
                            return;
                        }
                        status = t("无法识别文字，仍可圈选图片",
                                "Text unavailable; circle an image instead");
                        if (pending != null) {
                            if (!NativeCircleIntentRouter.permitsImageFallback(pending.kind.name()))
                                rejectTextGesture(pending,gestureRevision,"ocr_failure");
                            else resolveImage(pending,gestureRevision);
                        }
                        invalidate();
                        DiagnosticLog.i(app, "CIRCLE_NATIVE",
                                "ocr unavailable=" + ScreenCaptureBackend.safeMessage(failure));
                    });
                }
            });
        } catch (Throwable error) {
            recycleOcrBitmap(input);
            if(activeOcr==request)activeOcr=null;
            ocrBusy=false;
            selectAllAfterFullOcr=false;
            status=t("文字识别不可用，截图仍可使用",
                    "Text recognition unavailable; image crops still work");
            DiagnosticLog.i(app,"CIRCLE_NATIVE",
                    "ocr start failed="+ScreenCaptureBackend.safeMessage(error));
            FLCircleSelection.Selection pending=pendingGesture;
            if(pending!=null && state.phase==NativeCircleState.Phase.RESOLVING){
                if(!NativeCircleIntentRouter.permitsImageFallback(pending.kind.name()))
                    rejectTextGesture(pending,gestureRevision,"ocr_launch_error");
                else resolveImage(pending,gestureRevision);
            }
            invalidate();
        }
    }

    private static void recycleOcrBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
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
        } else if (state.phase == NativeCircleState.Phase.TEXT_READY) {
            drawTextSelection(canvas);
        } else if (state.phase == NativeCircleState.Phase.RESOLVING) {
            if (pendingGesture != null
                    && pendingGesture.kind == FLCircleSelection.Kind.HIGHLIGHT) {
                // Text highlights retain the real line, never a crop rectangle.
                canvas.drawPath(awaitingTextPath, outlinePaint);
            } else {
                RectF box = selectedViewBox();
                if (!box.isEmpty()) {
                    dimOutside(canvas, box, NativeCircleVisualPolicy.SELECTION_OUTSIDE_ALPHA);
                    canvas.drawRoundRect(box, 10*density,10*density,outlinePaint);
                }
            }
        }

        drawActiveIndicator(canvas);
        drawClose(canvas);
        // Commands are Android Views in FloatActionMenu, not text
        // painted onto the frozen screenshot. The overlay only draws the
        // selection, OCR highlights, handles and active-session indicator.
        if (!status.isBlank()) {
            drawHint(canvas,status);
        } else if (state.phase == NativeCircleState.Phase.IDLE) {
            drawHint(canvas,t("圈画已开启 · 圈住图片或划选文字",
                    "Circle active · Draw around an image or text"));
        } else if (state.phase == NativeCircleState.Phase.RESOLVING) {
            drawHint(canvas,t("正在识别划线文字…","Recognizing highlighted text…"));
        }
    }

    private float safeToolbarTop(){
        // FLAG_LAYOUT_NO_LIMITS overlay can draw over the status bar, so
        // android's insets may be zero. Keep a minimum safe status-band.
        float top=30f*density;
        WindowInsets insets=getRootWindowInsets();
        if(insets!=null)top=Math.max(top,
                insets.getInsets(WindowInsets.Type.statusBars()).top);
        return top+8f*density;
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
        float pillTop=safeToolbarTop();
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
        float top=safeToolbarTop();
        closeHit.set(right-42*density,top,right,top+42*density);
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

    private RectF screenToView(Rect screen) {
        return frame.bitmapToView(new RectF(frame.screenRectToBitmap(screen)),
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

    private void drawTextSelection(Canvas canvas) {
        firstHandle.setEmpty();
        lastHandle.setEmpty();
        if(chosenChars.isEmpty()||fullDocument==null)return;
        RectF first=null,last=null;
        // CTC character rectangles are hit targets, not reliable visual
        // bounds on large launcher glyphs. Use the detector's original line
        // width only when ALL drawable characters in that line are selected.
        // For a dragged / partial selection preserve character-precise width.
        LinkedHashMap<Integer,Rect> glyphsByLine=new LinkedHashMap<>();
        IdentityHashMap<OcrDocument.CharUnit,Boolean> selectedSet=
                new IdentityHashMap<>();
        for(OcrDocument.CharUnit glyph:chosenChars){
            Rect b=glyph.bounds();
            if(b.isEmpty())continue;
            selectedSet.put(glyph,Boolean.TRUE);
            Rect union=glyphsByLine.get(glyph.line());
            if(union==null)glyphsByLine.put(glyph.line(),new Rect(b));
            else union.union(b);
        }
        for(var entry:glyphsByLine.entrySet()){
            Rect glyphBounds=entry.getValue();
            Rect rowBounds=glyphBounds;
            boolean wholeLine=false;
            for(OcrDocument.Line row:fullDocument.lines()){
                if(row.chars().isEmpty()||row.chars().get(0).line()!=entry.getKey())
                    continue;
                rowBounds=row.bounds();
                wholeLine=true;
                for(OcrDocument.CharUnit unit:row.chars()){
                    if(unit.text().isBlank()||unit.bounds().isEmpty())continue;
                    if(!selectedSet.containsKey(unit)){
                        wholeLine=false;
                        break;
                    }
                }
                break;
            }
            float[] band=NativeCircleVisualPolicy.textSelectionScreenBand(
                    glyphBounds.left,glyphBounds.top,glyphBounds.right,glyphBounds.bottom,
                    rowBounds.left,rowBounds.top,rowBounds.right,rowBounds.bottom,
                    wholeLine);
            RectF screenBand=new RectF(band[0],band[1],band[2],band[3]);
            RectF selected=screenToViewF(screenBand);
            if(!selected.isEmpty()){
                // Scrim was painted before the selection. Reveal the actual
                // untouched screenshot pixels inside the selected band.
                canvas.save();
                canvas.clipRect(selected);
                canvas.drawBitmap(frame.bitmap,null,
                        new Rect(0,0,getWidth(),getHeight()),screenshotPaint);
                canvas.restore();

                // The outline lives outside the revealed pixels. Its stroke
                // must not color any Chinese or Latin glyph inside the band.
                RectF outline=new RectF(selected);
                outline.inset(-NativeCircleVisualPolicy.textBorderOutsideGap(density),
                        -NativeCircleVisualPolicy.textBorderOutsideGap(density));
                canvas.drawRoundRect(outline,3*density,3*density,selectionBorderPaint);
                if(first==null)first=new RectF(outline);
                last=new RectF(outline);
            }
        }
        if(first==null||last==null)return;
        // Never paint blue stems through the first/last glyph: the screenshot
        // holds the actual app font, which must remain visibly unchanged.
        float radius=Math.min(9f*density,Math.max(4f*density,
                YFloatOverlayStyle.handleRadius(app)));
        float hx=NativeCircleVisualPolicy.leadingHandleX(first.left,density);
        float tx=NativeCircleVisualPolicy.trailingHandleX(last.right,density);
        float hy=NativeCircleVisualPolicy.handleCenterY(first.bottom,density);
        float ty=NativeCircleVisualPolicy.handleCenterY(last.bottom,density);
        canvas.drawLine(hx,NativeCircleVisualPolicy.handleStemTop(first.bottom,density),
                hx,hy,outlinePaint);
        canvas.drawLine(tx,NativeCircleVisualPolicy.handleStemTop(last.bottom,density),
                tx,ty,outlinePaint);
        canvas.drawCircle(hx,hy,radius/1.8f,outlinePaint);
        canvas.drawCircle(tx,ty,radius/1.8f,outlinePaint);
        firstHandle.set(hx-24*density,hy-24*density,
                hx+24*density,hy+24*density);
        lastHandle.set(tx-24*density,ty-24*density,
                tx+24*density,ty+24*density);
    }

    private RectF screenToViewF(RectF screen){
        return frame.transform.screenToView(screen,getWidth(),getHeight());
    }

    /** One menu host for both OCR text and selected image rectangles. */
    private void syncPopup() {
        if (closed || touching || handleDrag != 0) return;
        boolean text = state.phase == NativeCircleState.Phase.TEXT_READY;
        boolean image = state.phase == NativeCircleState.Phase.IMAGE_READY;
        if (!text && !image) {
            FloatActionMenu.dismiss();
            return;
        }
        RectF selection = selectedViewBox();
        if (selection.isEmpty()) {
            FloatActionMenu.dismiss();
            return;
        }
        int[] origin = new int[2];
        getLocationOnScreen(origin);
        Rect screenBounds = new Rect(
                Math.round(origin[0] + selection.left),
                Math.round(origin[1] + selection.top),
                Math.round(origin[0] + selection.right),
                Math.round(origin[1] + selection.bottom));
        if (text) {
            if (!state.selectedText.isBlank()) {
                FloatActionMenu.showTextAt(app, state.selectedText,
                        this::selectAllText, screenBounds);
            }
        } else {
            FloatActionMenu.showImageAt(app, screenBounds, true, this::handleAction);
        }
    }

    private void selectAllText() {
        if (closed || fullDocument == null
                || state.phase != NativeCircleState.Phase.TEXT_READY
                || fullDocument.chars().isEmpty()) return;
        if(recognizedRoi!=null && (recognizedRoi.width()!=frame.bitmap.getWidth() ||
                recognizedRoi.height()!=frame.bitmap.getHeight())){
            // Select-all must cover the entire original screen, not only the
            // local text ROI that was required by the preceding highlight.
            startOcr(null,true);
            return;
        }
        selectedLine=-1;
        firstChar = 0;
        lastChar = fullDocument.chars().size() - 1;
        applyTextIndices(state.revision, false);
        // FloatActionMenu closes itself after Select all: republish next loop.
        MAIN.post(this::syncPopup);
    }

    private void routeResolvedGesture(FLCircleSelection.Selection gesture,long token) {
        if(closed||gesture==null||state.phase!=NativeCircleState.Phase.RESOLVING
                ||token!=state.revision)return;
        pendingGesture=null;
        // Screenshot intent must never wait for OCR or be reclassified by text inside it.
        if (NativeCircleIntentRouter.classify(gesture.kind.name(),
                fullDocument != null) == NativeCircleIntentRouter.Route.IMAGE) {
            // Image gestures never need OCR. Do not leave queued OCR work
            // occupying the serialized native inference lane.
            if(activeOcr!=null)activeOcr.cancel();
            activeOcr=null;
            ocrBusy=false;
            resolveImage(gesture, token);
            return;
        }
        if(fullDocument==null ||
                (recognizedRoi!=null &&
                 !recognizedRoi.contains(FLCircleSelection.exactRectAndClamp(
                     gesture.bounds,frame.bitmap.getWidth(),frame.bitmap.getHeight())))){
            pendingGesture=gesture;
            if(!ocrBusy || fullDocument!=null){
                // Intent-first local OCR. Expand text stroke to include complete
                // baseline, adjacent glyphs and tall mixed CJK/Latin shapes.
                Rect roi=PaddleCircleRoiPolicy.forGesture(
                    gesture.bounds,frame.bitmap.getWidth(),frame.bitmap.getHeight());
                fullDocument=null;
                sourceSpans.clear();
                cachedChars=List.of();charIndex=null;
                startOcr(roi,false);
            } else status=t("正在识别…","Recognizing…");
            invalidate();
            return;
        }
        List<OcrDocument.CharUnit> all=cachedChars;
        List<CircleGestureTextSelector.GroupHit> groups;
        try {
            groups = CircleGestureTextSelector.hitGroups(
                    app,frame,gesture,fullDocument,10f,10f);
        } catch (Throwable error) {
            DiagnosticLog.i(app, "CIRCLE_NATIVE",
                    "ocr hit matching failed=" + ScreenCaptureBackend.safeMessage(error));
            if(!NativeCircleIntentRouter.permitsImageFallback(gesture.kind.name()))
                rejectTextGesture(gesture,token,"matching_error");
            else resolveImage(gesture,token);
            return;
        }
        if(groups==null || groups.isEmpty()) {
            if(retryWholeForHighlight(gesture,token,"no_roi_text"))return;
            if(!NativeCircleIntentRouter.permitsImageFallback(gesture.kind.name()))
                rejectTextGesture(gesture,token,"no_text_at_stroke");
            else resolveImage(gesture,token);
            return;
        }
        int first=Integer.MAX_VALUE,last=-1;
        for(CircleGestureTextSelector.GroupHit hit:groups){
            for(OcrDocument.CharUnit ch:hit.chars) {
                Integer matched=charOrdinals.get(ch);
                if(matched==null){
                    // Some screen-transform adapters produce equivalent
                    // CharUnits instead of the same object; validate geometry.
                    for(int i=0;i<all.size();i++){
                        OcrDocument.CharUnit doc=all.get(i);
                        if(doc.line()==ch.line() && doc.order()==ch.order() &&
                                doc.bounds().equals(ch.bounds())){
                            matched=i;break;
                        }
                    }
                }
                if(matched!=null){
                    first=Math.min(first,matched);
                    last=Math.max(last,matched);
                }
            }
        }
        if(first>last){
            if(retryWholeForHighlight(gesture,token,"no_roi_match"))return;
            if(!NativeCircleIntentRouter.permitsImageFallback(gesture.kind.name()))
                rejectTextGesture(gesture,token,"no_character_match");
            else resolveImage(gesture,token);
            return;
        }
        selectedLine=all.get(first).line();
        // Non-explicit initial selection cannot bridge unrelated icon words
        // and launcher captions merely due to document reading order.
        while(last>first && all.get(last).line()!=selectedLine)last--;
        firstChar=first;lastChar=last;
        applyTextIndices(token,true);
        if (state.phase != NativeCircleState.Phase.TEXT_READY) {
            // Empty/invalid OCR text must not leave the workspace stuck in RESOLVING.
            if(!NativeCircleIntentRouter.permitsImageFallback(gesture.kind.name()))
                rejectTextGesture(gesture,token,"invalid_text");
            else resolveImage(gesture,token);
            return;
        }
        DiagnosticLog.i(app,"CIRCLE_NATIVE","auto route=text kind="+gesture.kind);
    }

    /** A failed highlight ROI gets at most one full-image retry. */
    private boolean retryWholeForHighlight(FLCircleSelection.Selection gesture,
            long token,String reason){
        if(gesture.kind!=FLCircleSelection.Kind.HIGHLIGHT||
                recognizedRoi==null||recognizedRoi.left!=0||recognizedRoi.top!=0||
                recognizedRoi.width()==frame.bitmap.getWidth()&&
                recognizedRoi.height()==frame.bitmap.getHeight())return false;
        pendingGesture=gesture;
        fullDocument=null;sourceSpans.clear();cachedChars=List.of();
        charOrdinals.clear();charIndex=null;
        DiagnosticLog.i(app,"PADDLE_SCHED","retry_full=1 reason="+reason);
        startOcr(null,false);
        return true;
    }

    /** Build source-position maps once after Paddle returns a full document. */
    private void rebuildTextOffsets(){
        sourceSpans.clear();
        cachedChars=fullDocument==null?List.of():fullDocument.chars();
        charOrdinals.clear();
        for(int i=0;i<cachedChars.size();i++)
            charOrdinals.put(cachedChars.get(i),i);
        charIndex=cachedChars.isEmpty()?null:new CircleCharSpatialIndex(cachedChars);
        if(fullDocument==null)return;
        int mapped=0,unmapped=0;
        List<OcrDocument.Line> lines=fullDocument.lines();
        for(int lineId=0;lineId<lines.size();lineId++){
            OcrDocument.Line line=lines.get(lineId);
            ArrayList<OcrDocument.CharUnit> units=new ArrayList<>();
            ArrayList<String> tokens=new ArrayList<>();
            for(OcrDocument.CharUnit unit:line.chars()){
                if(unit.text().isBlank())continue;
                units.add(unit);
                tokens.add(unit.text());
            }
            List<OcrSelectionText.Span> aligned=OcrSelectionText.align(
                    line.text(),tokens,lineId);
            for(int i=0;i<units.size();i++){
                OcrSelectionText.Span offset=aligned.get(i);
                if(offset!=null){sourceSpans.put(units.get(i),offset);mapped++;}
                else unmapped++;
            }
        }
        DiagnosticLog.i(app,"PADDLE_SPACE_TRACE",
                "stage=native_index mapped="+mapped+" unmapped="+unmapped+
                " lines="+lines.size());
    }

    /** Text is copied from recognized line offsets, NEVER inferred from glyph spacing. */
    private void applyTextIndices(long token,boolean initial) {
        if(fullDocument==null||firstChar<0||lastChar<firstChar)return;
        List<OcrDocument.CharUnit> all=fullDocument.chars();
        if(lastChar>=all.size())return;
        chosenChars.clear();
        ArrayList<String> tokens=new ArrayList<>();
        ArrayList<OcrSelectionText.Span> offsets=new ArrayList<>();
        ArrayList<Integer> indices=new ArrayList<>();
        ArrayList<Integer> lineNumbers=new ArrayList<>();
        Rect screen=new Rect();
        int mapped=0;
        for(int i=firstChar;i<=lastChar;i++){
            OcrDocument.CharUnit unit=all.get(i);
            if(unit.text().isBlank()||unit.bounds().isEmpty())continue;
            if(!CircleTextRangePolicy.sameLine(unit.line(),selectedLine))continue;
            OcrSelectionText.Span span=sourceSpans.get(unit);
            if(span!=null)mapped++;
            tokens.add(unit.text());
            offsets.add(span);
            lineNumbers.add(unit.line());
            indices.add(indices.size());
            screen.union(unit.bounds());
            chosenChars.add(unit);
        }
        String value=OcrSelectionText.selected(tokens,offsets,indices,lineNumbers);
        if(value.isBlank()||screen.isEmpty())return;
        int spaces=0;
        for(int cp:value.codePoints().toArray()){
            if(cp==32||cp==0x00a0||cp==0x3000||cp==0x202f)spaces++;
        }
        DiagnosticLog.i(app,"PADDLE_SPACE_TRACE",
                "stage=native_selection spaces="+spaces+
                " units="+tokens.size()+" mapped="+mapped+
                " unmapped="+(tokens.size()-mapped)+
                " codepoints="+value.codePointCount(0,value.length())+
                " initial="+initial);
        Rect inBitmap=frame.screenRectToBitmap(screen);
        if(initial){
            if(!state.resolveText(token,value,inBitmap.left,inBitmap.top,
                    inBitmap.right,inBitmap.bottom))return;
        }else{
            state.updateText(value,inBitmap.left,inBitmap.top,
                    inBitmap.right,inBitmap.bottom);
        }
        status="";
        invalidate();
        syncPopup();
    }

    private void rejectTextGesture(FLCircleSelection.Selection gesture,
                                   long token,String reason) {
        if (closed || gesture==null
                || gesture.kind!=FLCircleSelection.Kind.HIGHLIGHT
                || state.phase!=NativeCircleState.Phase.RESOLVING
                || state.revision!=token)return;
        pendingGesture=null;
        if(activeOcr!=null){activeOcr.cancel();activeOcr=null;ocrBusy=false;}
        awaitingTextPath.reset();
        chosenChars.clear();
        firstChar=lastChar=-1;selectedLine=-1;
        state.clear();
        status=t("没有识别到划线文字，请重新划线",
                 "No highlighted text found; try drawing the line again");
        DiagnosticLog.i(app,"CIRCLE_NATIVE",
                "highlight unresolved reason="+reason+" no_image_fallback=true");
        invalidate();
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
        chosenChars.clear(); firstChar=lastChar=-1;selectedLine=-1;
        // Image intent always resolves to a rectangle with four resize handles.
        pendingGesture=null;
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
        gestureRevision=state.revision;
        pendingGesture=gesture;
        awaitingTextPath.reset();
        if(gesture.kind==FLCircleSelection.Kind.HIGHLIGHT)
            awaitingTextPath.set(dragPath);
        // Keep the real stroke visible until Paddle returns OCR units.
        routeResolvedGesture(gesture,gestureRevision);
        if (pendingGesture == gesture && ocrBusy) {
            long token = gestureRevision;
            // Local OCR often needs 9-12 seconds. A highlight must not turn
            // into an image crop solely because its recognition is slow.
            boolean highlight=!NativeCircleIntentRouter.permitsImageFallback(gesture.kind.name());
            MAIN.postDelayed(() -> {
                if (closed || state.phase != NativeCircleState.Phase.RESOLVING
                        || state.revision != token || pendingGesture != gesture) return;
                if (highlight) {
                    rejectTextGesture(gesture,token,"ocr_timeout");
                } else {
                    DiagnosticLog.i(app,"CIRCLE_NATIVE",
                            "tap OCR timeout; falling back to image crop");
                    pendingGesture=null;
                    if(activeOcr!=null){activeOcr.cancel();activeOcr=null;ocrBusy=false;}
                    resolveImage(gesture,token);
                }
            }, highlight?25000L:4000L);
        }
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

    private void openWeb(String url){
        try {
            app.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch(Throwable failure){traceError("web",failure);}
    }
    private static String encode(String text){
        return URLEncoder.encode(text,StandardCharsets.UTF_8);
    }
    private void traceError(String action,Throwable error){
        DiagnosticLog.i(app,"CIRCLE_NATIVE",
                action+" failed="+ScreenCaptureBackend.safeMessage(error));
    }
    private void resetSelection(){
        FloatActionMenu.dismiss();
        state.clear();chosenChars.clear();
        firstChar=lastChar=-1;selectedLine=-1;pendingGesture=null;status="";
        awaitingTextPath.reset();
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
            if(state.phase==NativeCircleState.Phase.TEXT_READY){
                if(firstHandle.contains(x,y)){handleDrag=1;return true;}
                if(lastHandle.contains(x,y)){handleDrag=2;return true;}
            }
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
            awaitingTextPath.reset();
            chosenChars.clear();firstChar=lastChar=-1;
            pendingGesture=null;status="";
            invalidate();return true;
        }
        if(handleDrag!=0){
            if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
                if(handleDrag<=2)adjustTextHandle(x,y);
                else adjustImageHandle(x,y);
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

    private void adjustTextHandle(float x,float y){
        if(fullDocument==null||charIndex==null)return;
        List<OcrDocument.CharUnit> all=cachedChars;
        if(all.isEmpty())return;
        PointF bitmap=frame.viewToBitmap(x,y,getWidth(),getHeight());
        PointF screen=frame.bitmapPointToScreen(bitmap.x,bitmap.y);
        int candidate=charIndex.nearest(screen.x,screen.y);
        if(candidate<0)return;
        if(!CircleTextRangePolicy.sameLine(all.get(candidate).line(),selectedLine)){
            // Dragging over another row must not snap to its unrelated
            // caption. Clamp to the closest source glyph in the same line.
            float best=Float.POSITIVE_INFINITY;
            candidate=-1;
            for(int i=0;i<all.size();i++){
                if(all.get(i).line()!=selectedLine)continue;
                Rect b=all.get(i).bounds();
                float dx=b.exactCenterX()-screen.x;
                float dy=b.exactCenterY()-screen.y;
                float distance=dx*dx+dy*dy;
                if(distance<best){best=distance;candidate=i;}
            }
            if(candidate<0)return;
        }
        if(handleDrag==1)firstChar=Math.min(candidate,lastChar);
        else lastChar=Math.max(candidate,firstChar);
        applyTextIndices(state.revision,false);
    }

    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_BACK){
            dismissActive("back");return true;
        }
        return super.onKeyDown(code,event);
    }

    private void close(String reason) {
        if(closed)return;
        closed=true;state.close();pendingGesture=null;
        if(activeOcr!=null)activeOcr.cancel();
        cachedChars=List.of();charIndex=null;
        charOrdinals.clear();
        awaitingTextPath.reset();
        sourceSpans.clear();
        FloatActionMenu.dismiss();
        host.remove(this,"native_circle_"+reason);
        if(closedCallback!=null)closedCallback.run();
        DiagnosticLog.i(app,"CIRCLE_NATIVE","closed "+reason);
    }
}
