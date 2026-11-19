package com.yagay.YFloat;

import java.util.List;

/**
 * Slice ACTUAL Paddle local-source text using visual ML Symbol positions.
 * ML supplies only the cardinality of each position; it never supplies
 * any of the returned characters.
 */
final class OcrV3LocalSymbolSlice {
    private OcrV3LocalSymbolSlice(){}

    static String extract(String localPaddle,List<Integer> symbolLengths,
                          int fromInclusive,int toInclusive){
        if(localPaddle==null||symbolLengths==null||
                fromInclusive<0||toInclusive<fromInclusive||
                toInclusive>=symbolLengths.size())return null;
        int count=localPaddle.codePointCount(0,localPaddle.length());
        int sum=0;
        for(int n:symbolLengths){
            if(n<=0)return null;
            sum+=n;
        }
        // Never fabricate character positions where the model's output
        // does not have the same number of actual Unicode code points.
        if(sum!=count)return null;
        int start=0,end=0;
        for(int i=0;i<symbolLengths.size();i++){
            if(i<fromInclusive)start+=symbolLengths.get(i);
            if(i<=toInclusive)end+=symbolLengths.get(i);
        }
        return localPaddle.substring(
                localPaddle.offsetByCodePoints(0,start),
                localPaddle.offsetByCodePoints(0,end));
    }
}
