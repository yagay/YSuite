package com.yagay.ysuite.feature.ypower.api

import org.junit.Assert.*
import org.junit.Test

class YPowerHookRestartPolicyTest {
    @Test fun rootOnlyChangesNeverKillTarget() {
        assertFalse(requiresHookRestart(null, false, "rev1", false))
        assertFalse(requiresHookRestart("rev1", false, "rev2", false))
    }
    @Test fun newHookAndChangedHookRequireRestart() {
        assertTrue(requiresHookRestart(null, false, "rev1", true))
        assertTrue(requiresHookRestart("rev1", true, "rev2", true))
    }
    @Test fun disablingPreviouslyActiveHookRequiresRestart() {
        assertTrue(requiresHookRestart("rev1", true, "rev2", false))
        assertTrue(requiresHookRestart("rev1", true, "rev1", false))
    }
    @Test fun unchangedRunningHookDoesNotRestartAgain() {
        assertFalse(requiresHookRestart("rev1", true, "rev1", true))
    }
}
