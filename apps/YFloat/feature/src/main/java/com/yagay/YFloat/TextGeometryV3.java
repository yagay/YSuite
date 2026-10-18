package com.yagay.YFloat;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The single source of display geometry for native Circle OCR selections.
 * Paddle continues to own recognition/text offsets, while every selected
 * segment uses the original symbol quadrilaterals. No typeface reconstruction.
 *
 * Input and output coordinates are absolute SCREEN pixels. A caller maps the
 * resulting four corners to its View once at the drawing boundary.
 */
final class TextGeometryV3 {
    interface PixelRefiner {
        /** Return a confidently segmented SCREEN rectangle or null. */
        Rect refine(Rect glyphs,Rect detector);
    }

    static final class Region {
        private final List<PointF> corners;
        final boolean pixelCorrected;
        Region(List<PointF> points, boolean corrected) {
            if(points==null || points.size()!=4)throw new IllegalArgumentException("four corners");
            ArrayList<PointF> copy=new ArrayList<>(4);
            for(PointF p:points)copy.add(new PointF(p.x,p.y));
            corners=List.copyOf(copy);
            pixelCorrected=corrected;
        }
        List<PointF> corners() {
            ArrayList<PointF> copy=new ArrayList<>(4);
            for(PointF p:corners)copy.add(new PointF(p.x,p.y));
            return List.copyOf(copy);
        }
        RectF bounds() {
            float l=Float.POSITIVE_INFINITY,t=Float.POSITIVE_INFINITY;
            float r=Float.NEGATIVE_INFINITY,b=Float.NEGATIVE_INFINITY;
            for(PointF p:corners){
                l=Math.min(l,p.x);t=Math.min(t,p.y);
                r=Math.max(r,p.x);b=Math.max(b,p.y);
            }
            return new RectF(l,t,r,b);
        }
    }

    private TextGeometryV3(){ }

