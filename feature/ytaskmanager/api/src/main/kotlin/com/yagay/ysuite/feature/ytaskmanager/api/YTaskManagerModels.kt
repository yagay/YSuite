package com.yagay.ysuite.feature.ytaskmanager.api

enum class YTaskProcessKind {
    UserApp,
    SystemApp,
    Linux,
}

enum class YTaskProcessSort {
    Memory,
    Cpu,
    Download,
    Upload,
    Name,
    Pid,
}

enum class YTaskPage {
    Processes,
    Resources,
    Network,
}

data class YTaskCpuCore(
    val core: Int,
    val minKHz: Long? = null,
    val currentKHz: Long? = null,
    val maxKHz: Long? = null,
)

data class YTaskSystemSnapshot(
    val cpuPercent: Float = 0f,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val ramAvailableBytes: Long = 0L,
    val cachedBytes: Long = 0L,
    val buffersBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val load1: Float = 0f,
    val uptimeSeconds: Long = 0L,
    val soc: String = "",
    val architecture: String = "",
    val abi: String = "",
    val governor: String = "",
    val coreCount: Int = 0,
    val cpuTemperatureC: Float? = null,
    val cpuCores: List<YTaskCpuCore> = emptyList(),
)

data class YTaskGpuSnapshot(
    val vendor: String? = null,
    val renderer: String? = null,
    val openGlVersion: String? = null,
    val vulkanSupported: Boolean = false,
    val vulkanApiVersion: String? = null,
    val usagePercent: Float? = null,
    val currentHz: Long? = null,
    val minHz: Long? = null,
    val maxHz: Long? = null,
)

data class YTaskProcess(
    val pid: Int,
    val ppid: Int,
    val uid: Int,
    val rssKb: Long,
    val virtualMemoryKb: Long = 0L,
    val cpuPercent: Float,
    val state: String = "",
    val nice: Int = 0,
    val threads: Int = 0,
    val elapsedTimeMillis: Long = 0L,
    val oomScoreAdj: Int? = null,
    val isForeground: Boolean = false,
    val name: String,
    val command: String,
    val executablePath: String? = null,
    val cgroup: String? = null,
    val packageName: String? = null,
    val appLabel: String? = null,
    val kind: YTaskProcessKind = YTaskProcessKind.Linux,
    val rxBytesPerSecond: Long = 0L,
    val txBytesPerSecond: Long = 0L,
) {
    val displayName: String
        get() = appLabel?.takeIf { it.isNotBlank() }
            ?: packageName?.takeIf { it.isNotBlank() }
            ?: name
}

data class YTaskNetworkRow(
    val uid: Int,
    val label: String,
    val packageNames: List<String> = emptyList(),
    val rxBytesPerSecond: Long = 0L,
    val txBytesPerSecond: Long = 0L,
)

data class YTaskSnapshot(
    val system: YTaskSystemSnapshot = YTaskSystemSnapshot(),
    val gpu: YTaskGpuSnapshot = YTaskGpuSnapshot(),
    val processes: List<YTaskProcess> = emptyList(),
    val network: List<YTaskNetworkRow> = emptyList(),
    val networkBackend: String = "",
)
