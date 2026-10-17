package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * V2's only text-frame presenter: a non-touchable outline at canonical screen
 * coordinates. It never mirrors the Google screenshot, shades the whole screen
 * or changes Google's native View transforms.
 */
final class GoogleV2TextOutline {
    private static ViewFrame frame;
    private static FlOverlayWindowHost host;

    static synchronized void show(Context context, Rect bounds) {
        show(context,bounds,null);
    }

    static synchronized void show(Context context, Rect bounds, int[] rows) {
        if (context == null || bounds == null || bounds.isEmpty()) return;
        if (frame != null && frame.getParent() != null) {
            frame.update(bounds, rows);
            return;
        }
        dismiss();
        Context app = context.getApplicationContext();
        FlOverlayWindowHost next = new FlOverlayWindowHost(app);
        ViewFrame view = new ViewFrame(app, bounds, rows);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        if(next.addStable(view, params, "google_v2_text_outline")){
            frame = view;
            host = next;
        }
    }

    static synchronized void dismiss(){
        ViewFrame prior=frame;
        FlOverlayWindowHost old=host;
        frame=null;host=null;
        if(prior!=null&&old!=null)old.remove(prior,"google_v2_text_outline");
    }

    private static final class ViewFrame extends View {
        final Rect bounds;
        Rect[] rows;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int[] origin = new int[2];
        final RectF outline = new RectF();
        final float radius;

        ViewFrame(Context app,Rect source,int[] packedRows){
            super(app);
            bounds=new Rect(source);
            rows=parseRows(source,packedRows);
            float density=Math.max(1f,getResources().getDisplayMetrics().density);
            radius=8f*density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f,YFloatOverlayStyle.selectionStroke(app)));
            paint.setColor(YFloatOverlayStyle.selectionColor(app));
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update(Rect source,int[] packedRows){
            bounds.set(source);
            rows=parseRows(source,packedRows);
            invalidate();
        }

        private static Rect[] parseRows(Rect source,int[] packed){
            if(packed==null||packed.length<4||packed.length>512||packed.length%4!=0)
                return new Rect[]{new Rect(source)};
            java.util.ArrayList<Rect> valid=new java.util.ArrayList<>();
            for(int i=0;i<packed.length;i+=4){
                Rect item=new Rect(packed[i],packed[i+1],packed[i+2],packed[i+3]);
                if(item.isEmpty()||!item.intersect(source)||item.isEmpty())continue;
                valid.add(item);
            }
            return valid.isEmpty()?new Rect[]{new Rect(source)}
                    :valid.toArray(new Rect[0]);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(bounds.isEmpty())return;
            getLocationOnScreen(origin);
            for(Rect row:rows){
                outline.set(row);
                outline.offset(-origin[0],-origin[1]);
                if(!outline.intersect(0,0,getWidth(),getHeight()))continue;
                if(!outline.isEmpty())canvas.drawRoundRect(outline,radius,radius,paint);
            }
        }
    }

    private GoogleV2TextOutline(){}
}
