package com.yagay.YFloat;

import android.graphics.PointF;
import android.graphics.Rect;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

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
        // Avoid the O(Paddle * ML symbols) + repeated Unicode normalization
        // cost on main thread, especially on dense full-screen documents.
        java.util.HashMap<String,ArrayList<Integer>> byToken=new java.util.HashMap<>();
        for(int j=0;j<ml.size();j++){
            String token=compact(ml.get(j).text);
            if(!token.isEmpty())
                byToken.computeIfAbsent(token,ignored->new ArrayList<>()).add(j);
        }
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
            List<Integer> sameText=byToken.get(expected);
            if(sameText==null)continue;
            for(int j:sameText){
                if(used[j])continue;
                CircleMlKitGeometry.Glyph candidate=ml.get(j);
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

}
