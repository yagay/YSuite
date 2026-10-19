package com.yagay.YFloat;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One OCR text owner (Paddle), two optional geometry providers. Never replace
 * Paddle characters with ML Kit recognition text. A ML symbol needs matching
 * Unicode AND strongly local geometry to override Paddle's CTC estimate.
 */
final class CircleHybridSelectionGeometry {
    static final int PADDLE=0, HYBRID=1, COMPARE=2;

    static final class Alignment {
        private final IdentityHashMap<OcrDocument.CharUnit,List<PointF>> mapped;
        final int matched,total;
        Alignment(IdentityHashMap<OcrDocument.CharUnit,List<PointF>> map,
                  int matched,int total){
            mapped=map;this.matched=matched;this.total=total;
        }
        List<PointF> quad(OcrDocument.CharUnit c){
            List<PointF> points=mapped.get(c);
            return points==null?List.of():points;
        }
    }
    private CircleHybridSelectionGeometry(){}

    static Alignment align(OcrDocument paddle,List<CircleMlKitGeometry.Glyph> ml){
        IdentityHashMap<OcrDocument.CharUnit,List<PointF>> out=new IdentityHashMap<>();
        if(paddle==null||ml==null||ml.isEmpty())
            return new Alignment(out,0,paddle==null?0:paddle.chars().size());
        boolean[] used=new boolean[ml.size()];
        int matched=0;
        List<OcrDocument.CharUnit> source=paddle.chars();
        for(OcrDocument.CharUnit c:source){
            String expected=compact(c.text());
            if(expected.isEmpty()||expected.codePointCount(0,expected.length())!=1)
                continue;
            Rect a=c.bounds();
            if(a.isEmpty())continue;
            int best=-1;
            float lowest=Float.POSITIVE_INFINITY;
            for(int j=0;j<ml.size();j++){
                if(used[j])continue;
                CircleMlKitGeometry.Glyph candidate=ml.get(j);
                if(!expected.equals(compact(candidate.text)))continue;
                Rect b=candidate.bounds;
                if(b.isEmpty()||candidate.corners.size()!=4)continue;
                float score=matchScore(a,b);
                if(score<lowest){lowest=score;best=j;}
            }
            if(best>=0 && lowest<=5.2f){
                used[best]=true;
                out.put(c,ml.get(best).corners);
                matched++;
            }
        }
        return new Alignment(out,matched,source.size());
    }

    private static String compact(String src){
        return Normalizer.normalize(src==null?"":src,Normalizer.Form.NFC)
                .replaceAll("\\s+","");
    }

    /** Public within package for deterministic matching regression tests. */
    static float matchScore(Rect a,Rect b){
        if(a==null||b==null||a.isEmpty()||b.isEmpty())
            return Float.POSITIVE_INFINITY;
        return matchScore(a.exactCenterX(),a.exactCenterY(),a.width(),a.height(),
                b.exactCenterX(),b.exactCenterY(),b.width(),b.height());
    }

    /** Pure-number twin for deterministic JVM regression tests. */
    static float matchScore(float ax,float ay,float aw,float ah,
                            float bx,float by,float bw,float bh){
        if(aw<=0||ah<=0||bw<=0||bh<=0)return Float.POSITIVE_INFINITY;
        float scale=Math.max(5f,Math.max(ah,bh));
        float dx=bx-ax,dy=by-ay;
        float widthRatio=bw/aw,heightRatio=bh/ah;
        if(widthRatio<0.24f||widthRatio>4.0f||
                heightRatio<0.35f||heightRatio>2.9f)
            return Float.POSITIVE_INFINITY;
        if(Math.abs(dy)>scale*1.3f || Math.abs(dx)>scale*2.25f)
            return Float.POSITIVE_INFINITY;
        return (dx*dx+1.7f*dy*dy)/(scale*scale)
                +0.65f*(float)Math.abs(Math.log(widthRatio))
                +0.8f*(float)Math.abs(Math.log(heightRatio));
    }

    /**
     * Unlike V3, do not union all selected symbols on a line. Return a separate
     * blue quad for every contiguous Paddle group. Missing ML symbols fall
     * back to Paddle's ORIGINAL quads and remain selectable/copyable.
     */
    static List<TextGeometryV3.Region> grouped(OcrDocument document,
            List<OcrDocument.CharUnit> chosen,Alignment alignment,
            boolean useMl){
        if(document==null || !document.isScreenSpace() ||
                chosen==null || chosen.isEmpty())return List.of();
        ArrayList<TextGeometryV3.Region> out=new ArrayList<>();
        ArrayList<OcrDocument.CharUnit> group=new ArrayList<>();
        int line=Integer.MIN_VALUE,word=Integer.MIN_VALUE,lastOrder=-999;
        for(OcrDocument.CharUnit c:chosen){
            if(c==null||c.text().isBlank()||c.bounds().isEmpty())continue;
            boolean split=groupBreak(line,word,lastOrder,
                    c.line(),c.group(),c.order());
            if(split && !group.isEmpty()){
                add(out,group,alignment,useMl);
                group.clear();
            }
            line=c.line();word=c.group();lastOrder=c.order();
            group.add(c);
        }
        if(!group.isEmpty())add(out,group,alignment,useMl);
        return List.copyOf(out);
    }

    static boolean groupBreak(int lastLine,int lastGroup,int lastOrder,
                              int nextLine,int nextGroup,int nextOrder){
        return lastLine!=nextLine||lastGroup!=nextGroup||nextOrder!=lastOrder+1;
    }

    private static void add(List<TextGeometryV3.Region> out,
            List<OcrDocument.CharUnit> group,Alignment alignment,boolean useMl){
        ArrayList<PointF> points=new ArrayList<>();
        for(OcrDocument.CharUnit c:group){
            List<PointF> quad=useMl&&alignment!=null?alignment.quad(c):List.of();
            if(quad.size()!=4)quad=c.quad();
            if(quad.size()!=4){
                Rect r=c.bounds();
                quad=List.of(new PointF(r.left,r.top),
                        new PointF(r.right,r.top),
                        new PointF(r.right,r.bottom),
                        new PointF(r.left,r.bottom));
            }
            points.addAll(quad);
        }
        if(points.isEmpty())return;
        // Preserve orientation of the actual first character, not a
        // potentially oversized full-line detector box.
        PointF a=points.get(0),b=points.get(1);
        float ux=b.x-a.x,uy=b.y-a.y;
        float mag=(float)Math.hypot(ux,uy);
        if(mag<0.01f){ux=1f;uy=0f;}
        else {ux/=mag;uy/=mag;}
        float vx=-uy,vy=ux;
        float l=Float.POSITIVE_INFINITY,t=Float.POSITIVE_INFINITY;
        float r=Float.NEGATIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(PointF p:points){
            float u=p.x*ux+p.y*uy,v=p.x*vx+p.y*vy;
            l=Math.min(l,u);r=Math.max(r,u);
            t=Math.min(t,v);bottom=Math.max(bottom,v);
        }
        if(r-l<0.5f||bottom-t<0.5f)return;
        // Old ML Kit-style fill is exact group geometry, no full-row pad.
        out.add(new TextGeometryV3.Region(List.of(
                new PointF(l*ux+t*vx,l*uy+t*vy),
                new PointF(r*ux+t*vx,r*uy+t*vy),
                new PointF(r*ux+bottom*vx,r*uy+bottom*vy),
                new PointF(l*ux+bottom*vx,l*uy+bottom*vy)),false));
    }
}
