package com.yagay.YFloat;

/**
 * Small, Android-independent coordinate conversion contract: never mix
 * dialog/window-local rectangles with physical-screen selection anchors.
 */
final class SelectionCoordinateMath {
    static int[] toScreen(int left,int top,int right,int bottom,int xOnScreen,int yOnScreen) {
        return new int[]{
                left+xOnScreen,top+yOnScreen,right+xOnScreen,bottom+yOnScreen};
    }
    private SelectionCoordinateMath() {}
}
