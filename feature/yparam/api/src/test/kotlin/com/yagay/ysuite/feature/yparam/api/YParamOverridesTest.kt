package com.yagay.ysuite.feature.yparam.api
import org.junit.Assert.*
import org.junit.Test
class YParamOverridesTest {
    @Test fun unsetValuesNeverCountAsOverrides() {
        assertEquals(0, YParamOverrides().overrideCount())
        assertEquals(2, YParamOverrides(localeTag="zh-CN", densityDpi=440).overrideCount())
    }
    @Test fun allNonNullOverridesAreCounted() {
        assertEquals(26, YParamOverrides(
            densityDpi=1,widthPixels=1,heightPixels=1,smallestWidthDp=1,
            screenWidthDp=1,screenHeightDp=1,fontScale=1f,xdpi=1f,ydpi=1f,
            refreshRate=1f,localeTag="en",timeZoneId="UTC",nightMode="on",
            orientation="portrait",userAgent="ua",allowScreenshots=true,
            keepScreenOn=true,locationMode="gps",latitude=1.0,longitude=1.0,
            altitude=1.0,accuracy=1f,speed=1f,bearing=1f,
            randomRadiusMeters=1f,locationUpdateIntervalMs=100,
        ).overrideCount())
    }
}
