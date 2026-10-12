package com.yagay.YTaskManager.data

/** Interval CPU measurement: 100% means one fully occupied logical core. */
internal class ProcessCpuEstimator {
    data class Ticks(val startTicks: Long, val totalTicks: Long)

    private var previous = emptyMap<Int, Ticks>()
    private var lastAtMs = 0L
    private var previousHz = 0L

    @Synchronized
    fun sample(current: Map<Int, Ticks>, atMs: Long, hz: Long): Map<Int, Float> {
        // Empty/inaccessible procfs is unknown; preserve the previous baseline.
        if (current.isEmpty() || atMs <= 0L || hz <= 0L) return emptyMap()
        val elapsed = atMs - lastAtMs
        val valid = lastAtMs > 0L && elapsed in 100L..60_000L && hz == previousHz
        val measured = if (valid) current.mapNotNull { (pid, ticks) ->
            val old = previous[pid]
            if (old == null || old.startTicks != ticks.startTicks ||
                ticks.totalTicks < old.totalTicks) null
            else {
                val delta = ticks.totalTicks - old.totalTicks
                val cpu = delta.toDouble() * 100_000.0 /
                    (hz.toDouble() * elapsed.toDouble())
                pid to cpu.toFloat().coerceAtLeast(0f)
            }
        }.toMap() else emptyMap()
        previous = current
        lastAtMs = atMs
        previousHz = hz
        return measured
    }
}
