package com.yagay.YTaskManager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessCpuEstimatorTest {
    @Test fun twoSamplesComputeRealPercent() {
        val e = ProcessCpuEstimator()
        assertTrue(e.sample(mapOf(7 to ProcessCpuEstimator.Ticks(100, 200)), 1000, 100).isEmpty())
        assertEquals(25f, e.sample(mapOf(7 to ProcessCpuEstimator.Ticks(100, 225)), 2000, 100)[7]!!, 0.001f)
    }
    @Test fun reusedPidDoesNotInheritOldCpu() {
        val e = ProcessCpuEstimator()
        e.sample(mapOf(5 to ProcessCpuEstimator.Ticks(10, 200)), 1000, 100)
        assertFalse(e.sample(mapOf(5 to ProcessCpuEstimator.Ticks(100, 240)), 2000, 100).containsKey(5))
        assertEquals(10f, e.sample(mapOf(5 to ProcessCpuEstimator.Ticks(100, 250)), 3000, 100)[5]!!, 0.001f)
    }
    @Test fun emptyProcfsDoesNotResetBaseline() {
        val e = ProcessCpuEstimator()
        e.sample(mapOf(9 to ProcessCpuEstimator.Ticks(1, 100)), 1000, 100)
        assertTrue(e.sample(emptyMap(), 1500, 100).isEmpty())
        assertEquals(50f, e.sample(mapOf(9 to ProcessCpuEstimator.Ticks(1, 200)), 3000, 100)[9]!!, 0.001f)
    }
    @Test fun tickCounterResetAndHzChangeAreUnknown() {
        val e = ProcessCpuEstimator()
        e.sample(mapOf(9 to ProcessCpuEstimator.Ticks(1, 100)), 1000, 100)
        assertTrue(e.sample(mapOf(9 to ProcessCpuEstimator.Ticks(1, 80)), 2000, 100).isEmpty())
        assertTrue(e.sample(mapOf(9 to ProcessCpuEstimator.Ticks(1, 100)), 3000, 250).isEmpty())
    }
}
