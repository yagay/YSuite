package com.yagay.YFloat.hook;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/** Builds per-row screen outlines from Google's selected OCR word boxes. */
final class GoogleWordLineGeometry {
    static int[] projectRows(List<Rect> words, int width, int height,
                             int viewX, int viewY, Rect stableViewport) {
        if(words==null||words.isEmpty())return null;
        int[] packed=new int[Math.min(128,words.size())*4];
        int n=0;
        for(Rect word:words){
            if(n>=packed.length)break;
            if(word==null||word.isEmpty())continue;
            GoogleSelectionCoordinateSpace.Result mapped =
                    GoogleSelectionCoordinateSpace.map(word,width,height,
                            viewX,viewY,stableViewport);
            Rect r=mapped.bounds;
            if(r==null||r.isEmpty())continue;
            packed[n++]=r.left;packed[n++]=r.top;
            packed[n++]=r.right;packed[n++]=r.bottom;
        }
        return n==0?null:mergeProjected(java.util.Arrays.copyOf(packed,n));
    }

    /** Pure integer geometry, independently testable without Google or Android Views. */
    static int[] mergeProjected(int[] packed) {
        if(packed==null||packed.length<4||packed.length%4!=0)return null;
        List<int[]> words=new ArrayList<>();
        for(int i=0;i<packed.length&&words.size()<128;i+=4)
            if(packed[i+2]>packed[i]&&packed[i+3]>packed[i+1])
                words.add(new int[]{packed[i],packed[i+1],packed[i+2],packed[i+3]});
        words.sort((a,b)->a[1]==b[1]
                ?Integer.compare(a[0],b[0]):Integer.compare(a[1],b[1]));
        List<int[]> rows=new ArrayList<>();
        for(int[] w:words){
            int candidate=-1;
            for(int i=0;i<rows.size();i++){
                int[] row=rows.get(i);
                int overlap=Math.min(row[3],w[3])-Math.max(row[1],w[1]);
                int minHeight=Math.min(row[3]-row[1],w[3]-w[1]);
                int gap=Math.max(0,Math.max(w[0]-row[2],row[0]-w[2]));
                if(overlap>=Math.max(1,(minHeight+1)/2)
                        &&gap<=Math.max(16,2*Math.max(minHeight,row[3]-row[1]))){
                    candidate=i;break;
                }
            }
            if(candidate<0)rows.add(w.clone());
            else{
                int[] row=rows.get(candidate);
                row[0]=Math.min(row[0],w[0]); row[1]=Math.min(row[1],w[1]);
                row[2]=Math.max(row[2],w[2]); row[3]=Math.max(row[3],w[3]);
            }
        }
        rows.sort((a,b)->a[1]==b[1]
                ?Integer.compare(a[0],b[0]):Integer.compare(a[1],b[1]));
        int[] result=new int[rows.size()*4];
        for(int i=0;i<rows.size();i++)
            System.arraycopy(rows.get(i),0,result,i*4,4);
        return result.length==0?null:result;
    }
    private GoogleWordLineGeometry(){}
}