    static List<Region> build(OcrDocument document,List<OcrDocument.CharUnit> chosen,
                              PixelRefiner pixels) {
        if(document==null || !document.isScreenSpace() || chosen==null || chosen.isEmpty())
            return List.of();
        IdentityHashMap<OcrDocument.CharUnit,Boolean> selected=new IdentityHashMap<>();
        LinkedHashMap<Integer,List<OcrDocument.CharUnit>> byLine=new LinkedHashMap<>();
        for(OcrDocument.CharUnit c:chosen){
            if(c==null || c.text().isBlank() || c.bounds().isEmpty())continue;
            selected.put(c,Boolean.TRUE);
            byLine.computeIfAbsent(c.line(),key->new ArrayList<>()).add(c);
        }
        ArrayList<Region> out=new ArrayList<>();
        for(var entry:byLine.entrySet()){
            List<OcrDocument.CharUnit> units=entry.getValue();
            OcrDocument.Line row=null;
            for(OcrDocument.Line candidate:document.lines()){
                if(!candidate.chars().isEmpty()
                        && candidate.chars().get(0).line()==entry.getKey()){
                    row=candidate;break;
                }
            }
            boolean whole=row!=null;
            if(row!=null)for(OcrDocument.CharUnit c:row.chars()){
                if(c.text().isBlank()||c.bounds().isEmpty())continue;
                if(!selected.containsKey(c)){whole=false;break;}
            }
            // The original row's baseline provides the orientation, not its
            // expanded detector bounding rectangle. Symbol quads determine
            // the selected extents even for a one-character drag.
            List<PointF> rowQuad=row==null?List.of():row.quad();
            float dx=1f,dy=0f;
            if(rowQuad.size()==4) {
                dx=rowQuad.get(1).x-rowQuad.get(0).x;
                dy=rowQuad.get(1).y-rowQuad.get(0).y;
                float length=(float)Math.hypot(dx,dy);
                if(length>0.001f){dx/=length;dy/=length;}
                else {dx=1f;dy=0f;}
            }
            final float vx=-dy,vy=dx;
            float loU=Float.POSITIVE_INFINITY,hiU=Float.NEGATIVE_INFINITY;
            float loV=Float.POSITIVE_INFINITY,hiV=Float.NEGATIVE_INFINITY;
            Rect glyphRect=new Rect();
            boolean hasGeometry=false;
            for(OcrDocument.CharUnit c:units){
                Rect bounds=c.bounds();
                glyphRect.union(bounds);
                List<PointF> quad=c.quad();
                if(quad.size()!=4)quad=rectCorners(bounds);
                for(PointF p:quad){
                    float u=p.x*dx+p.y*dy;
                    float v=p.x*vx+p.y*vy;
                    loU=Math.min(loU,u);hiU=Math.max(hiU,u);
                    loV=Math.min(loV,v);hiV=Math.max(hiV,v);
                    hasGeometry=true;
                }
            }
            if(!hasGeometry || hiU-loU<1f || hiV-loV<1f)continue;
            // Preserve existing glyphs rather than inflate to the whole row.
            float pad=Math.max(0.5f,Math.min(1.5f,(hiV-loV)*0.015f));
            loU-=pad;hiU+=pad;loV-=pad;hiV+=pad;
            List<PointF> quad=orientedCorners(loU,hiU,loV,hiV,dx,dy,vx,vy);
            boolean corrected=false;

            // Pixel refinement is meaningful only when the entire detected
            // line was selected and it is nearly horizontal. It is never
            // allowed to reshape partial selections or rotated text.
            if(whole && pixels!=null && Math.abs(dy)<0.08f){
                try{
                    Rect detector=row.bounds();
                    Rect precise=pixels.refine(new Rect(glyphRect),detector);
                    if(precise!=null && !precise.isEmpty()){
                        float overlap=Math.max(0f,Math.min(precise.right,glyphRect.right)
                                -Math.max(precise.left,glyphRect.left))
                                * Math.max(0f,Math.min(precise.bottom,glyphRect.bottom)
                                -Math.max(precise.top,glyphRect.top));
                        float area=Math.max(1f,Math.min(
                                (float)precise.width()*precise.height(),
                                (float)glyphRect.width()*glyphRect.height()));
                        if(overlap/area>=0.55f){
                            quad=rectCorners(precise);
                            corrected=true;
                        }
                    }
                }catch(RuntimeException ignored){
                    // Corrupt/unsupported screenshot pixel formats must not
                    // interrupt the recognition, touch state, or action menu.
                }
            }
            if(!corrected && whole && Math.abs(dy)<0.08f && row!=null){
                // Conservative detector/CTC alignment fallback only; do not
                // allow an unrelated icon or subtitle into this region.
                Rect line=row.bounds();
                float[] band=NativeCircleVisualPolicy.textSelectionScreenBand(
                    glyphRect.left,glyphRect.top,glyphRect.right,glyphRect.bottom,
                    line.left,line.top,line.right,line.bottom,true);
                quad=rectCorners(new RectF(band[0],band[1],band[2],band[3]));
            }
            out.add(new Region(quad,corrected));
        }
        return List.copyOf(out);
    }

    private static List<PointF> orientedCorners(float l,float r,float t,float b,
                      float ux,float uy,float vx,float vy){
        return List.of(new PointF(l*ux+t*vx,l*uy+t*vy),
                new PointF(r*ux+t*vx,r*uy+t*vy),
                new PointF(r*ux+b*vx,r*uy+b*vy),
                new PointF(l*ux+b*vx,l*uy+b*vy));
    }
    private static List<PointF> rectCorners(Rect r) {
        return rectCorners(new RectF(r));
    }
    private static List<PointF> rectCorners(RectF r) {
        return List.of(new PointF(r.left,r.top),new PointF(r.right,r.top),
                new PointF(r.right,r.bottom),new PointF(r.left,r.bottom));
    }
}
