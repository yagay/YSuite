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

data class YTaskSystemSnapshot(
    val cpuPercent: Float = 0f,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val load1: Float = 0f,
    val uptimeSeconds: Long = 0L,
    val soc: String = "",
    val architecture: String = "",
    val coreCount: Int = 0,
    val cpuTemperatureC: Float? = null,
)

data class YTaskGpuSnapshot(
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
    val cpuPercent: Float,
    val name: String,
    val command: String,
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
