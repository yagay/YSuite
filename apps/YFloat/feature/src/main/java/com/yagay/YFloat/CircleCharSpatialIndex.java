package com.yagay.YFloat;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Immutable 2D text hit index in SCREEN coordinates; no allocation on drag. */
final class CircleCharSpatialIndex {
    private static final int CELL=96;
    private final float[] xs,ys;
    private final HashMap<Long,int[]> grid=new HashMap<>();
    private static long key(int x,int y) {
        return ((long)x<<32)|(y&0xffffffffL);
    }
    CircleCharSpatialIndex(List<OcrDocument.CharUnit> chars){
        int n=chars==null?0:chars.size();
        xs=new float[n];ys=new float[n];
        for(int i=0;i<n;i++){
            Rect r=chars.get(i).bounds();
            xs[i]=r.exactCenterX();ys[i]=r.exactCenterY();
        }
        buildCells();
    }
    /** Geometry-only constructor for pure JVM regression tests. */
    CircleCharSpatialIndex(float[] centersX,float[] centersY){
        if(centersX==null||centersY==null||centersX.length!=centersY.length)
            throw new IllegalArgumentException("Mismatched centers");
        xs=centersX.clone();ys=centersY.clone();
        buildCells();
    }
    private void buildCells(){
        HashMap<Long,ArrayList<Integer>> temporary=new HashMap<>();
        for(int i=0;i<xs.length;i++){
            long k=key((int)Math.floor(xs[i]/CELL),
                    (int)Math.floor(ys[i]/CELL));
            temporary.computeIfAbsent(k,ignored->new ArrayList<>()).add(i);
        }
        temporary.forEach((k,v)->{
            int[] items=new int[v.size()];
            for(int i=0;i<v.size();i++)items[i]=v.get(i);
            grid.put(k,items);
        });
    }
    int nearest(float x,float y){
        if(xs.length==0)return -1;
        int cx=(int)Math.floor(x/CELL),cy=(int)Math.floor(y/CELL);
        int best=-1;
        float distance=Float.POSITIVE_INFINITY;
        // Usually finger movements remain near a line: inspect only the
        // 3x3 neighborhood, rather than allocating ~N Rects every frame.
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
            int[] items=grid.get(key(cx+dx,cy+dy));
            if(items==null)continue;
            for(int i:items){
                float ax=xs[i]-x,ay=ys[i]-y;
                float d=ax*ax+ay*ay;
                if(d<distance){distance=d;best=i;}
            }
        }
        if(best!=-1 && distance<=CELL*CELL)return best;
        // Off-text handle drags must still snap to the nearest glyph.
        for(int i=0;i<xs.length;i++){
            float ax=xs[i]-x,ay=ys[i]-y;
            float d=ax*ax+ay*ay;
            if(d<distance){distance=d;best=i;}
        }
        return best;
    }
}
