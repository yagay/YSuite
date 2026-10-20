package com.yagay.YFloat;

/** Bitmap-space bounds for detector-free Paddle LINE recognition. */
final class OcrV4Region {
    final int left,top,right,bottom;
    private OcrV4Region(int l,int t,int r,int b){
        left=l;top=t;right=r;bottom=b;
    }
    /** A separate single-Element ROI, NOT a variant of the failed line. */
    static OcrV4Region word(int l,int t,int r,int b,int width,int height){
        if(width<2||height<2||r<=l||b<=t)return null;
        int h=b-t;
        int xMargin=Math.max(3,Math.min(12,Math.round(h*.20f)));
        int yMargin=Math.max(2,Math.min(8,Math.round(h*.12f)));
        int left=Math.max(0,l-xMargin),top=Math.max(0,t-yMargin);
        int right=Math.min(width,r+xMargin),bottom=Math.min(height,b+yMargin);
        return right-left<2||bottom-top<2?null:
                new OcrV4Region(left,top,right,bottom);
    }

    static OcrV4Region line(int l,int t,int r,int b,int width,int height,
                            boolean retry){
        if(width<2||height<2||r<=l||b<=t)return null;
        // The initial ROI covers the entire ML Line plus small margins,
        // unlike former word-level DB detector boxes. Second attempt widens
        // the horizontal crop ONLY; don't absorb an adjacent text row.
        int h=b-t;
        int horizontal=Math.max(5,Math.min(retry?40:20,
                Math.round(h*(retry?0.72f:0.32f))));
        int vertical=Math.max(3,Math.min(10,Math.round(h*0.15f)));
        int left=Math.max(0,l-horizontal),top=Math.max(0,t-vertical);
        int right=Math.min(width,r+horizontal),bottom=Math.min(height,b+vertical);
        return right-left<2||bottom-top<2?null:
                new OcrV4Region(left,top,right,bottom);
    }
}
