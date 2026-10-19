package com.yagay.YFloat;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * True visual selection topology, derived from ML Kit TextBlock/Line/Symbol
 * matched to Paddle CharUnits. Paddle is the ONLY text/clipboard authority.
 *
 * Important: never select OCR ordinals from unrelated launcher captions
 * merely because they happen to fall between two grip indices.
 * Independent components are selected only after the finger VISITS them.
 */
final class CircleMlBlockSelection {
    static final class Entry {
        final int index,paddleLine,mlLine,mlWord,mlSymbol;
        final String mlBlock;
        final float left,top,right,bottom;
        Entry(int index,int paddleLine,String mlBlock,int mlLine,
              int mlWord,int mlSymbol,float left,float top,float right,float bottom) {
            this.index=index;this.paddleLine=paddleLine;
            this.mlBlock=mlBlock==null?"":mlBlock;
            this.mlLine=mlLine;this.mlWord=mlWord;this.mlSymbol=mlSymbol;
            this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
        boolean valid(){return right>left&&bottom>top&&
                Float.isFinite(left)&&Float.isFinite(top)&&
                Float.isFinite(right)&&Float.isFinite(bottom);}
        float x(){return (left+right)*.5f;}
        float y(){return (top+bottom)*.5f;}
        float height(){return bottom-top;}
        float width(){return right-left;}
    }

    static final class Mask {
        final boolean[] selected;
        final int selectedCount,rejectedByBlock;
        final int touchedBlocks;
        Mask(boolean[] selected,int count,int rejected,int blocks){
            this.selected=selected;selectedCount=count;
            rejectedByBlock=rejected;touchedBlocks=blocks;
        }
        boolean contains(int index){
            return index>=0&&index<selected.length&&selected[index];
        }
    }

    private final List<Entry> entries;
    private final int[] component;
    private final int[] visualRank;
    private final int components;
    private final int mlSymbols;
    private CircleMlBlockSelection(List<Entry> entries){
        this.entries=List.copyOf(entries);
        int n=entries.size();
        component=new int[n];visualRank=new int[n];
        for(int i=0;i<n;i++)component[i]=i;
        int matched=0;
        // Spatial continuity is mandatory even when one ML TextBlock
        // mistakenly contains independent icon captions in different columns.
        for(int i=0;i<n;i++){
            Entry a=entries.get(i);
            if(!a.valid())continue;
            if(!a.mlBlock.isEmpty())matched++;
            for(int j=0;j<i;j++){
                Entry b=entries.get(j);
                if(b.valid()&&connected(a,b))union(i,j);
            }
        }
        mlSymbols=matched;
        // Capture ALL roots before rewriting union-find parent pointers to
        // public component IDs. Mixing those phases corrupts the forest.
        int[] roots=new int[n];
        for(int i=0;i<n;i++)
            roots[i]=entries.get(i).valid()?root(i):-1;
        HashMap<Integer,Integer> ids=new HashMap<>();
        int next=0;
        for(int i=0;i<n;i++){
            if(roots[i]<0){component[i]=-1;continue;}
            Integer id=ids.get(roots[i]);
            if(id==null){id=next++;ids.put(roots[i],id);}
            component[i]=id;
        }
        components=next;
        // ML kit's text position (screen line and symbol order) is now
        // authoritative for selection within a component, NOT Paddle's
        // global OCR index which interleaves launcher columns.
        HashMap<Integer,ArrayList<Integer>> byComponent=new HashMap<>();
        for(int i=0;i<n;i++){
            if(component[i]>=0)
                byComponent.computeIfAbsent(component[i],k->new ArrayList<>()).add(i);
        }
        for(ArrayList<Integer> indexes:byComponent.values()){
            // Stable row grouping, unlike a tolerance-based comparator that
            // can violate transitivity and throw from TimSort on large pages.
            indexes.sort(Comparator.comparingDouble((Integer i)->entries.get(i).y())
                    .thenComparingDouble(i->entries.get(i).x()));
            ArrayList<ArrayList<Integer>> rows=new ArrayList<>();
            float baseline=Float.NaN,rowHeight=0f;
            for(int i:indexes){
                Entry e=entries.get(i);
                if(rows.isEmpty() || Math.abs(e.y()-baseline)>
                        Math.min(rowHeight,e.height())*.48f){
                    rows.add(new ArrayList<>());
                    baseline=e.y();rowHeight=e.height();
                }
                rows.get(rows.size()-1).add(i);
            }
            int rank=0;
            for(ArrayList<Integer> row:rows){
                row.sort(Comparator.comparingDouble(i->entries.get(i).x()));
                for(int i:row)visualRank[i]=rank++;
            }
        }
    }

