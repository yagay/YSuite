package com.yagay.YFloat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
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
import java.util.List;
import java.util.Locale;

/**
 * New V3 Circle UI. The earlier Circle OCR and selection runtime is
 * completely outside this implementation.
 *
 * ML Kit owns text blocks/words/handles. Paddle owns ALL output text.
 * Image crop remains an independent screenshot operation.
 */
final class OcrV3Workspace extends View {
    private static OcrV3Workspace active;
    private static final int BLUE=0xFF4285F4, WASH=0x884285F4;
    private final Context app;
    private final FlOverlayWindowHost host;
    private final FLCircleCapture.Frame frame;
    private final Runnable onClosed;
    private final float density;
    private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF closeHit=new RectF();
    private OcrV3Page paddle;
    private List<OcrV3MlLayout.Word> mlWords=List.of();
    private OcrV3Selection selection;
    private boolean paddleFinished,mlFinished,closed,downOnHandle,textStroke;
    private int generation;
    private float downX,downY,lastX,lastY;
    private Rect imageSelection;
    private final ArrayList<PointF> strokePath=new ArrayList<>();
    private List<PointF> pendingPath;
    private String status;
    private final float[] tempPoint=new float[2];

    static synchronized boolean show(Context context,FLCircleCapture.Frame frame,
                                     Runnable onClosed){
        if(context==null||frame==null||frame.bitmap==null||frame.bitmap.isRecycled())
            return false;
        dismissActive("replaced");
        Context app=context.getApplicationContext();
        FloatActionMenu.dismiss();
        Rect display=ScreenGeometry.displayBounds(app);
        Rect bounds=frame.screenBounds;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                Math.max(1,bounds.width()),Math.max(1,bounds.height()),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        lp.x=bounds.left-display.left;lp.y=bounds.top-display.top;
        FlOverlayWindowHost host=new FlOverlayWindowHost(app);
        OcrV3Workspace view=new OcrV3Workspace(app,frame,host,onClosed);
        if(!host.addStable(view,lp,"native_circle_workspace"))return false;
        active=view;
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.start();
        return true;
    }
    static synchronized void dismissActive(String reason){
        OcrV3Workspace prior=active;
        active=null;
        if(prior!=null)prior.close(reason);
    }
    static synchronized void promoteFocus(String reason){
        if(active!=null&&!active.closed)active.requestFocus();
    }

