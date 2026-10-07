package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskNetworkRow

internal data class YTaskNetworkSample(
    val backend: String,
    val rows: List<YTaskNetworkRow>,
)

internal class YTaskNetworkSampler(
    context: Context,
    private val shell: YTaskRootRunner,
) {
    private val packageManager = context.packageManager
    private var previous = emptyMap<Int, Counter>()
    private var previousAt = 0L
    private var previousBackend = ""

    suspend fun read(): YTaskNetworkSample {
        val raw =
            shell.text(
                """
                echo __NETSTATS__
                dumpsys netstats 2>/dev/null || true
                echo __QTAGUID__
                cat /proc/net/xt_qtaguid/stats 2>/dev/null || true
                """.trimIndent(),
                12_000L,
            )
        val modern =
            parseNetstats(
                raw.substringAfter("__NETSTATS__", "")
                    .substringBefore("__QTAGUID__", ""),
            )
        val legacy =
            if (modern.isEmpty()) {
                parseQtaguid(raw.substringAfter("__QTAGUID__", ""))
            } else {
                emptyMap()
            }
        val backend =
            when {
                modern.isNotEmpty() -> "Root netstats eBPF"
                legacy.isNotEmpty() -> "Root qtaguid"
                else -> "No cross-UID counters"
            }
        val counters =
            if (modern.isNotEmpty()) modern else legacy

        if (previousBackend.isNotBlank() && previousBackend != backend) {
            previous = emptyMap()
            previousAt = 0L
        }

        val now = System.currentTimeMillis()
        val seconds =
            if (previousAt > 0L) {
                ((now - previousAt) / 1000.0).coerceAtLeast(0.25)
            } else {
                0.0
            }
        val rows =
            if (seconds <= 0.0) {
                emptyList()
            } else {
                counters.mapNotNull { (uid, value) ->
                    val old = previous[uid] ?: return@mapNotNull null
                    val deltaRx = value.rx - old.rx
                    val deltaTx = value.tx - old.tx
                    if (deltaRx < 0L || deltaTx < 0L) {
                        return@mapNotNull null
                    }
                    val rx = (deltaRx / seconds).toLong()
                    val tx = (deltaTx / seconds).toLong()
                    if (rx == 0L && tx == 0L) return@mapNotNull null
                    val packages =
                        runCatching {
                            packageManager.getPackagesForUid(uid)
                                ?.toList()
                                .orEmpty()
                        }.getOrDefault(emptyList())
                    val label =
                        packages.firstOrNull()?.let { pkg ->
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
                }.sortedByDescending {
                    it.rxBytesPerSecond + it.txBytesPerSecond
                }
            }

        previous = counters
        previousAt = now
        previousBackend = backend
        return YTaskNetworkSample(backend, rows)
    }

    private fun parseNetstats(raw: String): Map<Int, Counter> {
        if (raw.isBlank() || !raw.contains("mAppUidStatsMap")) {
            return emptyMap()
        }
        val result = HashMap<Int, Counter>()
        var inMap = false
        var sawData = false
        raw.lineSequence().forEach { original ->
            val line = original.trim()
            if (line.contains("mAppUidStatsMap")) {
                inMap = true
                sawData = false
                return@forEach
            }
            if (!inMap) return@forEach
            if (line.isBlank()) {
                if (sawData) inMap = false
                return@forEach
            }
            if (
                sawData &&
                line.endsWith(":") &&
                line.firstOrNull()?.isLetter() == true
            ) {
                inMap = false
                return@forEach
            }
            if (
                line.startsWith("uid ", true) ||
                line.startsWith("BPF map", true) ||
                line.contains("status:", true)
            ) {
                return@forEach
            }
            val p = line.split(Regex("""\s+"""))
            if (p.size < 5) return@forEach
            val uid = p[0].trimEnd(':', ',').toIntOrNull() ?: return@forEach
            val rx = p[1].trimEnd(',').toLongOrNull() ?: return@forEach
            val tx = p[3].trimEnd(',').toLongOrNull() ?: return@forEach
            result[uid] = Counter(rx, tx)
            sawData = true
        }
        return result
    }

    private fun parseQtaguid(raw: String): Map<Int, Counter> {
        if (raw.isBlank() || !raw.contains("uid_tag_int")) {
            return emptyMap()
        }
        val result = HashMap<Int, Counter>()
        raw.lineSequence().drop(1).forEach { line ->
            val p = line.trim().split(Regex("""\s+"""))
            if (p.size < 8 || p[2] != "0x0") return@forEach
            val uid = p[3].toIntOrNull() ?: return@forEach
            val rx = p[5].toLongOrNull() ?: return@forEach
            val tx = p[7].toLongOrNull() ?: return@forEach
            val old = result[uid] ?: Counter(0L, 0L)
            result[uid] = Counter(old.rx + rx, old.tx + tx)
        }
        return result
    }

    private data class Counter(
        val rx: Long,
        val tx: Long,
    )
}
