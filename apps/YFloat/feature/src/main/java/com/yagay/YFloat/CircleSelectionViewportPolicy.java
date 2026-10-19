package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Selection text, blue pixels and grips must use one visual membership mask.
 * OCR ordinal ranges alone are NOT proof that an intervening icon/column is
 * part of the selected text. Operates in immutable absolute screen geometry.
 */
final class CircleSelectionViewportPolicy {
    static final class Glyph {
        final int line;
        final float left,top,right,bottom;
        Glyph(int line,float left,float top,float right,float bottom){
            this.line=line;this.left=left;this.top=top;
            this.right=right;this.bottom=bottom;
        }
        float width(){return right-left;}
        float height(){return bottom-top;}
        float x(){return (left+right)*.5f;}
        float y(){return (top+bottom)*.5f;}
        boolean valid(){return right>left && bottom>top &&
                Float.isFinite(left)&&Float.isFinite(top)&&
                Float.isFinite(right)&&Float.isFinite(bottom);}
    }

    static final class Mask {
        final boolean[] selected;
        final int selectedCount;
        final int rejectedByGeometry;
        Mask(boolean[] selected,int count,int rejected){
            this.selected=selected;selectedCount=count;rejectedByGeometry=rejected;
        }
        boolean contains(int i){return i>=0&&i<selected.length&&selected[i];}
    }

    private static final class Band {
        float left,top,right,bottom;
        Band(Glyph g){left=g.left;right=g.right;top=g.top;bottom=g.bottom;}
        void include(Glyph g){
            left=Math.min(left,g.left);right=Math.max(right,g.right);
            top=Math.min(top,g.top);bottom=Math.max(bottom,g.bottom);
        }
    }

    private CircleSelectionViewportPolicy(){}

    /** Separate adjacent OCR icon labels sharing a detector line, by gap. */
    private static Band connectedBand(List<Glyph> chars,int anchor){
        Glyph focus=chars.get(anchor);
        Band band=new Band(focus);
        ArrayList<Integer> neighbors=new ArrayList<>();
        for(int i=0;i<chars.size();i++){
            Glyph g=chars.get(i);
            if(g.valid()&&g.line==focus.line)
                neighbors.add(i);
        }
        neighbors.sort(Comparator.comparingDouble(i->chars.get(i).left));
        int at=neighbors.indexOf(anchor);
        if(at<0)return band;
        for(int direction:new int[]{-1,1}){
            Glyph previous=focus;
            for(int i=at+direction;i>=0&&i<neighbors.size();i+=direction){
                Glyph next=chars.get(neighbors.get(i));
                float gap=direction<0?previous.left-next.right:
                        next.left-previous.right;
                float maxGap=Math.max(5f,Math.min(
                        previous.height(),next.height())*.65f);
                float overlap=Math.min(next.bottom,previous.bottom)-
                        Math.max(next.top,previous.top);
                if(gap>maxGap||overlap<Math.min(
                        next.height(),previous.height())*.2f)break;
                band.include(next);
                previous=next;
            }
        }
        return band;
    }

    static Mask select(List<Glyph> glyphs,int first,int last,
                       int lockedLine,boolean explicitAll){
        int size=glyphs==null?0:glyphs.size();
        boolean[] included=new boolean[size];
        if(size==0||first<0||last<first||last>=size)
            return new Mask(included,0,0);
        int count=0,rejected=0;
        Glyph start=glyphs.get(first),end=glyphs.get(last);
        if(!start.valid()||!end.valid())
            return new Mask(included,0,0);
        // Select All is explicit user intent and may span unrelated columns.
        if(explicitAll){
            for(int i=first;i<=last;i++){
                if(glyphs.get(i).valid()){included[i]=true;count++;}
            }
            return new Mask(included,count,0);
        }
        boolean sameLine=start.line==end.line;
        Band startBand=connectedBand(glyphs,first);
        Band endBand=connectedBand(glyphs,last);
        float left=sameLine?Math.min(start.left,end.left):
                Math.min(startBand.left,endBand.left);
        float right=sameLine?Math.max(start.right,end.right):
                Math.max(startBand.right,endBand.right);
        float glyphHeight=Math.max(4f,Math.min(start.height(),end.height()));
        float margin=Math.max(2f,Math.min(12f,glyphHeight*.25f));
        float top=Math.min(start.y(),end.y())-glyphHeight*.65f;
        float bottom=Math.max(start.y(),end.y())+glyphHeight*.65f;
        boolean forward=start.y()<=end.y();
        for(int i=first;i<=last;i++){
            Glyph g=glyphs.get(i);
            if(!g.valid())continue;
            if(lockedLine>=0&&g.line!=lockedLine)continue;
            if(i==first||i==last){
                included[i]=true;count++;
                continue;
            }
            // OCR order can interleave a different column or launcher icon.
            if(g.x()<left-margin||g.x()>right+margin||
                    g.y()<top-margin||g.y()>bottom+margin){
                rejected++;continue;
            }
            // Partial first/last rows stop at the actual endpoint glyph.
            if(!sameLine){
                if(g.line==start.line &&
                        (forward?g.x()<start.x()-margin:
                                 g.x()>start.x()+margin)){
                    rejected++;continue;
                }
                if(g.line==end.line &&
                        (forward?g.x()>end.x()+margin:
                                 g.x()<end.x()-margin)){
                    rejected++;continue;
                }
            }
            included[i]=true;count++;
        }
        return new Mask(included,count,rejected);
    }
}
