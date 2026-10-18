package com.yagay.YFloat;

import android.graphics.Rect;

/**
 * Bitmap-space text-gesture crop sizing, independent of OCR/model lifetime.
 * A pure primitive core keeps edge/small-screen behavior unit-testable.
 */
final class PaddleCircleRoiPolicy {
    static Rect forGesture(Rect bounds,int width,int height){
        if(bounds==null)return new Rect(0,0,Math.max(1,width),Math.max(1,height));
        int[] result=expand(bounds.left,bounds.top,bounds.right,bounds.bottom,width,height);
        return new Rect(result[0],result[1],result[2],result[3]);
    }

    /** [left,top,right,bottom] inside the frozen screenshot. */
    static int[] expand(int l,int t,int r,int b,int width,int height){
        width=Math.max(1,width);height=Math.max(1,height);
        l=Math.max(0,Math.min(width,l));r=Math.max(0,Math.min(width,r));
        t=Math.max(0,Math.min(height,t));b=Math.max(0,Math.min(height,b));
        if(r<=l||b<=t)return new int[]{0,0,width,height};
        int padX=Math.max(90,Math.min(240,width/9));
        int padY=Math.max(96,Math.min(240,height/14));
        int left=Math.max(0,l-padX),right=Math.min(width,r+padX);
        int top=Math.max(0,t-padY),bottom=Math.min(height,b+padY);
        int minWidth=Math.min(width,480),minHeight=Math.min(height,240);
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
        return new int[]{left,top,right,bottom};
    }
    private PaddleCircleRoiPolicy(){}
}
