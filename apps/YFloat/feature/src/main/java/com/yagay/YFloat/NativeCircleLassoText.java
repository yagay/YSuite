package com.yagay.YFloat;

/**
 * Geometry-only V3 lasso/content fusion. OCR coordinates and gesture points
 * must already have been mapped to the same frozen bitmap pixel space.
 */
final class NativeCircleLassoText {
    static boolean contains(float[] path, float x, float y) {
        if(path==null||path.length<6||path.length%2!=0)return false;
        boolean inside=false;
        int n=path.length/2;
        for(int i=0,j=n-1;i<n;j=i++){
            float ax=path[2*i],ay=path[2*i+1];
            float bx=path[2*j],by=path[2*j+1];
            if((ay>y)!=(by>y)){
                float crossed=(bx-ax)*(y-ay)/(by-ay)+ax;
                if(x<crossed)inside=!inside;
            }
        }
        return inside;
    }
    static boolean preferText(int recognizedCharacters, float coveredFraction) {
        return recognizedCharacters>=4&&coveredFraction>=0.10f;
    }
    private NativeCircleLassoText(){}
}
