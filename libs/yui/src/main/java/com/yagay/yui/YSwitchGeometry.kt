package com.yagay.yui

import kotlin.math.max

/**
 * Geometry shared by the Compose and Java/View switch renderers.
 * Visual dimensions may shrink; the touch target remains at least 48dp.
 */
data class YSwitchGeometry(
    val scale: Float,
    val trackWidthDp: Float,
    val trackHeightDp: Float,
    val thumbDiameterDp: Float,
    val canvasWidthDp: Float,
    val canvasHeightDp: Float,
    val touchWidthDp: Float,
    val touchHeightDp: Float,
    val useMaterialTrack: Boolean,
)

fun YAppearance.switchGeometry(): YSwitchGeometry {
    val scale = switchScalePercent / 100f
    val trackWidth = switchTrackWidthDp * scale
    val trackHeight = switchTrackHeightDp * scale
    val thumb = switchThumbSizeDp * scale
    val canvasWidth = max(trackWidth, thumb + 4f * scale)
    val canvasHeight = max(trackHeight, thumb + 4f * scale)
    return YSwitchGeometry(
        scale = scale,
        trackWidthDp = trackWidth,
        trackHeightDp = trackHeight,
        thumbDiameterDp = thumb,
        canvasWidthDp = canvasWidth,
        canvasHeightDp = canvasHeight,
        touchWidthDp = max(max(48f, iconTouchTargetDp.toFloat()),
            max(switchSlotWidthDp.toFloat(), canvasWidth)),
        touchHeightDp = max(max(48f, iconTouchTargetDp.toFloat()), canvasHeight),
        useMaterialTrack = switchTrackWidthDp == 52 &&
            switchTrackHeightDp == 32 && switchThumbSizeDp == 24,
    )
}
