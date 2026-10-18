package com.yagay.YFloat;

import android.graphics.Bitmap;
import android.graphics.Rect;

/**
 * Original screenshot only: bounded local pixel calibration. Recognition,
 * copied text, and the selection's logical character indices are untouched.
 */
final class TextGeometryPixelLocator {
    private TextGeometryPixelLocator(){ }

    static Rect refine(FLCircleCapture.Frame frame,Rect glyph,Rect detector){
        if(frame==null || frame.bitmap==null || frame.bitmap.isRecycled()
                || glyph==null || glyph.isEmpty())return null;
        Bitmap bitmap=frame.bitmap;
        // Never analyse another screenshot frame. Keep the calibration
        // inside the OCR word with some background for colour statistics.
        int xPad=Math.min(64,Math.max(8,Math.round(glyph.width()*0.20f)));
        int yPad=Math.min(42,Math.max(8,Math.round(glyph.height()*0.32f)));
        Rect region=new Rect(glyph.left-xPad,glyph.top-yPad,
                glyph.right+xPad,glyph.bottom+yPad);
        if(detector!=null && !detector.isEmpty()) {
            // Expand only toward a reasonably related detector edge.
            if(detector.width()<=glyph.width()*1.6f
                    && detector.height()<=glyph.height()*1.8f
                    && Rect.intersects(glyph,detector)){
                region.union(detector);
            }
        }
        Rect bitmapRegion=frame.screenRectToBitmap(region);
        if(!bitmapRegion.intersect(0,0,bitmap.getWidth(),bitmap.getHeight()))
            return null;
        int width=bitmapRegion.width(),height=bitmapRegion.height();
        if(width<8 || height<8 || width>720 || height>360)return null;
        Rect seed=frame.screenRectToBitmap(glyph);
        seed.offset(-bitmapRegion.left,-bitmapRegion.top);
        if(!seed.intersect(0,0,width,height))return null;
        int[] src=new int[width*height];
        try{
            bitmap.getPixels(src,0,width,bitmapRegion.left,bitmapRegion.top,width,height);
        }catch(RuntimeException error){return null;}
        int[] adjusted=TextGeometryPixelPolicy.refine(src,width,height,
                new int[]{seed.left,seed.top,seed.right,seed.bottom});
        if(adjusted==null)return null;
        Rect screenResult=frame.bitmapRectToScreen(new Rect(
                bitmapRegion.left+adjusted[0],bitmapRegion.top+adjusted[1],
                bitmapRegion.left+adjusted[2],bitmapRegion.top+adjusted[3]));
        // Only use the refined pixels if they remain close to the selected
        // word. This prevents a red icon's border or launcher caption from
        // jumping into the selected text.
        if(screenResult.isEmpty() || screenResult.width()>glyph.width()*1.75f
                || screenResult.height()>glyph.height()*1.6f)return null;
        return screenResult;
    }
}
