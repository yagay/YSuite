package com.yagay.YFloat;

/**
 * Turn a 1-pixel-high text highlight or a tap into a readable bitmap crop.
 * Coordinates remain in YFloat's captured bitmap. Crops must NEVER be sent
 * to screenshot result handlers just because they contain OCR text.
 */
final class NativeCircleGoogleRecognitionCrop {
    static int[] forGesture(String kind, float left,float top,float right,float bottom,
                            float focusX,float focusY,
                            int frameWidth,int frameHeight,float density) {
        if(frameWidth<1||frameHeight<1)return null;
        float scale=Math.max(1f,density);
        boolean tap="TAP".equals(kind);
        float l=tap?focusX-110f*scale:left-28f*scale;
        float r=tap?focusX+110f*scale:right+28f*scale;
        float t=tap?focusY-64f*scale:top-44f*scale;
        float b=tap?focusY+64f*scale:bottom+44f*scale;
        int[] x=fit(l,r,frameWidth,tap?220f*scale:160f*scale);
        int[] y=fit(t,b,frameHeight,88f*scale);
        return new int[]{x[0],y[0],x[1],y[1]};
    }

    private static int[] fit(float from,float to,int extent,float minSize) {
        int width=Math.min(extent,Math.max(1,(int)Math.ceil(Math.max(to-from,minSize))));
        int start=Math.max(0,Math.min(extent-width,
                (int)Math.floor((from+to-width)*.5f)));
        return new int[]{start,start+width};
    }

    private NativeCircleGoogleRecognitionCrop(){}
}
