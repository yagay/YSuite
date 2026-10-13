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
        if (context == null || bounds == null || bounds.isEmpty()) return;
        if (frame != null && frame.getParent() != null) {
            frame.bounds.set(bounds);
            frame.invalidate();
            return;
        }
        dismiss();
        Context app = context.getApplicationContext();
        FlOverlayWindowHost next = new FlOverlayWindowHost(app);
        ViewFrame view = new ViewFrame(app, bounds);
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
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int[] origin = new int[2];
        final RectF outline = new RectF();
        final float radius;

        ViewFrame(Context app,Rect source){
            super(app);
            bounds=new Rect(source);
            float density=Math.max(1f,getResources().getDisplayMetrics().density);
            radius=8f*density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f,YFloatOverlayStyle.selectionStroke(app)));
            paint.setColor(YFloatOverlayStyle.selectionColor(app));
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(bounds.isEmpty())return;
            getLocationOnScreen(origin);
            outline.set(bounds);
            outline.offset(-origin[0],-origin[1]);
            outline.intersect(0,0,getWidth(),getHeight());
            if(!outline.isEmpty())canvas.drawRoundRect(outline,radius,radius,paint);
        }
    }

    private GoogleV2TextOutline(){}
}
