package com.yagay.YFloat;

/** One intent-first interaction reducer. No modes or Google-specific classes. */
final class NativeCircleState {
    enum Phase { IDLE, DRAWING, RESOLVING, TEXT_READY, IMAGE_READY, CLOSED }
    enum Intent { TEXT, IMAGE }

    Phase phase = Phase.IDLE;
    Intent intent;
    long revision;
    String selectedText = "";
    float left, top, right, bottom;

    void clear() {
        if(phase==Phase.CLOSED)return;
        phase=Phase.IDLE; intent=null; selectedText="";
        left=top=right=bottom=0f;
        revision++;
    }
    boolean begin(float x,float y) {
        if(phase==Phase.CLOSED)return false;
        clear();left=right=x;top=bottom=y;phase=Phase.DRAWING;return true;
    }
    void bounds(float l,float t,float r,float b) {
        if(phase!=Phase.DRAWING && phase!=Phase.RESOLVING
                && phase!=Phase.TEXT_READY && phase!=Phase.IMAGE_READY)return;
        left=Math.min(l,r);top=Math.min(t,b);
        right=Math.max(l,r);bottom=Math.max(t,b);
        ++revision;
    }
    boolean resolving() {
        if(phase!=Phase.DRAWING)return false;
        phase=Phase.RESOLVING;revision++;return true;
    }
    boolean resolveImage(long expected,float l,float t,float r,float b) {
        if(phase!=Phase.RESOLVING||expected!=revision||r<=l||b<=t)return false;
        setBox(l,t,r,b);
        intent=Intent.IMAGE;phase=Phase.IMAGE_READY;revision++;return true;
    }
    boolean resolveText(long expected,String text,float l,float t,float r,float b) {
        if(phase!=Phase.RESOLVING||expected!=revision||text==null||text.isBlank())return false;
        setBox(l,t,r,b);
        selectedText=text;intent=Intent.TEXT;phase=Phase.TEXT_READY;revision++;return true;
    }
    boolean updateText(String text,float l,float t,float r,float b) {
        if(phase!=Phase.TEXT_READY||text==null||text.isBlank())return false;
        selectedText=text;setBox(l,t,r,b);revision++;return true;
    }
    boolean canFinishImage(){return phase==Phase.IMAGE_READY&&right>left&&bottom>top;}
    void close(){phase=Phase.CLOSED;intent=null;selectedText="";revision++;}
    private void setBox(float l,float t,float r,float b){
        left=Math.min(l,r);top=Math.min(t,b);right=Math.max(l,r);bottom=Math.max(t,b);
    }
}
