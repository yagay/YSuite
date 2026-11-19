package com.yagay.YFloat;

import static org.junit.Assert.*;
import java.util.concurrent.CancellationException;
import org.junit.Test;

/** No Android device or actual neural-network weight needed. */
public final class PaddleRequestPerformanceTest {
    @Test public void cancellationIsIdempotentAndObservable(){
        PaddleRequestToken request=new PaddleRequestToken();
        assertFalse(request.cancelled());
        request.check();
        request.cancel();request.cancel();
        assertTrue(request.cancelled());
        try{
            request.check();
            fail("Expected cancellation at a safe inference boundary");
        }catch(CancellationException expected){}
        assertTrue(request.queueMs()>=0L);
    }

    @Test public void textRegionRemainsWithinScreenAndContainsGesture(){
        int[] b=PaddleCircleRoiPolicy.expand(400,1250,600,1270,1272,2641);
        assertTrue(b[0]<=400 && b[1]<=1250);
        assertTrue(b[2]>=600 && b[3]>=1270);
        assertTrue(b[2]-b[0]>=480);
        assertTrue(b[3]-b[1]>=240);
        assertTrue(b[0]>=0 && b[1]>=0);
        assertTrue(b[2]<=1272 && b[3]<=2641);
        assertTrue((b[2]-b[0])*(b[3]-b[1])<1272*2641/2);
    }

    @Test public void nearEdgeAndSmallDisplayAreClamped(){
        int[] edge=PaddleCircleRoiPolicy.expand(0,0,8,6,340,640);
        assertArrayEquals(new int[]{0,0,340,240},edge);
        int[] tiny=PaddleCircleRoiPolicy.expand(10,10,20,20,150,140);
        assertArrayEquals(new int[]{0,0,150,140},tiny);
        int[] invalid=PaddleCircleRoiPolicy.expand(100,100,80,90,320,600);
        assertArrayEquals(new int[]{0,0,320,600},invalid);
    }

    @Test public void invalidRoiUsesFullImageWithoutNegativeDimensions(){
        int[] off=PaddleCircleRoiPolicy.expand(-80,-70,-30,-10,1080,2400);
        assertArrayEquals(new int[]{0,0,1080,2400},off);
    }
}
