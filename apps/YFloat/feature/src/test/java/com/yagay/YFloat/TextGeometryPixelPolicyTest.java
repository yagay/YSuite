package com.yagay.YFloat;

import static org.junit.Assert.*;
import org.junit.Test;

public final class TextGeometryPixelPolicyTest {
    private static final int RED=0xffb52131;
    private static final int WHITE=0xffeeeeee;

    private static int[] icon(int width,int height) {
        int[] bitmap=new int[width*height];
        java.util.Arrays.fill(bitmap,RED);
        return bitmap;
    }

    private static void fill(int[] pixels,int width,int left,int top,int right,int bottom){
        for(int y=top;y<bottom;y++)
            for(int x=left;x<right;x++)pixels[y*width+x]=WHITE;
    }

    @Test public void toutiaoWhiteGlyphsOnUniformRedIconAreTightlyLocated(){
        int w=120,h=72;
        int[] pixels=icon(w,h);
        fill(pixels,w,24,18,47,51);
        fill(pixels,w,59,18,91,51);
        // Deliberately short OCR CTC bounds cut the last Chinese glyph.
        int[] result=TextGeometryPixelPolicy.refine(
                pixels,w,h,new int[]{21,11,79,60});
        assertNotNull("Pixel refinement should cover the actual glyphs",result);
        assertTrue("Second glyph must not be cut",result[2]>=91);
        assertTrue("Do not include the whole red icon",result[0]>15);
        assertTrue(result[1]>=16);
        assertTrue(result[3]<=53);
    }

    @Test public void confusingNonUniformBackgroundRejectsPixelGeometry(){
        int w=96,h=64;
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)
            for(int x=0;x<w;x++)
                pixels[y*w+x]=((x+y)&1)==0?0xff222222:0xffffffff;
        assertNull(TextGeometryPixelPolicy.refine(
                pixels,w,h,new int[]{20,15,70,45}));
    }

    @Test public void invalidAndOversizedImagesAlwaysFallback(){
        assertNull(TextGeometryPixelPolicy.refine(new int[4],2,2,
                new int[]{0,0,2,2}));
        assertNull(TextGeometryPixelPolicy.refine(new int[721*40],721,40,
                new int[]{10,10,60,30}));
        assertNull(TextGeometryPixelPolicy.refine(new int[100],10,10,
                new int[]{-2,0,8,10}));
    }
}
