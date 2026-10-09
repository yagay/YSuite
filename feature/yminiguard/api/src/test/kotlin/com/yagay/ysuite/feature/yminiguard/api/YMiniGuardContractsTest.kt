package com.yagay.ysuite.feature.yminiguard.api
import org.junit.Assert.*
import org.junit.Test
class YMiniGuardContractsTest {
    @Test fun configCanDisableMasterWithoutLosingUserOptions() {
        val old = YMiniGuardSettings(masterEnabled=true, blockRemoveKill=true)
        val next = old.copy(masterEnabled=false)
        assertFalse(next.masterEnabled)
        assertTrue(next.blockRemoveKill)
    }
    @Test fun engineIsUnknownUntilRuntimeEvidenceIsRead() {
        val engine = YMiniGuardEngineStatus()
        assertTrue(engine.pid < 0)
        assertTrue(engine.hookCount < 0)
    }
}
