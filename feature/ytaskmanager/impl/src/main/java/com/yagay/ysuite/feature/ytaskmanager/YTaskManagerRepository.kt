package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskGpuSnapshot
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskNetworkRow
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessKind
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskSnapshot
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskSystemSnapshot
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlin.math.max

internal class YTaskManagerRepository(
    context: Context,
    private val root: RootGateway,
    private val logger: YSuiteLogger,
) {
    private val packageManager = context.packageManager
    private var previousCpu: CpuCounters? = null
    private var previousNetwork = emptyMap<Int, Pair<Long, Long>>()
    private var previousNetworkAt = 0L

    suspend fun rootStatus(): CapabilityStatus = root.status()

    suspend fun snapshot(): YTaskSnapshot {
        val systemText = command(
            "cat /proc/stat; echo __MEM__; cat /proc/meminfo; " +
                "echo __LOAD__; cat /proc/loadavg; echo __UPTIME__; cat /proc/uptime; " +
                "echo __SOC__; getprop ro.soc.model; echo __ARCH__; uname -m; " +
                "echo __TEMP__; cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null || true",
        )
        val processText = command(
            "ps -A -o PID,PPID,UID,RSS,NAME,ARGS 2>/dev/null || ps -A 2>/dev/null",
        )
        val gpuText = command(
            "cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null || true; echo __FREQ__; " +
                "cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null || true; " +
                "cat /sys/class/kgsl/kgsl-3d0/devfreq/min_freq 2>/dev/null || true; " +
                "cat /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null || true",
        )
        val networkText = command(
            "cat /proc/net/xt_qtaguid/stats 2>/dev/null || true",
        )

        val network = parseNetwork(networkText)
        val processes = parseProcesses(processText, network.associateBy { it.uid })
        return YTaskSnapshot(
            system = parseSystem(systemText),
            gpu = parseGpu(gpuText),
            processes = processes,
            network = network,
            networkBackend =
                if (networkText.lineSequence().count() > 1) {
                    "qtaguid"
                } else {
                    "unavailable"
                },
        )
    }

    suspend fun kill(pid: Int): Boolean =
        commandResult("kill -9 $pid").exitCode == 0

    suspend fun forceStop(packageName: String): Boolean =
        commandResult(
            "am force-stop " + shellQuote(packageName),
        ).exitCode == 0

    private suspend fun command(value: String): String =
        commandResult(value).let { result ->
            if (result.exitCode != 0 && result.stdout.isBlank()) {
                error(result.stderr.ifBlank { "Command failed" })
            }
            result.stdout
        }

    private suspend fun commandResult(value: String) =
        when (
            val outcome =
                root.execute(
                    RootRequest(
                        command = value,
                        timeoutMillis = 8_000L,
                    ),
                )
        ) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> {
                logger.error(
                    TAG,
                    "Root command failed: " + outcome.error.code,
                    outcome.error.cause,
                )
                error(outcome.error.message)
            }
        }

    private fun parseSystem(raw: String): YTaskSystemSnapshot {
        val stat = raw.substringBefore("__MEM__")
        val mem = raw.substringAfter("__MEM__", "").substringBefore("__LOAD__")
        val load = raw.substringAfter("__LOAD__", "").substringBefore("__UPTIME__")
        val uptime = raw.substringAfter("__UPTIME__", "").substringBefore("__SOC__")
        val soc = raw.substringAfter("__SOC__", "").substringBefore("__ARCH__").trim()
        val arch = raw.substringAfter("__ARCH__", "").substringBefore("__TEMP__").trim()
        val tempRaw = raw.substringAfter("__TEMP__", "").trim().lineSequence().firstOrNull().orEmpty()

        val counters = stat.lineSequence()
            .firstOrNull { it.startsWith("cpu ") }
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.drop(1)
            ?.mapNotNull { it.toLongOrNull() }
            ?.let(::CpuCounters)
        val cpu = cpuPercent(counters)

        val values = mem.lineSequence()
            .mapNotNull { line ->
                val key = line.substringBefore(':', "").trim()
                val amount = line.substringAfter(':', "").trim()
                    .substringBefore(' ')
                    .toLongOrNull()
                if (key.isBlank() || amount == null) null else key to amount * 1024L
            }
            .toMap()
        val total = values["MemTotal"] ?: 0L
        val available = values["MemAvailable"] ?: values["MemFree"] ?: 0L
        val swapTotal = values["SwapTotal"] ?: 0L
        val swapFree = values["SwapFree"] ?: 0L
        val coreCount = stat.lineSequence().count {
            it.matches(Regex("cpu\\d+\\s+.*"))
        }
        val tempValue = tempRaw.toFloatOrNull()?.let {
            if (it > 500f) it / 1000f else it
        }

        return YTaskSystemSnapshot(
            cpuPercent = cpu,
            ramUsedBytes = max(0L, total - available),
            ramTotalBytes = total,
            swapUsedBytes = max(0L, swapTotal - swapFree),
            swapTotalBytes = swapTotal,
            load1 = load.trim().substringBefore(' ').toFloatOrNull() ?: 0f,
            uptimeSeconds = uptime.trim().substringBefore(' ').toDoubleOrNull()?.toLong() ?: 0L,
            soc = soc,
            architecture = arch,
            coreCount = coreCount,
            cpuTemperatureC = tempValue,
        )
    }

    private fun cpuPercent(current: CpuCounters?): Float {
        if (current == null) return 0f
        val previous = previousCpu
        previousCpu = current
        if (previous == null) return 0f
        val totalDelta = current.total - previous.total
        val idleDelta = current.idle - previous.idle
        if (totalDelta <= 0L) return 0f
        return ((totalDelta - idleDelta) * 100f / totalDelta).coerceIn(0f, 100f)
    }

    private fun parseProcesses(
        raw: String,
        network: Map<Int, YTaskNetworkRow>,
    ): List<YTaskProcess> {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            if (parts.size < 5) return@mapNotNull null
            val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val ppid = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val uidText = parts.getOrNull(2).orEmpty()
            val uid = uidText.toIntOrNull() ?: android.os.Process.getUidForName(uidText)
            val rss = parts.getOrNull(3)?.toLongOrNull() ?: 0L
            val name = parts.getOrNull(4).orEmpty()
            val command = parts.getOrNull(5).orEmpty().ifBlank { name }
            val packages = runCatching {
                packageManager.getPackagesForUid(uid)?.toList().orEmpty()
            }.getOrDefault(emptyList())
            val packageName = packages.firstOrNull { pkg ->
                command == pkg || command.startsWith("$pkg:")
            } ?: packages.firstOrNull()
            val info = packageName?.let { pkg ->
                runCatching {
                    packageManager.getApplicationInfo(pkg, 0)
                }.getOrNull()
            }
            val label = info?.let {
                runCatching {
                    packageManager.getApplicationLabel(it).toString()
                }.getOrNull()
            }
            val kind = when {
                info == null -> YTaskProcessKind.Linux
                info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ->
                    YTaskProcessKind.SystemApp
                else -> YTaskProcessKind.UserApp
            }
            val speed = network[uid]
            YTaskProcess(
                pid = pid,
                ppid = ppid,
                uid = uid,
                rssKb = rss,
                cpuPercent = 0f,
                name = name,
                command = command,
                packageName = packageName,
                appLabel = label,
                kind = kind,
                rxBytesPerSecond = speed?.rxBytesPerSecond ?: 0L,
                txBytesPerSecond = speed?.txBytesPerSecond ?: 0L,
            )
        }
    }

    private fun parseNetwork(raw: String): List<YTaskNetworkRow> {
        val totals = linkedMapOf<Int, Pair<Long, Long>>()
        raw.lineSequence().drop(1).forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size < 8) return@forEach
            val uid = p[3].toIntOrNull() ?: return@forEach
            val rx = p[5].toLongOrNull() ?: return@forEach
            val tx = p[7].toLongOrNull() ?: return@forEach
            val old = totals[uid] ?: (0L to 0L)
            totals[uid] = old.first + rx to old.second + tx
        }
        val now = System.currentTimeMillis()
        val seconds =
            if (previousNetworkAt > 0L) {
                ((now - previousNetworkAt) / 1000.0).coerceAtLeast(0.25)
            } else {
                1.0
            }
        val result = totals.map { (uid, total) ->
            val old = previousNetwork[uid] ?: total
            val rx = ((total.first - old.first).coerceAtLeast(0L) / seconds).toLong()
            val tx = ((total.second - old.second).coerceAtLeast(0L) / seconds).toLong()
            val packages = runCatching {
                packageManager.getPackagesForUid(uid)?.toList().orEmpty()
            }.getOrDefault(emptyList())
            val label = packages.firstOrNull()?.let { pkg ->
                runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0),
                    ).toString()
                }.getOrNull()
            } ?: "UID $uid"
            YTaskNetworkRow(
                uid = uid,
                label = label,
                packageNames = packages,
                rxBytesPerSecond = rx,
                txBytesPerSecond = tx,
            )
        }.sortedByDescending { it.rxBytesPerSecond + it.txBytesPerSecond }
        previousNetwork = totals
        previousNetworkAt = now
        return result
    }

    private fun parseGpu(raw: String): YTaskGpuSnapshot {
        val busyPart = raw.substringBefore("__FREQ__").trim()
        val freqLines = raw.substringAfter("__FREQ__", "")
            .lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val busy = busyPart.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        val usage = if (busy.size >= 2 && busy[1] > 0L) {
            (busy[0] * 100f / busy[1]).coerceIn(0f, 100f)
        } else null
        return YTaskGpuSnapshot(
            usagePercent = usage,
            currentHz = freqLines.getOrNull(0)?.toLongOrNull(),
            minHz = freqLines.getOrNull(1)?.toLongOrNull(),
            maxHz = freqLines.getOrNull(2)?.toLongOrNull(),
        )
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private data class CpuCounters(
        val values: List<Long>,
    ) {
        val total: Long get() = values.sum()
        val idle: Long get() = (values.getOrNull(3) ?: 0L) + (values.getOrNull(4) ?: 0L)
    }

    companion object {
        private const val TAG = "YSuite/YTaskManager"
    }
}
