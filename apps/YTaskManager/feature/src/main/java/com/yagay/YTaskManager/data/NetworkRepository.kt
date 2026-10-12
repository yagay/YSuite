package com.yagay.YTaskManager.data

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.yagay.suite.api.InstalledAppCatalog
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import com.yagay.YTaskManager.model.NetworkEntry
import com.yagay.YTaskManager.model.NetworkSnapshot
import com.yagay.YTaskManager.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class NetworkRepository(
    private val application: Application,
    private val shell: RootShell,
) {
    private var packageCache: Map<Int, List<PackageIdentity>> = emptyMap()
    private var packageCacheTime = 0L
    private var previous: Map<Int, Counter> = emptyMap()
    private var previousNanos: Long = 0L
    private var previousBackend: String? = null
    private var preferredRootProbe: Pair<String, String>? = null
    private var lastFullProbeMs = 0L
    /** Last successful root UID map probe, never a device-total disguised as UID. */
    private var lastSourceError: String = "No root UID network counters discovered"

    suspend fun read(knownUids: Set<Int>): NetworkSnapshot = withContext(Dispatchers.IO) {
        refreshPackageCacheIfNeeded()
        val nowNanos = SystemClock.elapsedRealtimeNanos()

        val rootSample = readRootUidCounters()
        val backend: String
        val current: Map<Int, Counter>
        var uidSource = false

        if (rootSample.counters.isNotEmpty()) {
            backend = rootSample.backend
            current = rootSample.counters
            uidSource = true
        } else {
            val qtaguidCounters = readRootQtaguid()
            if (qtaguidCounters.isNotEmpty()) {
                backend = "Root qtaguid"
                current = qtaguidCounters
                uidSource = true
            } else {
                // Android N+ public TrafficStats cannot provide arbitrary other UIDs.
                // Keep this only as an explicit diagnostic fallback.
                // Global traffic counters can work even when the OEM hides
                // cross-UID netstats. UID -1 marks a synthetic device total,
                // not traffic from a specific application.
                val totalRx = TrafficStats.getTotalRxBytes()
                val totalTx = TrafficStats.getTotalTxBytes()
                if (totalRx >= 0L && totalTx >= 0L) {
                    backend = "Device total only — UID network data unavailable ($lastSourceError)"
                    current = mapOf(-1 to Counter(totalRx, totalTx))
                } else {
                    backend = "TrafficStats (own UID only)"
                    val uid = Process.myUid()
                    val rx = TrafficStats.getUidRxBytes(uid)
                    val tx = TrafficStats.getUidTxBytes(uid)
                    current = if (rx >= 0L && tx >= 0L) mapOf(uid to Counter(rx, tx)) else emptyMap()
                }
            }
        }

        // Never compare counters from different backends. Their accounting domains can differ,
        // which otherwise produces either huge spikes or a permanent stream of clamped zeroes.
        if (previousBackend != null && previousBackend != backend) {
            previous = emptyMap()
            previousNanos = 0L
        }

        val elapsedSeconds = if (previousNanos > 0L) {
            (nowNanos - previousNanos).coerceAtLeast(1L) / 1_000_000_000.0
        } else 0.0

        val uidFilter = if (knownUids.isEmpty()) null else knownUids
        val observedUids: Set<Int> = if (uidSource && elapsedSeconds > 0.0)
            current.keys.filterTo(HashSet()) { it >= 0 && previous.containsKey(it) }
        else emptySet()
        val entries = if (elapsedSeconds <= 0.0) {
            emptyList()
        } else {
            current.mapNotNull { (uid, value) ->
                if (uid >= 0 && uidFilter != null && uid !in uidFilter && uid !in packageCache) return@mapNotNull null

                val old = previous[uid] ?: return@mapNotNull null
                val deltaRx = value.rx - old.rx
                val deltaTx = value.tx - old.tx
                // Counter reset/rollover: skip this sample instead of showing a false speed.
                if (deltaRx < 0L || deltaTx < 0L) return@mapNotNull null

                val rx = max(0L, (deltaRx / elapsedSeconds).toLong())
                val tx = max(0L, (deltaTx / elapsedSeconds).toLong())
                if (rx == 0L && tx == 0L) return@mapNotNull null

                val identities = packageCache[uid].orEmpty()
                val primary = identities.firstOrNull()
                NetworkEntry(
                    uid = uid,
                    label = if (uid == -1) "Device total (all interfaces)" else primary?.label ?: primary?.packageName ?: "UID $uid",
                    packageNames = identities.map { it.packageName },
                    system = identities.isEmpty() || identities.any { it.system },
                    rxBytesPerSecond = rx,
                    txBytesPerSecond = tx,
                )
            }.sortedByDescending { max(it.rxBytesPerSecond, it.txBytesPerSecond) }
        }

        previous = current
        previousNanos = nowNanos
        previousBackend = backend

        NetworkSnapshot(
            entries = entries,
            totalRxBytesPerSecond = entries.sumOf { it.rxBytesPerSecond },
            totalTxBytesPerSecond = entries.sumOf { it.txBytesPerSecond },
            backend = backend,
            uidCountersAvailable = uidSource,
            observedUids = observedUids,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * mAppUidStatsMap used to be dumped by netd's TrafficController. Newer Android
     * moved the map dump to NetworkStatsService, so probe the modern location first
     * and keep the old locations for OEM/backward compatibility.
     */
    private suspend fun readRootUidCounters(): RootSample {
        val probes = listOf(
            // Linux pinned map contents are only available when bpftool is
            // present and the OEM/SELinux policy permits read access.
            "Root pinned eBPF (bpftool)" to
                "command -v bpftool >/dev/null 2>&1 && bpftool -j map dump pinned /sys/fs/bpf/netd_shared/map_netd_app_uid_stats_map 2>/dev/null",
            "Root netstats eBPF" to "dumpsys netstats 2>/dev/null",
            "Root connectivity eBPF" to "dumpsys connectivity trafficcontroller 2>/dev/null",
            "Root netd eBPF" to "dumpsys netd trafficcontroller 2>/dev/null",
        )
        val now = SystemClock.elapsedRealtime()
        val selected = preferredRootProbe?.let { listOf(it) } ?: if (
            lastFullProbeMs != 0L && now - lastFullProbeMs < 60_000L
        ) emptyList() else probes
        // Do not move the retry deadline when the cached "unsupported" answer
        // is reused. Otherwise every 5s poll postpones re-probing indefinitely.
        if (selected.isEmpty()) {
            return RootSample("No cross-UID root counters", emptyMap())
        }
        if (preferredRootProbe == null) lastFullProbeMs = now
        for ((name, command) in selected) {
            val raw = runCatching { shell.execute(command, 4_000).stdout }.getOrDefault("")
            val parsed = if (name.startsWith("Root pinned eBPF")) parseBpfMap(raw)
                else parseAppUidStats(raw)
            if (parsed.isNotEmpty()) {
                preferredRootProbe = name to command
                return RootSample(name, parsed)
            }
        }
        preferredRootProbe = null
        lastFullProbeMs = now
        lastSourceError = "eBPF map unavailable or unreadable; netstats UID map absent"
        return RootSample("No cross-UID root counters", emptyMap())
    }

    /**
     * Read-only decoder of the AOSP netd app_uid_stats_map.
     * Key is little-endian uint32 UID; StatsValue is four uint64s:
     * rxPackets, rxBytes, txPackets, txBytes. No kernel writes.
     * bpftool can emit byte arrays or BTF named-object fields.
     */
    private fun parseBpfMap(raw: String): Map<Int, Counter> {
        if (!raw.trimStart().startsWith("[")) return emptyMap()
        return try {
            val records = JSONArray(raw)
            val parsed = HashMap<Int, Counter>()
            for (i in 0 until records.length()) {
                val item = records.optJSONObject(i) ?: continue
                val key = item.opt("key")
                val uid = when (key) {
                    is JSONArray -> readUnsignedLittleEndian(key, 0, 4)?.toInt()
                    is JSONObject -> key.optInt("uid", -1)
                    else -> -1
                } ?: -1
                if (uid < 0) continue
                val value = item.opt("value")
                val rx: Long
                val tx: Long
                when (value) {
                    is JSONArray -> {
                        if (value.length() < 32) continue
                        rx = readUnsignedLittleEndian(value, 8, 8) ?: continue
                        tx = readUnsignedLittleEndian(value, 24, 8) ?: continue
                    }
                    is JSONObject -> {
                        rx = value.optLong("rxBytes", value.optLong("rx_bytes", -1L))
                        tx = value.optLong("txBytes", value.optLong("tx_bytes", -1L))
                    }
                    else -> continue
                }
                if (rx < 0L || tx < 0L) continue
                parsed[uid] = Counter(rx, tx)
            }
            parsed
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readUnsignedLittleEndian(bytes: JSONArray, offset: Int, size: Int): Long? {
        if (bytes.length() < offset + size) return null
        var result = 0L
        for (index in 0 until size) {
            val octet = bytes.optInt(offset + index, -1)
            if (octet !in 0..255) return null
            result = result or ((octet.toLong() and 0xffL) shl (index * 8))
        }
        return result.takeIf { it >= 0L }
    }

    /**
     * Expected map body (indentation and punctuation around the section vary by release/OEM):
     * uid rxBytes rxPackets txBytes txPackets
     * 10234 12345 100 67890 120
     */
    private fun parseAppUidStats(raw: String): Map<Int, Counter> {
        if (raw.isBlank() || !raw.contains("mAppUidStatsMap")) return emptyMap()

        val result = HashMap<Int, Counter>()
        var inMap = false
        var sawData = false

        for (original in raw.lineSequence()) {
            val line = original.trim()

            if (line.contains("mAppUidStatsMap")) {
                inMap = true
                sawData = false
                continue
            }
            if (!inMap) continue

            if (line.isBlank()) {
                if (sawData) break
                continue
            }

            // A new named dump section after data means the app-uid map is finished.
            if (sawData && line.endsWith(":") && line.firstOrNull()?.isLetter() == true) break
            if (line.startsWith("uid ", ignoreCase = true) ||
                line.startsWith("BPF map", ignoreCase = true) ||
                line.contains("status:", ignoreCase = true)
            ) continue

            val parts = line.split(Regex("\\s+"))
            if (parts.size < 5) {
                if (sawData && line.firstOrNull()?.isLetter() == true) break
                continue
            }

            val uid = parts[0].trimEnd(':', ',').toIntOrNull() ?: continue
            val rx = parts[1].trimEnd(',').toLongOrNull() ?: continue
            val tx = parts[3].trimEnd(',').toLongOrNull() ?: continue
            result[uid] = Counter(rx, tx)
            sawData = true
        }
        return result
    }

    /** Legacy fallback for older/vendor kernels that still expose xt_qtaguid. */
    private suspend fun readRootQtaguid(): Map<Int, Counter> {
        val raw = runCatching {
            shell.execute(
                "if [ -r /proc/net/xt_qtaguid/stats ]; then cat /proc/net/xt_qtaguid/stats; fi",
                4_000,
            ).stdout
        }.getOrDefault("")
        if (raw.isBlank() || !raw.contains("uid_tag_int")) return emptyMap()

        val result = HashMap<Int, Counter>()
        raw.lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 8) return@forEach
            if (parts[2] != "0x0") return@forEach
            val uid = parts[3].toIntOrNull() ?: return@forEach
            val rx = parts[5].toLongOrNull() ?: return@forEach
            val tx = parts[7].toLongOrNull() ?: return@forEach
            val old = result[uid]
            result[uid] = Counter(
                rx = (old?.rx ?: 0L) + rx,
                tx = (old?.tx ?: 0L) + tx,
            )
        }
        return result
    }

    private fun refreshPackageCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (packageCache.isNotEmpty() && now - packageCacheTime < 60_000L) return

        val apps = InstalledAppCatalog.list(application, PackageManager.GET_META_DATA)
        val map = HashMap<Int, MutableList<PackageIdentity>>()
        apps.forEach { app ->
            map.getOrPut(app.uid) { mutableListOf() } += PackageIdentity(
                packageName = app.packageName,
                label = app.label,
                system = app.system ||
                    (app.info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }
        packageCache = map
        packageCacheTime = now
    }

    private data class RootSample(val backend: String, val counters: Map<Int, Counter>)
    private data class Counter(val rx: Long, val tx: Long)

    private data class PackageIdentity(
        val packageName: String,
        val label: String,
        val system: Boolean,
    )
}
