package com.yagay.YFloat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One authority for text selection lifecycle. Indexes identify Paddle
 * CharUnits, but selection ORDER, hit testing, block membership and handle
 * crossing are delegated to the ML Kit spatial topology.
 *
 * The visited-block route is reversible: dragging back through a previously
 * visited block removes blocks beyond it. It is replayed by SCREEN coordinate
 * when asynchronous OCR/ML recognition rebuilds component identifiers.
 */
final class CircleMlSelectionSession {
    int first=-1,last=-1,lockedLine=-1;
    boolean selectAll;
    int movingHandle;
    private CircleMlBlockSelection topology;
    private final ArrayList<float[]> trace=new ArrayList<>();
    private final LinkedHashSet<Integer> visited=new LinkedHashSet<>();

    void reset(){
        first=last=lockedLine=-1;
        selectAll=false;movingHandle=0;
        trace.clear();visited.clear();
    }

    void initialize(int first,int last,int paddleLine){
        reset();
        this.first=first;this.last=last;lockedLine=paddleLine;
        normalizeEndpoints();
    }

    void chooseAll(int count){
        reset();
        if(count<=0)return;
        first=0;last=count-1;selectAll=true;
    }

    void bind(CircleMlBlockSelection model){
        topology=model;
        replayTrace();
        normalizeEndpoints();
    }

    /** Called after ROI -> full screenshot remap, keeps actual drag route. */
    void rebase(int first,int last){
        this.first=first;this.last=last;
        normalizeEndpoints();
        replayTrace();
    }

    private void normalizeEndpoints(){
        if(topology!=null&&first>=0&&last>=0&&
                first<topology.size()&&last<topology.size()&&
                topology.compare(first,last)>0){
            int swap=first;first=last;last=swap;
            if(movingHandle==1)movingHandle=2;
            else if(movingHandle==2)movingHandle=1;
        }
    }

    void beginDrag(int handle){
        if(handle!=1&&handle!=2)return;
        lockedLine=-1;selectAll=false;movingHandle=handle;
        trace.clear();visited.clear();
        if(topology!=null){
            // Starting block is intentional. Other blocks must be touched
            // explicitly by the pointer, never inferred from OCR indexes.
            int anchor=handle==1?first:last;
            if(anchor>=0&&anchor<topology.size())
                appendTrace(anchor);
        }
    }

    /** 
     * Returns true if the rendered selection may need recomputation.
     * The candidate MUST originate from the ML symbol hit function.
     */
    boolean moveTo(int target){
        if(topology==null||movingHandle==0||target<0||
                target>=topology.size()||topology.blockOf(target)<0)return false;
        boolean routeChanged=appendTrace(target);
        CircleMlBlockSelection.Move next=
                topology.move(first,last,movingHandle,target);
        boolean changed=next.first!=first||next.last!=last||
                next.active!=movingHandle;
        first=next.first;last=next.last;movingHandle=next.active;
        return changed||routeChanged;
    }

    void endDrag(){movingHandle=0;}

    void clearVisited(){
        trace.clear();visited.clear();
    }

    Set<Integer> visitedBlocks(){return Set.copyOf(visited);}

    CircleMlBlockSelection.Mask mask(){
        return topology==null?null:
                topology.select(first,last,lockedLine,selectAll,visited);
    }

    private boolean appendTrace(int target){
        int block=topology.blockOf(target);
        if(block<0)return false;
        float x=topology.centerX(target),y=topology.centerY(target);
        int oldSize=trace.size();
        boolean truncated=false;
        // On reverse traversal, remove later blocks instead of keeping
        // every component the handle ever passed.
        int matched=-1;
        for(int i=0;i<trace.size();i++){
            int b=topology.hit(trace.get(i)[0],trace.get(i)[1],8f);
            if(b>=0&&topology.blockOf(b)==block)matched=i;
        }
        if(matched>=0 && matched!=trace.size()-1){
            trace.subList(matched+1,trace.size()).clear();
            truncated=true;
        }else if(matched<0){
            trace.add(new float[]{x,y});
        }
        replayTrace();
        return truncated||trace.size()!=oldSize;
    }

    private void replayTrace(){
        visited.clear();
        if(topology==null)return;
        for(float[] point:trace){
            int match=topology.hit(point[0],point[1],12f);
            if(match>=0){
                int block=topology.blockOf(match);
                if(block>=0)visited.add(block);
            }
        }
    }

    int routeLength(){return trace.size();}
}
