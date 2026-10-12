package com.yagay.YTaskManager.data

/**
 * Interval CPU accounting from /proc/PID/stat.
 * 100% means one occupied logical core, so multithreaded tasks may exceed 100%.
 */
internal class ProcessCpuEstimator {
    data class Ticks(val startTicks: Long, val totalTicks: Long)
    private data class Baseline(val ticks: Ticks, val timestampMs: Long)

    // One independent baseline per PID. A partial procfs read cannot reset
    // the baseline of another process or fabricate a 0% reading.
    private val baselines = HashMap<Int, Baseline>()
    private var previousHz = 0L
    private var previousTimeMs = 0L

    @Synchronized
    fun sample(current: Map<Int, Ticks>, atMs: Long, hz: Long): Map<Int, Float> {
        if (current.isEmpty() || atMs <= 0L || hz <= 0L) return emptyMap()
        if (previousHz != hz || atMs < previousTimeMs) baselines.clear()
        previousHz = hz
        previousTimeMs = atMs

        val measured = HashMap<Int, Float>()
        current.forEach { (pid, tick) ->
            val old = baselines[pid]
            val elapsed = atMs - (old?.timestampMs ?: atMs)
            if (old != null && old.ticks.startTicks == tick.startTicks &&
                tick.totalTicks >= old.ticks.totalTicks && elapsed in 100L..60_000L
            ) {
                val delta = tick.totalTicks - old.ticks.totalTicks
                val cpuPercent = delta.toDouble() * 100_000.0 /
                    (hz.toDouble() * elapsed.toDouble())
                measured[pid] = cpuPercent.toFloat().coerceAtLeast(0f)
            }
            baselines[pid] = Baseline(tick, atMs)
        }
        // Prevent unbounded growth as processes terminate. Missing PIDs retain
        // their last baseline briefly to survive intermittent /proc permission errors.
        baselines.entries.removeAll { (_, baseline) ->
            atMs - baseline.timestampMs > 60_000L ||
                baseline.timestampMs > atMs
        }
        return measured
    }
}
