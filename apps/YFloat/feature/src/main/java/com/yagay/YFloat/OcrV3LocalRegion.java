package com.yagay.YFloat;

/**
 * Detector-independent ROI around a real ML Kit Element.
 * Uses only picture bounds and ML geometry, never OCR-derived text or boxes.
 */
final class OcrV3LocalRegion {
    final int left,top,right,bottom;
    private OcrV3LocalRegion(int l,int t,int r,int b){
        left=l;top=t;right=r;bottom=b;
    }
    static OcrV3LocalRegion plan(int l,int t,int r,int b,int width,int height){
        if(width<2||height<2||r<=l||b<=t)return null;
        int xMargin=Math.max(4,Math.min(18,Math.round((b-t)*0.28f)));
        int yMargin=Math.max(3,Math.min(14,Math.round((b-t)*0.18f)));
        int x0=Math.max(0,l-xMargin),y0=Math.max(0,t-yMargin);
        int x1=Math.min(width,r+xMargin),y1=Math.min(height,b+yMargin);
        return x1-x0<2||y1-y0<2?null:
                new OcrV3LocalRegion(x0,y0,x1,y1);
    }
}
