package com.yagay.YFloat;

import android.graphics.Rect;

/**
 * Conservative bitmap-space OCR crop around a completed text gesture.
 * Never modifies selection coordinates: the caller maps local OCR quads
 * to the frozen screenshot with ScreenBitmapTransform.documentLocalToScreen.
 */
final class PaddleCircleRoiPolicy {
    static Rect forGesture(Rect bounds,int width,int height){
        width=Math.max(1,width);height=Math.max(1,height);
        Rect input=bounds==null?new Rect():new Rect(bounds);
        if(input.isEmpty())return new Rect(0,0,width,height);
        if(!input.intersect(0,0,width,height))
            return new Rect(0,0,width,height);
        // Include the surrounding baseline/line and neighboring words. Do
        // not crop a 15px horizontal underline down to a 15px OCR tensor.
        int padX=Math.max(90,Math.min(240,width/9));
        int padY=Math.max(96,Math.min(240,height/14));
        int left=Math.max(0,input.left-padX),right=Math.min(width,input.right+padX);
        int top=Math.max(0,input.top-padY),bottom=Math.min(height,input.bottom+padY);
        int minWidth=Math.min(width,480);
        int minHeight=Math.min(height,240);
        if(right-left<minWidth){
            int target=(left+right)/2;
            left=Math.max(0,Math.min(width-minWidth,target-minWidth/2));
            right=left+minWidth;
        }
        if(bottom-top<minHeight){
            int target=(top+bottom)/2;
            top=Math.max(0,Math.min(height-minHeight,target-minHeight/2));
            bottom=top+minHeight;
        }
        return new Rect(left,top,right,bottom);
    }
    private PaddleCircleRoiPolicy(){}
}
