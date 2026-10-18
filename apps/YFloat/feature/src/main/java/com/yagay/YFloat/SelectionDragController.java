package com.yagay.YFloat;

/**
 * Pure selection reducer; its drag identity is a caret, not a rectangle corner.
 * AOSP Editor-style behaviour: when the moving caret crosses the stationary
 * one, swap the active endpoint instead of clamping it in place.
 */
final class SelectionDragController {
    static final int NONE=0, START=1, END=2;
    static final class Range {
        final int first,last,active;
        Range(int first,int last,int active){
            this.first=first;this.last=last;this.active=active;
        }
    }
    private SelectionDragController() {}
    static Range move(int first,int last,int active,int candidate){
        if(first<0||last<first||candidate<0 ||(active!=START&&active!=END))
            return new Range(first,last,active);
        if(active==START){
            return candidate<=last
                    ?new Range(candidate,last,START)
                    :new Range(last,candidate,END);
        }
        return candidate>=first
                ?new Range(first,candidate,END)
                :new Range(candidate,first,START);
    }
}
