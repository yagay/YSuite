package com.yagay.YFloat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/**
 * ML Kit-era Circle SELECTED-TEXT UI, independent of its OCR engine.
 *
 * Paddle is the only source of selected characters and copied text.
 * ML Kit optionally contributes matched SCREEN-space glyph positions.
 * We draw the original main-branch ML selection style directly on top
 * of the already drawn screenshot: #884285F4 rounded 2dp rects, 7dp
 * blue stem + 7dp circular selection handles. No screenshot repaint,
 * inverted clipping, artificial text rendering or font reconstruction.
 *
 * All boxes are immutable SCREEN coordinates until the draw boundary.
 */
final class CircleMlSelectionVisual {
    static final int ML_BLUE_WASH = 0x884285F4;
    static final int ML_BLUE_HANDLE = 0xFF4285F4;
    static final float CORNER_DP = 2f;
    static final float HANDLE_STEM_DP = 7f;
    static final float HANDLE_RADIUS_DP = 7f;

    static final class Layout {
        final List<RectF> segments;
        final RectF first;
        final RectF last;
        final RectF bounds;
        final int symbolCount;
        final int mlMatched;
        Layout(List<RectF> segments,RectF first,RectF last,RectF bounds,
               int symbolCount,int mlMatched){
            ArrayList<RectF> copies=new ArrayList<>(segments.size());
            for(RectF rect:segments)copies.add(new RectF(rect));
            this.segments=List.copyOf(copies);
            this.first=first==null?new RectF():new RectF(first);
            this.last=last==null?new RectF():new RectF(last);
            this.bounds=bounds==null?new RectF():new RectF(bounds);
            this.symbolCount=symbolCount;
            this.mlMatched=mlMatched;
        }
        boolean isEmpty(){return segments.isEmpty();}
        static Layout empty(){return new Layout(List.of(),null,null,null,0,0);}
    }

    static final class Handles {
        final PointF start,end;
        Handles(PointF start,PointF end){
            this.start=start;this.end=end;
        }
    }

    private CircleMlSelectionVisual(){}

    /**
     * Port of main/FLCircleInlineOverlay's visual selection grouping:
     * draw one rounded box per contiguous word/selection segment.
     *
     * Unlike the old whole-row union, segments only include explicitly
     * selected characters, and cannot cross a large visual gap, source
     * group break, reading-order discontinuity or OCR line boundary.
     */
    static Layout build(List<OcrDocument.CharUnit> selected,
                        CircleHybridSelectionGeometry.Alignment alignment,
                        boolean useMl){
        if(selected==null||selected.isEmpty())return Layout.empty();
        ArrayList<RectF> boxes=new ArrayList<>();
        RectF segment=null,prevRect=null,first=null,last=null,union=null;
        OcrDocument.CharUnit prev=null;
        int count=0,matched=0;
        for(OcrDocument.CharUnit glyph:selected){
            if(glyph==null||glyph.text().isBlank())continue;
            List<PointF> ml=useMl&&alignment!=null?alignment.quad(glyph):List.of();
            RectF r=box(ml);
            boolean usedMl=!r.isEmpty();
            if(!usedMl)r=box(glyph.quad());
            if(r.isEmpty()){
                Rect source=glyph.bounds();
                if(source.isEmpty())continue;
                r=new RectF(source);
            }
            if(!finite(r)||r.width()<.5f||r.height()<.5f)continue;
            if(first==null)first=new RectF(r);
            last=new RectF(r);
            if(union==null)union=new RectF(r);else union.union(r);
            if(segment==null){
                segment=new RectF(r);
            }else if(mayMerge(prev!=null
                                &&prev.line()==glyph.line()
                                &&prev.group()==glyph.group(),
                    prev!=null && glyph.order()==prev.order()+1,
                    r.left-prevRect.right,
                    Math.min(prevRect.bottom,r.bottom)
                            -Math.max(prevRect.top,r.top),
                    Math.min(prevRect.height(),r.height()),
                    r.left,prevRect.left,r.right,prevRect.right)){
                segment.union(r);
            }else{
                boxes.add(segment);
                segment=new RectF(r);
            }
            prevRect=r;prev=glyph;count++;
            if(usedMl)matched++;
        }
        if(segment!=null)boxes.add(segment);
        return new Layout(boxes,first,last,union,count,matched);
    }