    static CircleMlBlockSelection build(OcrDocument paddle,
            CircleHybridSelectionGeometry.Alignment alignment){
        if(paddle==null)return new CircleMlBlockSelection(List.of());
        ArrayList<Entry> out=new ArrayList<>();
        List<OcrDocument.CharUnit> all=paddle.chars();
        for(int i=0;i<all.size();i++){
            OcrDocument.CharUnit c=all.get(i);
            CircleMlKitGeometry.Glyph g=alignment==null?null:alignment.glyph(c);
            Rect r=g!=null?g.bounds:c.bounds();
            if(r==null)r=new Rect();
            out.add(new Entry(i,c.line(),g==null?"":g.blockId,
                    g==null?-1:g.lineId,g==null?-1:g.wordId,
                    g==null?-1:g.symbolId,r.left,r.top,r.right,r.bottom));
        }
        return new CircleMlBlockSelection(out);
    }

    /** Pure constructor for regression testing, no recognizer or Android IO. */
    static CircleMlBlockSelection of(List<Entry> entries){
        return new CircleMlBlockSelection(entries);
    }

    private int root(int i){
        if(component[i]!=i)component[i]=root(component[i]);
        return component[i];
    }
    private void union(int a,int b){
        int ra=root(a),rb=root(b);
        if(ra!=rb)component[ra]=rb;
    }

    static boolean connected(Entry a,Entry b){
        if(!a.valid()||!b.valid())return false;
        float minH=Math.min(a.height(),b.height());
        float verticalOverlap=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
        float horizontalGap=Math.max(0f,Math.max(a.left,b.left)-Math.min(a.right,b.right));
        // Within the same OCR line, physically touching letters may have
        // been picked up by different recognition-script passes.
        if(verticalOverlap>=minH*.48f && horizontalGap<=Math.max(3f,minH*.55f)){
            if(!a.mlBlock.isEmpty()&&!b.mlBlock.isEmpty() &&
                    !a.mlBlock.equals(b.mlBlock)){
                // Different ML blocks must not fuse loosely adjacent icons,
                // but permit tightly touching multilingual word fragments.
                return horizontalGap<=Math.max(2f,minH*.12f);
            }
            return a.mlBlock.equals(b.mlBlock) || a.mlBlock.isEmpty()
                    || b.mlBlock.isEmpty();
        }
        // Consecutive lines belong together only inside ONE ML TextBlock,
        // and only when line spacing resembles a paragraph. Launcher rows
        // separated by large icon heights are independent components.
        if(a.mlBlock.isEmpty()||!a.mlBlock.equals(b.mlBlock))return false;
        float gap=Math.max(0f,Math.max(a.top,b.top)-Math.min(a.bottom,b.bottom));
        float xOverlap=Math.min(a.right,b.right)-Math.max(a.left,b.left);
        float leftDiff=Math.abs(a.left-b.left);
        return gap<=Math.max(4f,minH*1.1f)&&
                (xOverlap>0 || leftDiff<=minH*.9f);
    }

    int blockOf(int index){
        return index>=0&&index<component.length?component[index]:-1;
    }
    int mlMatchCount(){return mlSymbols;}
    int blockCount(){return components;}
    int size(){return entries.size();}

