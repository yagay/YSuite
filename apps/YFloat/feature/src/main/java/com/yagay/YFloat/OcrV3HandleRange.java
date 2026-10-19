package com.yagay.YFloat;

/** Minimal pointer state for the standard two ML Kit text handles. */
final class OcrV3HandleRange {
    final int first,last,moving;
    private OcrV3HandleRange(int a,int b,int m){first=a;last=b;moving=m;}
    static OcrV3HandleRange move(int first,int last,int target,
                                 int moving,boolean sameMlBlock){
        if(target<0||moving<1||moving>2)
            return new OcrV3HandleRange(first,last,moving);
        if(!sameMlBlock)return new OcrV3HandleRange(target,target,moving);
        int a=moving==1?target:first;
        int b=moving==2?target:last;
        if(a>b)return new OcrV3HandleRange(b,a,moving==1?2:1);
        return new OcrV3HandleRange(a,b,moving);
    }
}