    private OcrV3Workspace(Context app,FLCircleCapture.Frame frame,
                           FlOverlayWindowHost host,Runnable onClosed){
        super(app);
        this.app=app;this.frame=frame;this.host=host;this.onClosed=onClosed;
        density=getResources().getDisplayMetrics().density;
        fill.setColor(WASH);fill.setStyle(Paint.Style.FILL);
        border.setColor(BLUE);border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2*density);
        label.setTextSize(13*density);label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        status=tr("正在识别文字…","Recognizing text…");
        setFocusable(true);setClickable(true);
    }

    private static String tr(String chinese,String english){
        return Locale.getDefault().getLanguage().startsWith("zh")
                ?chinese:english;
    }
    private void start(){
        final int run=++generation;
        // Both engines have their own input copies and independent lifetimes.
        OcrV3MlLayout.scan(frame.bitmap, words->{
            if(closed||generation!=run)return;
            mlWords=words;mlFinished=true;
            DiagnosticLog.i(app,"OCR_V3",
                    "ml_words="+words.size()+" paddle_source_only=true");
            bindIfReady();
        });
        final Bitmap ocrCopy=frame.bitmap.copy(Bitmap.Config.ARGB_8888,false);
        if(ocrCopy==null){
            paddleFinished=true;
            status=tr("无法复制 OCR 截图","OCR image copy failed");
            invalidate();return;
        }
        OcrV3Paddle.recognize(app,ocrCopy,new OcrV3Paddle.Callback(){
            @Override public void success(OcrV3Page page){
                if(closed||generation!=run)return;
                paddle=page;paddleFinished=true;
                DiagnosticLog.i(app,"OCR_V3",
                        "paddle_lines="+page.lines.size()+
                        " paddle_glyphs="+page.glyphs.size()+
                        " ctc_sdk_chars="+page.sdkCharacters+
                        " ctc_source_aligned="+page.sourceAlignedCharacters+
                        " ctc_source_misses="+page.sourceUnmatchedCharacters+
                        " ctc_invalid_rects="+page.invalidGeometryCharacters+
                        " whole_line_fallbacks="+page.wholeLineFallbacks);
                bindIfReady();
            }
            @Override public void failure(Throwable error){
                if(closed||generation!=run)return;
                paddleFinished=true;
                status=tr("离线 OCR 不可用，请检查模型","Offline OCR unavailable; check model");
                DiagnosticLog.i(app,"OCR_V3","paddle_failed="+
                        ScreenCaptureBackend.safeMessage(error));
                invalidate();
            }
        });
    }
    private void bindIfReady(){
        if(!paddleFinished||!mlFinished||paddle==null)return;
        selection=new OcrV3Selection(paddle,mlWords);
        DiagnosticLog.i(app,"OCR_V3_ALIGNMENT",
                "ml_elements="+mlWords.size()+
                " paddle_glyphs="+paddle.glyphs.size()+
                " aligned_elements="+selection.size()+
                " mapped_paddle_glyphs="+selection.matchedGlyphs()+
                " unmatched_paddle_glyphs="+selection.unmatchedGlyphs()+
                " ml_elements_without_symbols="+selection.missingMlSymbols()+
                " copy_source=paddle_only");
        status=selection.size()==0
                ?tr("文字位置未对应，查看 OCR 诊断","Text geometry not aligned; see OCR diagnostics")
                :tr("点击文字选取，拖动手柄扩选","Tap text; drag handles to extend");
        if(pendingPath!=null){
            List<PointF> path=pendingPath;
            pendingPath=null;
            if(!selectPath(path)){
                if(path.size()==1){
                    selection.tap(path.get(0).x,path.get(0).y);
                }else{
                    imageSelection=bitmapCrop(path.get(0),
                            path.get(path.size()-1));
                }
            }
            if(selection.selected())imageSelection=null;
            syncPopup();
        }
        invalidate();
    }

    private float toolbarY(){
        float top=30f*density;
        WindowInsets insets=getRootWindowInsets();
        if(insets!=null)
            top=Math.max(top,insets.getInsets(WindowInsets.Type.statusBars()).top);
        return top+10*density;
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(closed||frame.bitmap.isRecycled())return;
        int w=getWidth(),h=getHeight();
        c.drawBitmap(frame.bitmap,null,new Rect(0,0,w,h),bitmapPaint);
        if(imageSelection!=null&&!imageSelection.isEmpty()){
            RectF r=frame.bitmapToView(new RectF(imageSelection),w,h);
            c.save();c.clipOutRect(r);
            c.drawColor(Color.argb(76,0,0,0));c.restore();
            c.drawRoundRect(r,4*density,4*density,border);
        }else if(selection!=null && selection.selected()){
            fill.setColor(WASH);
            for(Rect rect:selection.highlights()){
                RectF box=frame.bitmapToView(new RectF(rect),w,h);
                c.drawRoundRect(box,2*density,2*density,fill);
            }
            drawHandles(c);
        }
        drawToolbar(c);
        drawHint(c);
    }

    private void drawHandles(Canvas c){
        Rect a=selection.firstBox(),b=selection.lastBox();
        if(a.isEmpty()||b.isEmpty())return;
        RectF first=frame.bitmapToView(new RectF(a),getWidth(),getHeight());
        RectF last=frame.bitmapToView(new RectF(b),getWidth(),getHeight());
        fill.setColor(BLUE);
        float stem=7*density,rad=7*density;
        c.drawLine(first.left,first.bottom,first.left,first.bottom+stem,fill);
        c.drawCircle(first.left,first.bottom+stem,rad,fill);
        c.drawLine(last.right,last.bottom,last.right,last.bottom+stem,fill);
        c.drawCircle(last.right,last.bottom+stem,rad,fill);
        fill.setColor(WASH);
    }

    /** The close affordance is local; selected text/image actions use the
     * existing YUI FloatActionMenu positioned around the real selection. */
    private void drawToolbar(Canvas c){
        float top=toolbarY();
        closeHit.set(getWidth()-52*density,top,getWidth()-10*density,top+42*density);
        fill.setColor(0xE51F2733);
        c.drawRoundRect(closeHit,20*density,20*density,fill);
        label.setColor(Color.WHITE);label.setTextSize(22*density);
        c.drawText("×",closeHit.centerX(),closeHit.centerY()+7*density,label);
        fill.setColor(WASH);
    }
    private void drawHint(Canvas c){
        if(status==null||status.isBlank())return;
        float x=getWidth()/2f,y=getHeight()-38*density;
        float half=Math.min(getWidth()/2f-12*density,170*density);
        RectF r=new RectF(x-half,y-22*density,x+half,y+15*density);
        fill.setColor(0xDD222936);
        c.drawRoundRect(r,16*density,16*density,fill);
        label.setColor(Color.WHITE);label.setTextSize(12*density);
        c.drawText(status,x,y+2*density,label);
        fill.setColor(WASH);
    }
    private PointF bitmapPoint(float x,float y){
        return frame.viewToBitmap(x,y,getWidth(),getHeight());
    }
    private boolean hitHandle(float x,float y){
        if(selection==null||!selection.selected())return false;
        PointF p=bitmapPoint(x,y);
        PointF top=bitmapPoint(0f,0f);
        PointF stem=bitmapPoint(0f,7f*density);
        float bitmapStem=(float)Math.hypot(stem.x-top.x,stem.y-top.y);
        boolean captured=selection.begin(p.x,p.y,Math.max(18f,
                frame.transform.screenDistanceToBitmap(20f*density)),bitmapStem);
        if(captured)DiagnosticLog.i(app,"OCR_V3_HANDLE",
                "phase=begin side="+selection.movingHandle()+
                " range="+selection.first()+".."+selection.last()+
                " units="+selection.expansionUnits());
        return captured;
    }

    private float handleSnapRadius(){
        return Math.max(8f,frame.transform.screenDistanceToBitmap(28f*density));
    }

    private void moveHandle(MotionEvent e){
        if(selection==null)return;
        for(int i=0;i<e.getHistorySize();i++){
            PointF p=bitmapPoint(e.getHistoricalX(i),e.getHistoricalY(i));
            selection.drag(p.x,p.y,handleSnapRadius());
        }
        PointF p=bitmapPoint(e.getX(),e.getY());
        if(selection.drag(p.x,p.y,handleSnapRadius()))
            DiagnosticLog.i(app,"OCR_V3_HANDLE",
                    "phase=move first="+selection.first()+
                    " last="+selection.last()+
                    " side="+selection.movingHandle()+
                    " fine="+selection.expanded());
    }

    private float selectionPadding(){
        return Math.max(4f,frame.transform.screenDistanceToBitmap(10*density));
    }

    /** Collect physical motion history: the first valid ML word may be
     * crossed BETWEEN sparse MotionEvents, not at ACTION_DOWN or ACTION_UP. */
    private void recordPoint(float x,float y){
        PointF next=bitmapPoint(x,y);
        if(!strokePath.isEmpty()){
            PointF prior=strokePath.get(strokePath.size()-1);
            if(selection!=null&&!downOnHandle&&
                    selection.extendStrokeSegment(prior.x,prior.y,
                            next.x,next.y,selectionPadding())){
                textStroke=true;
                imageSelection=null;
            }
        }
        if(strokePath.size()<1000)strokePath.add(next);
    }

    private void recordMovement(MotionEvent e){
        for(int i=0;i<e.getHistorySize();i++)
            recordPoint(e.getHistoricalX(i),e.getHistoricalY(i));
        recordPoint(e.getX(),e.getY());
    }

    private boolean selectPath(List<PointF> path){
        if(selection==null||path==null||path.isEmpty())return false;
        if(path.size()==1)return selection.tap(path.get(0).x,path.get(0).y);
        for(int i=1;i<path.size();i++){
            PointF a=path.get(i-1),b=path.get(i);
            selection.extendStrokeSegment(a.x,a.y,b.x,b.y,selectionPadding());
        }
        return selection.selected();
    }

    private Rect bitmapCrop(PointF a,PointF b){
        int w=frame.bitmap.getWidth(),h=frame.bitmap.getHeight();
        int l=Math.max(0,Math.min(w-1,(int)Math.floor(Math.min(a.x,b.x))));
        int t=Math.max(0,Math.min(h-1,(int)Math.floor(Math.min(a.y,b.y))));
        int r=Math.max(l+1,Math.min(w,(int)Math.ceil(Math.max(a.x,b.x))));
        int bot=Math.max(t+1,Math.min(h,(int)Math.ceil(Math.max(a.y,b.y))));
        return new Rect(l,t,r,bot);
    }

    /** Shared YFloat menu at the selected words' absolute screen bounds. */
    private void syncPopup(){
        if(closed||downOnHandle)return;
        if(imageSelection!=null&&!imageSelection.isEmpty()){
            FloatActionMenu.showImageAt(app,
                    frame.bitmapRectToScreen(imageSelection),
                    true,this::handleImageAction);
            return;
        }
        if(selection==null||!selection.selected()){
            FloatActionMenu.dismiss();return;
        }
        String value=selection.text();
        if(value.isEmpty()){FloatActionMenu.dismiss();return;}
        Rect bounds=new Rect();
        for(Rect part:selection.highlights())bounds.union(part);
        if(bounds.isEmpty()){FloatActionMenu.dismiss();return;}
        Rect anchor=frame.bitmapRectToScreen(bounds);
        DiagnosticLog.i(app,"OCR_V3_MENU",
                "type=text chars="+value.length()+" anchor="+anchor.toShortString());
        FloatActionMenu.showTextAt(app,value,this::selectAllFromMenu,anchor);
    }

    private void selectAllFromMenu(){
        if(selection==null)return;
        selection.selectAll();
        imageSelection=null;
        invalidate();
        // The menu's Select All listener dismisses after invoking the callback.
        post(this::syncPopup);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(closed)return false;
        float x=e.getX(),y=e.getY();
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(closeHit.contains(x,y)){dismissActive("close_button");return true;}
            FloatActionMenu.dismiss();
            downOnHandle=imageSelection==null&&hitHandle(x,y);
            strokePath.clear();
            strokePath.add(bitmapPoint(x,y));
            downX=lastX=x;downY=lastY=y;
            textStroke=false;
            if(!downOnHandle){
                imageSelection=null;
                if(selection!=null){
                    PointF p=strokePath.get(0);
                    textStroke=selection.tap(p.x,p.y);
                    if(textStroke)status="";
                }
            }
            invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(downOnHandle&&selection!=null){
                moveHandle(e);
            }else{
                recordMovement(e);
                if(!textStroke&&Math.hypot(x-downX,y-downY)>18*density)
                    imageSelection=cropRect(downX,downY,x,y);
            }
            lastX=x;lastY=y;
            invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(downOnHandle){
                // ACTION_UP can contain the final movement; never discard it.
                if(selection!=null){
                    moveHandle(e);
                    DiagnosticLog.i(app,"OCR_V3_HANDLE",
                            "phase=end first="+selection.first()+
                            " last="+selection.last()+
                            " fine="+selection.expanded()+
                            " chars="+selection.text().length());
                    selection.release();
                }
                downOnHandle=false;
                syncPopup();invalidate();return true;
            }
            recordMovement(e);
            float distance=(float)Math.hypot(x-downX,y-downY);
            if(selection==null&&!paddleFinished&&!mlFinished){
                // OCR is asynchronous; never discard a stroke drawn while
                // the recognizers are still loading.
                pendingPath=List.copyOf(strokePath);
                imageSelection=null;
                invalidate();return true;
            }
            if(selection==null&&(paddle==null||!mlFinished)){
                pendingPath=List.copyOf(strokePath);
                imageSelection=null;
                invalidate();return true;
            }
            if(textStroke){
                imageSelection=null;
                if(selection!=null)DiagnosticLog.i(app,"OCR_V3_STROKE",
                        "selected="+selection.selected()+
                        " first="+selection.first()+" last="+selection.last()+
                        " points="+strokePath.size());
            }else if(distance<=18*density){
                imageSelection=null;
                PointF p=bitmapPoint(x,y);
                boolean found=selection!=null&&selection.tap(p.x,p.y);
                DiagnosticLog.i(app,"OCR_V3_TAP",
                        "matched="+found+" x="+(int)p.x+
                        " y="+(int)p.y+" selectable_elements="+
                        (selection==null?0:selection.size()));
                if(!found)status=tr("此位置没有可选择的文字",
                        "No selectable text at this position");
            }else{
                imageSelection=cropRect(downX,downY,x,y);
                if(selection!=null)selection.clear();
            }
            syncPopup();invalidate();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){
            if(downOnHandle&&selection!=null)selection.release();
            downOnHandle=false;textStroke=false;
            syncPopup();invalidate();return true;
        }
        return true;
    }

    private Rect cropRect(float x0,float y0,float x1,float y1){
        PointF a=bitmapPoint(x0,y0),b=bitmapPoint(x1,y1);
        int l=Math.max(0,Math.min(frame.bitmap.getWidth()-1,
                (int)Math.floor(Math.min(a.x,b.x))));
        int t=Math.max(0,Math.min(frame.bitmap.getHeight()-1,
                (int)Math.floor(Math.min(a.y,b.y))));
        int r=Math.max(l+1,Math.min(frame.bitmap.getWidth(),
                (int)Math.ceil(Math.max(a.x,b.x))));
        int bot=Math.max(t+1,Math.min(frame.bitmap.getHeight(),
                (int)Math.ceil(Math.max(a.y,b.y))));
        return new Rect(l,t,r,bot);
    }

    /** The same configurable image menu as the pre-V3 Circle workspace. */
    private void handleImageAction(String action){
        if(imageSelection==null||imageSelection.isEmpty())return;
        if(ImageMenuSettings.RESET.equals(action)){
            imageSelection=null;invalidate();return;
        }
        Bitmap clip=null;
        try{
            Rect chosen=new Rect(imageSelection);
            clip=Bitmap.createBitmap(frame.bitmap,
                    chosen.left,chosen.top,chosen.width(),chosen.height());
            if(clip==frame.bitmap)
                clip=frame.bitmap.copy(Bitmap.Config.ARGB_8888,false);
            if(clip==null)return;
            if(ImageMenuSettings.DONE.equals(action)){
                Rect screen=frame.bitmapRectToScreen(chosen);
                dismissActive("image_done");
                if(!ResultSurfaceRouter.showScreenshot(app,clip,screen))
                    clip.recycle();
                clip=null;return;
            }
            if(ImageMenuSettings.FIND.equals(action)||
                    ImageMenuSettings.SHARE.equals(action)||
                    ImageCustomMenuStore.isCustom(action))
                dismissActive("image_external_action");
            if(ImageMenuSettings.FIND.equals(action))
                ImageShareUtils.openWith(app,clip);
            else if(ImageMenuSettings.COPY.equals(action))
                ImageShareUtils.copyToClipboard(app,clip);
            else if(ImageMenuSettings.SHARE.equals(action))
                ImageShareUtils.share(app,clip);
            else if(ImageMenuSettings.SAVE.equals(action))
                ScreenshotController.save(app,clip);
            else if(ImageCustomMenuStore.isCustom(action))
                ImageCustomMenuStore.launch(app,action,clip);
        }catch(Throwable t){
            DiagnosticLog.i(app,"OCR_V3","image_action="+t);
        }finally{
            if(clip!=null&&!clip.isRecycled())clip.recycle();
        }
    }

    @Override public boolean onKeyDown(int code,KeyEvent e){
        if(code==KeyEvent.KEYCODE_BACK){dismissActive("back");return true;}
        return super.onKeyDown(code,e);
    }
    private void close(String reason){
        if(closed)return;
        closed=true;generation++;
        FloatActionMenu.dismiss();
        host.remove(this,"native_circle_workspace");
        try{onClosed.run();}catch(Throwable t){
            DiagnosticLog.i(app,"OCR_V3","close_callback="+t);
        }
        DiagnosticLog.i(app,"OCR_V3","closed="+reason);
    }
}
