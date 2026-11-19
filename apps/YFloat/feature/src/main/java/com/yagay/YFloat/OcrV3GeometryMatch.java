package com.yagay.YFloat;

/** Pure coordinate matching, with no recognition text or Android UI dependency. */
final class OcrV3GeometryMatch {
    private OcrV3GeometryMatch(){}
    static double score(int gl,int gt,int gr,int gb,
                        int wl,int wt,int wr,int wb) {
        int left=Math.max(gl,wl),top=Math.max(gt,wt);
        int right=Math.min(gr,wr),bottom=Math.min(gb,wb);
        if(gr<=gl||gb<=gt||wr<=wl||wb<=wt||right<=left||bottom<=top)
            return Double.NEGATIVE_INFINITY;
        double glyphArea=(double)(gr-gl)*(gb-gt);
        double covered=(double)(right-left)*(bottom-top)/glyphArea;
        // Never snap to unrelated words or icon labels across empty pixels.
        if(covered<.35)return Double.NEGATIVE_INFINITY;
        double cx=(gl+gr)*.5,cy=(gt+gb)*.5;
        boolean center=cx>=wl&&cx<wr&&cy>=wt&&cy<wb;
        double wordArea=(double)(wr-wl)*(wb-wt);
        return covered*3+(center?1:0)-.06*Math.log1p(wordArea/glyphArea);
    }
}
