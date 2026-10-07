package com.yagay.ysuite.feature.ytaskmanager

import com.yagay.ysuite.feature.ytaskmanager.api.YTaskCpuCore
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskSystemSnapshot
import kotlin.math.max

internal class YTaskSystemSampler(
    private val shell: YTaskRootRunner,
) {
    private var previousCpu: CpuCounters? = null

    suspend fun read(): YTaskSystemSnapshot {
        val command =
            """
            echo __STAT__
            cat /proc/stat
            echo __MEM__
            cat /proc/meminfo
            echo __LOAD__
            cat /proc/loadavg
            echo __UPTIME__
            cat /proc/uptime
            echo __SOC__
            getprop ro.soc.model 2>/dev/null
            echo __ARCH__
            uname -m 2>/dev/null
            echo __ABI__
            getprop ro.product.cpu.abi 2>/dev/null
            echo __GOV__
            cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null
            echo __TEMP__
            for z in /sys/class/thermal/thermal_zone*; do
              [ -r "@z/temp" ] || continue
              type=$(cat "@z/type" 2>/dev/null)
              case "@type" in
                *cpu*|*CPU*|*soc*|*SOC*|*ap*|*AP*)
                  echo "@type|$(cat "@z/temp" 2>/dev/null)"
                  break
                  ;;
              esac
            done
            echo __CORES__
            for c in /sys/devices/system/cpu/cpu[0-9]*; do
              n=$(basename "@c" | sed 's/^cpu//')
              min=$(cat "@c/cpufreq/scaling_min_freq" 2>/dev/null)
              cur=$(cat "@c/cpufreq/scaling_cur_freq" 2>/dev/null)
              max=$(cat "@c/cpufreq/scaling_max_freq" 2>/dev/null)
              echo "@n|@min|@cur|@max"
            done
            """.trimIndent()
                .replace('@', 36.toChar())
        val raw = shell.text(command)

        val stat = section(raw, "__STAT__", "__MEM__")
        val mem = section(raw, "__MEM__", "__LOAD__")
        val load = section(raw, "__LOAD__", "__UPTIME__")
        val uptime = section(raw, "__UPTIME__", "__SOC__")
        val soc = section(raw, "__SOC__", "__ARCH__")
        val arch = section(raw, "__ARCH__", "__ABI__")
        val abi = section(raw, "__ABI__", "__GOV__")
        val governor = section(raw, "__GOV__", "__TEMP__")
        val temp =
            section(raw, "__TEMP__", "__CORES__")
                .lineSequence()
                .firstOrNull()
                .orEmpty()
        val cores =
            raw.substringAfter("__CORES__", "")
                .lineSequence()
                .mapNotNull { line ->
                    val values = line.split('|')
                    val index =
                        values.getOrNull(0)?.toIntOrNull()
                            ?: return@mapNotNull null
                    YTaskCpuCore(
                        core = index,
                        minKHz = values.getOrNull(1)?.toLongOrNull(),
                        currentKHz = values.getOrNull(2)?.toLongOrNull(),
                        maxKHz = values.getOrNull(3)?.toLongOrNull(),
                    )
                }
                .sortedBy { it.core }
                .toList()

        val cpuLine =
            stat.lineSequence()
                .firstOrNull { it.startsWith("cpu ") }
                .orEmpty()
        val currentCpu = parseCpu(cpuLine)
        val cpuPercent = calculateCpu(previousCpu, currentCpu)
        previousCpu = currentCpu

        val memory =
            mem.lineSequence()
                .mapNotNull { line ->
                    val key = line.substringBefore(':', "").trim()
                    val amount =
                        line.substringAfter(':', "")
                            .trim()
                            .substringBefore(' ')
                            .toLongOrNull()
                    if (key.isBlank() || amount == null) {
                        null
                    } else {
                        key to amount * 1024L
                    }
                }
                .toMap()

        val total = memory["MemTotal"] ?: 0L
        val available =
            memory["MemAvailable"]
                ?: (
                    (memory["MemFree"] ?: 0L) +
                        (memory["Buffers"] ?: 0L) +
                        (memory["Cached"] ?: 0L)
                    )
        val swapTotal = memory["SwapTotal"] ?: 0L
        val swapFree = memory["SwapFree"] ?: 0L

        return YTaskSystemSnapshot(
            cpuPercent = cpuPercent,
            ramUsedBytes = max(0L, total - available),
            ramTotalBytes = total,
            ramAvailableBytes = available,
            cachedBytes = memory["Cached"] ?: 0L,
            buffersBytes = memory["Buffers"] ?: 0L,
            swapUsedBytes = max(0L, swapTotal - swapFree),
            swapTotalBytes = swapTotal,
            load1 = load.substringBefore(' ').toFloatOrNull() ?: 0f,
            uptimeSeconds =
                uptime.substringBefore(' ').toDoubleOrNull()?.toLong() ?: 0L,
            soc = soc,
            architecture = arch,
            abi = abi,
            governor = governor,
            coreCount =
                cores.size.takeIf { it > 0 }
                    ?: stat.lineSequence().count {
                        it.matches(Regex("""cpu\d+\s+.*"""))
                    },
            cpuTemperatureC = parseTemperature(temp),
            cpuCores = cores,
        )
    }

    private fun parseCpu(line: String): CpuCounters? {
        if (!line.startsWith("cpu ")) return null
        val values =
            line.trim()
                .split(Regex("""\s+"""))
                .drop(1)
                .mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        return CpuCounters(values)
    }

    private fun calculateCpu(
        old: CpuCounters?,
        current: CpuCounters?,
    ): Float {
        if (old == null || current == null) return 0f
        val total = current.total - old.total
        val idle = current.idle - old.idle
        if (total <= 0L) return 0f
        return ((total - idle) * 100f / total).coerceIn(0f, 100f)
    }

    private fun parseTemperature(line: String): Float? {
        val raw =
            line.substringAfter('|', line)
                .trim()
                .toFloatOrNull()
                ?: return null
        val c =
            when {
                raw > 10_000f -> raw / 1000f
                raw > 1000f -> raw / 100f
                raw > 200f -> raw / 10f
                else -> raw
            }
        return c.takeIf { it in -20f..150f }
    }

    private fun section(
        text: String,
        start: String,
        end: String,
    ): String =
        text.substringAfter(start, "")
            .substringBefore(end, "")
            .trim()

    private data class CpuCounters(
        val values: List<Long>,
    ) {
        val total: Long
            get() = values.sum()
        val idle: Long
            get() =
                (values.getOrNull(3) ?: 0L) +
                    (values.getOrNull(4) ?: 0L)
    }
}