    /**
     * A true glyph hit, not a 96dp global nearest fallback that jumps to
     * unrelated desktop icons while the finger crosses empty screen space.
     */
    int hit(float x,float y,float radius){
        int best=-1;float closest=Float.POSITIVE_INFINITY;
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);
            if(!e.valid())continue;
            float dx=x<e.left?e.left-x:x>e.right?x-e.right:0f;
            float dy=y<e.top?e.top-y:y>e.bottom?y-e.bottom:0f;
            float distance=dx*dx+dy*dy*2f;
            float threshold=Math.max(radius,e.height()*.55f);
            if(distance>threshold*threshold)continue;
            if(distance<closest){closest=distance;best=i;}
        }
        return best;
    }

    /**
     * Initial ML Kit Symbol hit test. TAP uses the actual ML Element (word);
     * HIGHLIGHT uses only symbols physically touched by the stroke, limited
     * to the touched TextBlock component rather than Paddle's OCR span.
     * Positions are absolute SCREEN pixels, pairs [x,y].
     */
    int[] initialRange(float focusX,float focusY,List<float[]> stroke,
                       boolean tap,float tolerance){
        int pivot=hit(focusX,focusY,tolerance);
        if(pivot<0 && stroke!=null){
            for(float[] point:stroke){
                if(point==null||point.length<2)continue;
                pivot=hit(point[0],point[1],tolerance);
                if(pivot>=0)break;
            }
        }
        if(pivot<0)return null;
        Entry focus=entries.get(pivot);
        int block=component[pivot];
        int lo=pivot,hi=pivot;
        if(tap){
            if(!focus.mlBlock.isEmpty()&&focus.mlLine>=0&&focus.mlWord>=0){
                for(int i=0;i<entries.size();i++){
                    Entry e=entries.get(i);
                    if(component[i]==block && focus.mlBlock.equals(e.mlBlock)
                            && e.mlLine==focus.mlLine&&e.mlWord==focus.mlWord){
                        lo=Math.min(lo,i);hi=Math.max(hi,i);
                    }
                }
            }
        }else if(stroke!=null){
            // Bound selection by the smallest/largest touched VISUAL rank
            // of one ML component (not the global OCR order).
            int lower=visualRank[pivot],upper=lower;
            for(float[] point:stroke){
                if(point==null||point.length<2)continue;
                int i=hit(point[0],point[1],tolerance);
                if(i<0||component[i]!=block)continue;
                lower=Math.min(lower,visualRank[i]);
                upper=Math.max(upper,visualRank[i]);
            }
            for(int i=0;i<entries.size();i++){
                if(component[i]!=block)continue;
                if(visualRank[i]==lower)lo=i;
                if(visualRank[i]==upper)hi=i;
            }
        }
        return new int[]{Math.min(lo,hi),Math.max(lo,hi)};
    }

    /**
     * Blocks are intentionally non-contiguous in Paddle's document. A finger
     * stroke can visit more blocks, but no "intermediate" OCR ordinal is
     * automatically visited. Both handles' actual endpoint blocks are always
     * eligible. Within each component, use visual rank, not global OCR order.
     */
    Mask select(int first,int last,int lockedLine,boolean selectAll,
                Set<Integer> visited){
        boolean[] flags=new boolean[entries.size()];
        if(entries.isEmpty()||first<0||last<first||last>=entries.size()||
                blockOf(first)<0||blockOf(last)<0)
            return new Mask(flags,0,0,0);
        if(selectAll){
            int n=0;
            for(int i=0;i<entries.size();i++)
                if(entries.get(i).valid()){flags[i]=true;n++;}
            return new Mask(flags,n,0,components);
        }
        int a=blockOf(first),b=blockOf(last);
        Set<Integer> active=new HashSet<>();
        active.add(a);active.add(b);
        if(visited!=null)active.addAll(visited);
        // Initial taps/highlights cannot select a neighboring component
        // merely because a detection/recognition row interleaves its index.
        if(lockedLine>=0){
            active.clear();active.add(a);
        }
        int minRank=Math.min(visualRank[first],visualRank[last]);
        int maxRank=Math.max(visualRank[first],visualRank[last]);
        int count=0,rejected=0;
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);
            if(!e.valid())continue;
            int c=component[i];
            if(!active.contains(c)){if(i>=first&&i<=last)rejected++;continue;}
            if(lockedLine>=0&&e.paddleLine!=lockedLine)continue;
            int rank=visualRank[i];
            boolean inRange;
            if(a==b){
                inRange=c==a&&rank>=minRank&&rank<=maxRank;
            }else if(c==a){
                inRange=rank>=visualRank[first];
            }else if(c==b){
                inRange=rank<=visualRank[last];
            }else {
                inRange=true; // only an actual visited component
            }
            if(inRange){flags[i]=true;count++;}
        }
        return new Mask(flags,count,rejected,active.size());
    }
}
