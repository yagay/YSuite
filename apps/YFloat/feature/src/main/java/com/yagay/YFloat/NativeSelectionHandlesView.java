package com.yagay.YFloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * Independent, full-size, transparent overlay CHILD, not another window.
 * Touches outside the grips return false and remain available to the Circle
 * workspace. No selection rendering or OCR ownership is duplicated here.
 *
 * The asymmetric teardrop grip follows Android Editor's hotspot/gravity
 * principle without depending on hidden Editor/SelectionHandleView APIs.
 */
final class NativeSelectionHandlesView extends View {
    interface DragListener {
        void onBegin(int active);
        int onMove(int active,float caretX,float caretY);
        void onEnd();
    }
    private final Paint gripPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final DragListener listener;
    private TextCaretGeometry.Pair caret;
    private int active=SelectionDragController.NONE;
    private int pointerId=-1;
    private float fingerOffsetX,fingerOffsetY;

    NativeSelectionHandlesView(Context context,DragListener listener){
        super(context);
        this.listener=listener;
        density=context.getResources().getDisplayMetrics().density;
        gripPaint.setStyle(Paint.Style.FILL);
        gripPaint.setColor(YFloatOverlayStyle.selectionColor(context));
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void show(TextCaretGeometry.Pair next){
        caret=next;
        invalidate();
    }
    void clear(){
        caret=null;
        active=SelectionDragController.NONE;
        pointerId=-1;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(caret==null)return;
        drawGrip(canvas,caret.start,true);
        drawGrip(canvas,caret.end,false);
    }

    private void drawGrip(Canvas canvas,TextCaretGeometry.Caret c,boolean leading){
        canvas.save();
        canvas.translate(c.x,c.y);
        canvas.rotate(c.angleDegrees);
        float side=leading?-1f:1f;
        // Pixel-perfect hotspot (0,0) touches blue selection boundary.
        // The lobe sits below and to the left/right, without a long stem.
        Path shape=new Path();
        shape.moveTo(0f,0f);
        shape.lineTo(0f,7f*density);
        shape.cubicTo(0f,14f*density,side*2f*density,18f*density,
                side*10f*density,18f*density);
        shape.cubicTo(side*17f*density,18f*density,side*20f*density,13f*density,
                side*18f*density,8f*density);
        shape.cubicTo(side*16f*density,2f*density,side*6f*density,0f,0f,0f);
        shape.close();
        canvas.drawPath(shape,gripPaint);
        canvas.restore();
    }

    private float[] centre(TextCaretGeometry.Caret c,boolean leading){
        float sign=leading?-1f:1f;
        float ux=sign*10f*density,uy=10f*density;
        double angle=Math.toRadians(c.angleDegrees);
        return new float[]{c.x+(float)(ux*Math.cos(angle)-uy*Math.sin(angle)),
                c.y+(float)(ux*Math.sin(angle)+uy*Math.cos(angle))};
    }
    private int hit(float x,float y){
        if(caret==null)return SelectionDragController.NONE;
        float[] start=centre(caret.start,true),end=centre(caret.end,false);
        float d1=(x-start[0])*(x-start[0])+(y-start[1])*(y-start[1]);
        float d2=(x-end[0])*(x-end[0])+(y-end[1])*(y-end[1]);
        float touchRadius=24f*density;
        boolean h1=d1<=touchRadius*touchRadius,h2=d2<=touchRadius*touchRadius;
        if(!h1&&!h2)return SelectionDragController.NONE;
        return h1&&(!h2||d1<=d2)?SelectionDragController.START:SelectionDragController.END;
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            int chosen=hit(event.getX(),event.getY());
            if(chosen==SelectionDragController.NONE)return false;
            active=chosen;
            pointerId=event.getPointerId(0);
            TextCaretGeometry.Caret c=chosen==SelectionDragController.START
                    ?caret.start:caret.end;
            fingerOffsetX=event.getX()-c.x;
            fingerOffsetY=event.getY()-c.y;
            listener.onBegin(active);
            return true;
        }
        if(active==SelectionDragController.NONE)return false;
        if(action==MotionEvent.ACTION_POINTER_UP){
            if(event.getPointerId(event.getActionIndex())==pointerId)finish();
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_UP){
            int index=event.findPointerIndex(pointerId);
            if(index>=0){
                active=listener.onMove(active,event.getX(index)-fingerOffsetX,
                        event.getY(index)-fingerOffsetY);
            }
            if(action==MotionEvent.ACTION_UP)finish();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){finish();return true;}
        return true;
    }

    private void finish(){
        if(active==SelectionDragController.NONE)return;
        active=SelectionDragController.NONE;
        pointerId=-1;
        listener.onEnd();
    }
}
