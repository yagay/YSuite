package com.yagay.YFloat;

/**
 * Bitmap-space crop for YFloat's tap/highlight text-recognition gestures.
 * A gesture's raw stroke can be one pixel tall, which is not an OCR image.
 * Give Lens neighbouring glyphs while keeping the crop tied to the user's
 * selection. Closed circles and scribbles remain editable screenshot regions.
 *
 * No Android dependencies: pure geometry can be tested on the JVM.
 */
final class NativeCircleGoogleRecognitionCrop {
    static int[] forGesture(String kind,
                            float left,float top,float right,float bottom,
                            float focusX,float focusY,
                            int frameWidth,int frameHeight,float density) {
        if(frameWidth<=0||frameHeight<=0) return null;
        float scale=Math.max(1f,density);
        boolean tap="TAP".equals(kind);
        float x1=tap?focusX-110f*scale:left-28f*scale;
        float x2=tap?focusX+110f*scale:right+28f*scale;
        float y1=tap?focusY-64f*scale:top-44f*scale;
        float y2=tap?focusY+64f*scale:bottom+44f*scale;
        int[] x=fit(x1,x2,frameWidth,tap?220f*scale:160f*scale);
        int[] y=fit(y1,y2,frameHeight,88f*scale);
        if(x[1]<=x[0]||y[1]<=y[0])return null;
        return new int[]{x[0],y[0],x[1],y[1]};
    }

    /** Preserve the target span at screen edges instead of clipping it to 1px. */
    private static int[] fit(float start,float end,int extent,float minimum) {
        float width=Math.min(extent,Math.max(end-start,minimum));
        float centre=(start+end)*.5f;
        int pixels=Math.min(extent,Math.max(1,(int)Math.ceil(width)));
        int left=Math.max(0,Math.min(extent-pixels,(int)Math.floor(centre-pixels*.5f)));
        return new int[]{left,left+pixels};
    }

    private NativeCircleGoogleRecognitionCrop(){}
}