    /** Pairwise visual joining rule, tested without Android graphics runtime. */
    static boolean mayMerge(boolean sameGroup,boolean consecutive,
                            float horizontalGap,float verticalOverlap,
                            float smallerHeight,
                            float nextLeft,float oldLeft,
                            float nextRight,float oldRight){
        if(!sameGroup||!consecutive||smallerHeight<=0f)return false;
        if(verticalOverlap<smallerHeight*.55f)return false;
        if(horizontalGap>Math.max(3f,smallerHeight*.45f))return false;
        // Do not join misplaced duplicates that extend far behind an earlier
        // glyph; they must remain separately visible, never one giant block.
        return nextLeft>=oldLeft-smallerHeight*.35f &&
                nextRight>=oldRight-smallerHeight*.35f;
    }

    static RectF box(List<PointF> quad){
        if(quad==null||quad.size()!=4)return new RectF();
        float l=Float.POSITIVE_INFINITY,t=Float.POSITIVE_INFINITY;
        float r=Float.NEGATIVE_INFINITY,b=Float.NEGATIVE_INFINITY;
        for(PointF point:quad){
            if(point==null||!Float.isFinite(point.x)||!Float.isFinite(point.y))
                return new RectF();
            l=Math.min(l,point.x);t=Math.min(t,point.y);
            r=Math.max(r,point.x);b=Math.max(b,point.y);
        }
        return new RectF(l,t,r,b);
    }

    private static boolean finite(RectF r){
        return Float.isFinite(r.left)&&Float.isFinite(r.top)
                &&Float.isFinite(r.right)&&Float.isFinite(r.bottom);
    }

    /**
     * Exactly the main branch's paint style. The screenshot has already
     * been drawn by NativeCircleWorkspace.onDraw; only draw color/handles.
     */
    static Handles draw(Canvas canvas,Layout layout,
                        ScreenBitmapTransform transform,int width,int height,
                        float density,Paint selectedPaint,Paint gripPaint){
        if(canvas==null||layout==null||layout.isEmpty()||transform==null)
            return null;
        float corner=CORNER_DP*density;
        for(RectF rect:layout.segments){
            RectF view=transform.screenToView(rect,width,height);
            if(!view.isEmpty())
                canvas.drawRoundRect(view,corner,corner,selectedPaint);
        }
        RectF start=transform.screenToView(layout.first,width,height);
        RectF end=transform.screenToView(layout.last,width,height);
        if(start.isEmpty()||end.isEmpty())return null;
        float stem=HANDLE_STEM_DP*density;
        float radius=HANDLE_RADIUS_DP*density;
        canvas.drawLine(start.left,start.bottom,start.left,start.bottom+stem,gripPaint);
        canvas.drawCircle(start.left,start.bottom+stem,radius,gripPaint);
        canvas.drawLine(end.right,end.bottom,end.right,end.bottom+stem,gripPaint);
        canvas.drawCircle(end.right,end.bottom+stem,radius,gripPaint);
        return new Handles(new PointF(start.left,start.bottom+stem),
                new PointF(end.right,end.bottom+stem));
    }

    static void drawCompare(Canvas canvas,Layout paddle,
                            ScreenBitmapTransform transform,int width,int height,
                            float density,Paint compareOutline){
        if(paddle==null||paddle.isEmpty())return;
        float corner=CORNER_DP*density;
        for(RectF rect:paddle.segments){
            RectF view=transform.screenToView(rect,width,height);
            if(!view.isEmpty())canvas.drawRoundRect(view,corner,corner,compareOutline);
        }
    }

    static RectF selectedViewBox(Layout layout,ScreenBitmapTransform transform,
                                 int width,int height){
        return layout==null||layout.isEmpty()?new RectF()
                :transform.screenToView(layout.bounds,width,height);
    }
}
