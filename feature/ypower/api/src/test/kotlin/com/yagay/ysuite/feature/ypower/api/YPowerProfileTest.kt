package com.yagay.ysuite.feature.ypower.api
import org.junit.Assert.*
import org.junit.Test
class YPowerProfileTest {
    @Test fun tracingRequiresExplicitFeatureNotGlobalEnable() {
        assertFalse(YPowerProfile(packageName="test.app", enabled=true).anyHookFeature)
        assertTrue(YPowerProfile(packageName="test.app", traceFiles=true).anyHookFeature)
        assertTrue(YPowerProfile(packageName="test.app", simulatePermissions=true).anyHookFeature)
    }
    @Test fun applyReportDoesNotClaimSuccessWithErrors() {
        assertFalse(YPowerApplyResult(applied=listOf("root"), errors=listOf("hook_unavailable")).success)
        assertTrue(YPowerApplyResult(applied=listOf("root")).success)
    }
}
