package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import android.content.pm.ApplicationInfo
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskNetworkRow
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessKind
import kotlin.math.max

internal class YTaskProcessSampler(
    context: Context,
    private val shell: YTaskRootRunner,
) {
    private val packageManager = context.packageManager
    private var previousTicks = emptyMap<Int, Long>()
    private var previousAt = 0L
    private var clockTicks = 100L

    suspend fun read(
        network: Map<Int, YTaskNetworkRow>,
    ): List<YTaskProcess> {
        val command =
            """
            HZ=$(getconf CLK_TCK 2>/dev/null || echo 100)
            IFS=' ' read -r UP _ < /proc/uptime 2>/dev/null || UP=0
            echo "__META__|@HZ|@UP"
            for p in /proc/[0-9]*; do
              pid=$(basename "@p")
              IFS= read -r statline < "@p/stat" 2>/dev/null || continue
              uid=-1
              rss=0
              vmsize=0
              threads=0
              while IFS= read -r line; do
                case "@line" in
                  Uid:*) set -- @line; uid=@2 ;;
                  VmRSS:*) set -- @line; rss=@2 ;;
                  VmSize:*) set -- @line; vmsize=@2 ;;
                  Threads:*) set -- @line; threads=@2 ;;
                esac
              done < "@p/status" 2>/dev/null
              oom=0
              IFS= read -r oom < "@p/oom_score_adj" 2>/dev/null || oom=0
              cmd=''
              IFS= read -r -d '' cmd < "@p/cmdline" 2>/dev/null || true
              [ -n "@cmd" ] || cmd='-'
              printf '__PROC__|%s|%s|%s|%s|%s|%s|%s\n' "@pid" "@uid" "@rss" "@vmsize" "@threads" "@oom" "@cmd"
              printf '__PSTAT__|%s\n' "@statline"
            done
            """.trimIndent()
                .replace('@', 36.toChar())
        return parse(
            shell.text(command, 12_000L),
            network,
        )
    }

    suspend fun details(
        process: YTaskProcess,
    ): YTaskProcess {
        if (process.pid <= 0) return process
        val command =
            """
            p=/proc/@PID@
            [ -d "@p" ] || exit 1
            exe=$(readlink "@p/exe" 2>/dev/null | tr '\t\r\n|' '    ')
            cgroup=$(tr '\n\t\r|' '    ' < "@p/cgroup" 2>/dev/null)
            printf '%s|%s\n' "@exe" "@cgroup"
            """.trimIndent()
                .replace("@PID@", process.pid.toString())
                .replace('@', 36.toChar())
        val raw =
            runCatching {
                shell.text(command, 4_000L)
            }.getOrDefault("")
        if (raw.isBlank()) return process
        val values =
            raw.lineSequence().last()
                .split('|', limit = 2)
        return process.copy(
            executablePath =
                values.getOrNull(0)?.takeIf { it.isNotBlank() },
            cgroup =
                values.getOrNull(1)?.takeIf { it.isNotBlank() },
        )
    }

    private fun parse(
        raw: String,
        network: Map<Int, YTaskNetworkRow>,
    ): List<YTaskProcess> {
        val lines = raw.lineSequence().toList()
        val meta =
            lines.firstOrNull { it.startsWith("__META__|") }
                ?.split('|')
        clockTicks =
            meta?.getOrNull(1)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 100L
        val uptime =
            meta?.getOrNull(2)?.toDoubleOrNull()
                ?: 0.0

        val now = System.currentTimeMillis()
        val sampleMs =
            if (previousAt > 0L) {
                max(1L, now - previousAt)
            } else {
                0L
            }
        val newTicks = HashMap<Int, Long>()
        val result = ArrayList<YTaskProcess>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("__PROC__|")) {
                index++
                continue
            }
            val statLine = lines.getOrNull(index + 1)
            if (
                statLine == null ||
                !statLine.startsWith("__PSTAT__|")
            ) {
                index++
                continue
            }

            val values = line.split('|', limit = 8)
            val pid = values.getOrNull(1)?.toIntOrNull()
            val stat =
                parseStat(statLine.removePrefix("__PSTAT__|"))
            if (pid == null || stat == null || stat.pid != pid) {
                index += 2
                continue
            }

            val uid = values.getOrNull(2)?.toIntOrNull() ?: -1
            val rss = values.getOrNull(3)?.toLongOrNull() ?: 0L
            val virtualMemory =
                values.getOrNull(4)?.toLongOrNull() ?: 0L
            val threads =
                values.getOrNull(5)?.toIntOrNull() ?: stat.threads
            val oom = values.getOrNull(6)?.toIntOrNull()
            val command =
                values.getOrNull(7)
                    .orEmpty()
                    .takeUnless { it == "-" }
                    .orEmpty()
                    .ifBlank { stat.name }

            val totalTicks = stat.userTicks + stat.systemTicks
            newTicks[pid] = totalTicks
            val oldTicks = previousTicks[pid]
            val cpu =
                if (oldTicks != null && sampleMs > 0L) {
                    val delta = max(0L, totalTicks - oldTicks)
                    (
                        (delta.toDouble() / clockTicks.toDouble()) /
                            (sampleMs.toDouble() / 1000.0) *
                            100.0
                        ).toFloat().coerceAtLeast(0f)
                } else {
                    0f
                }

            val elapsedSeconds =
                max(
                    0.0,
                    uptime -
                        stat.startTicks.toDouble() /
                        clockTicks.toDouble(),
                )
            val packages =
                runCatching {
                    packageManager.getPackagesForUid(uid)
                        ?.toList()
                        .orEmpty()
                }.getOrDefault(emptyList())
            val packageName =
                packages.firstOrNull {
                    command == it || command.startsWith("$it:")
                } ?: packages.firstOrNull()
            val info =
                packageName?.let { pkg ->
                    runCatching {
                        packageManager.getApplicationInfo(pkg, 0)
                    }.getOrNull()
                }
            val label =
                info?.let {
                    runCatching {
                        packageManager.getApplicationLabel(it).toString()
                    }.getOrNull()
                }
            val system =
                info?.flags?.and(
                    ApplicationInfo.FLAG_SYSTEM or
                        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                ) != 0
            val kind =
                when {
                    info == null -> YTaskProcessKind.Linux
                    system -> YTaskProcessKind.SystemApp
                    else -> YTaskProcessKind.UserApp
                }
            val speed = network[uid]

            result +=
                YTaskProcess(
                    pid = pid,
                    ppid = stat.ppid,
                    uid = uid,
                    rssKb = rss,
                    virtualMemoryKb = virtualMemory,
                    cpuPercent = cpu,
                    state = stat.state,
                    nice = stat.nice,
                    threads = threads,
                    elapsedTimeMillis =
                        (elapsedSeconds * 1000.0).toLong(),
                    oomScoreAdj = oom,
                    isForeground = info != null && (oom ?: 1000) <= 0,
                    name = stat.name,
                    command = command,
                    packageName = packageName,
                    appLabel = label,
                    kind = kind,
                    rxBytesPerSecond =
                        speed?.rxBytesPerSecond ?: 0L,
                    txBytesPerSecond =
                        speed?.txBytesPerSecond ?: 0L,
                )
            index += 2
        }

        previousTicks = newTicks
        previousAt = now
        return result
    }

    private fun parseStat(raw: String): ProcStat? {
        val open = raw.indexOf('(')
        val close = raw.lastIndexOf(')')
        if (open <= 0 || close <= open) return null
        val pid =
            raw.substring(0, open)
                .trim()
                .toIntOrNull()
                ?: return null
        val fields =
            raw.substring(close + 1)
                .trim()
                .split(Regex("""\s+"""))
        if (fields.size < 22) return null
        return ProcStat(
            pid = pid,
            name = raw.substring(open + 1, close),
            state = fields[0],
            ppid = fields[1].toIntOrNull() ?: 0,
            userTicks = fields[11].toLongOrNull() ?: 0L,
            systemTicks = fields[12].toLongOrNull() ?: 0L,
            nice = fields[16].toIntOrNull() ?: 0,
            threads = fields[17].toIntOrNull() ?: 0,
            startTicks = fields[19].toLongOrNull() ?: 0L,
        )
    }

    private data class ProcStat(
        val pid: Int,
        val name: String,
        val state: String,
        val ppid: Int,
        val userTicks: Long,
        val systemTicks: Long,
        val nice: Int,
        val threads: Int,
        val startTicks: Long,
    )
}
