package com.yagay.ysuite.feature.ydiag.api

enum class YDiagCategory {
    Basic,
    Root,
    Lsposed,
    System,
    WebView,
    Network,
    Performance,
    Files,
    Native,
}

enum class YDiagRecommendation {
    Recommended,
    OnDemand,
    Deep,
}

enum class YDiagLoad {
    VeryLow,
    Low,
    Medium,
    High,
    VeryHigh,
}

enum class YDiagSeverity {
    Info,
    Warning,
    Error,
    Fatal,
}

data class YDiagOption(
    val id: String,
    val category: YDiagCategory,
    val recommendation: YDiagRecommendation,
    val load: YDiagLoad,
    val defaultEnabled: Boolean = false,
)

data class YDiagPreset(
    val id: String,
    val options: Set<String>,
)

data class YDiagApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val system: Boolean,
)

data class YDiagEvent(
    val id: String,
    val timestampMillis: Long,
    val optionId: String,
    val severity: YDiagSeverity,
    val title: String,
    val detail: String,
)

object YDiagCatalog {
    val options =
        listOf(
            YDiagOption("logcat", YDiagCategory.Basic, YDiagRecommendation.Recommended, YDiagLoad.Low, true),
            YDiagOption("crash", YDiagCategory.Basic, YDiagRecommendation.Recommended, YDiagLoad.VeryLow, true),
            YDiagOption("exit_info", YDiagCategory.Basic, YDiagRecommendation.Recommended, YDiagLoad.VeryLow, true),
            YDiagOption("anr", YDiagCategory.Basic, YDiagRecommendation.Recommended, YDiagLoad.Low, true),
            YDiagOption("events", YDiagCategory.System, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("process", YDiagCategory.System, YDiagRecommendation.Recommended, YDiagLoad.VeryLow, true),
            YDiagOption("lsposed_status", YDiagCategory.Lsposed, YDiagRecommendation.Recommended, YDiagLoad.VeryLow, true),
            YDiagOption("hook_health", YDiagCategory.Lsposed, YDiagRecommendation.Recommended, YDiagLoad.Low, true),
            YDiagOption("lifecycle", YDiagCategory.Lsposed, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("intent", YDiagCategory.Lsposed, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("method_trace", YDiagCategory.Lsposed, YDiagRecommendation.Deep, YDiagLoad.High),
            YDiagOption("stack_trace", YDiagCategory.Lsposed, YDiagRecommendation.Deep, YDiagLoad.VeryHigh),
            YDiagOption("webview", YDiagCategory.WebView, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("network", YDiagCategory.Network, YDiagRecommendation.OnDemand, YDiagLoad.Medium),
            YDiagOption("file_io", YDiagCategory.Files, YDiagRecommendation.Deep, YDiagLoad.High),
            YDiagOption("binder", YDiagCategory.System, YDiagRecommendation.Deep, YDiagLoad.High),
            YDiagOption("memory", YDiagCategory.Performance, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("perfetto", YDiagCategory.Performance, YDiagRecommendation.Deep, YDiagLoad.High),
            YDiagOption("kernel", YDiagCategory.Root, YDiagRecommendation.OnDemand, YDiagLoad.Medium),
            YDiagOption("selinux", YDiagCategory.Root, YDiagRecommendation.OnDemand, YDiagLoad.Low),
            YDiagOption("root_module", YDiagCategory.Root, YDiagRecommendation.OnDemand, YDiagLoad.Medium),
            YDiagOption("lsposed_log", YDiagCategory.Lsposed, YDiagRecommendation.OnDemand, YDiagLoad.Medium),
            YDiagOption("tombstone", YDiagCategory.Native, YDiagRecommendation.Recommended, YDiagLoad.Low, true),
            YDiagOption("native", YDiagCategory.Native, YDiagRecommendation.OnDemand, YDiagLoad.Low),
        )

    val defaultEnabled =
        options.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.id }

    val presets =
        listOf(
            YDiagPreset(
                "quick",
                setOf(
                    "logcat", "crash", "exit_info", "anr",
                    "process", "lsposed_status", "hook_health", "tombstone",
                ),
            ),
            YDiagPreset(
                "crash",
                setOf(
                    "logcat", "crash", "exit_info", "anr",
                    "process", "tombstone", "native", "lsposed_status",
                ),
            ),
            YDiagPreset(
                "hook",
                setOf(
                    "logcat", "process", "lsposed_status",
                    "hook_health", "lifecycle", "intent", "lsposed_log",
                ),
            ),
            YDiagPreset(
                "webview",
                setOf("logcat", "crash", "exit_info", "process",
                    "lsposed_status", "hook_health", "lifecycle", "intent", "webview", "network"),
            ),
            YDiagPreset(
                "freeze",
                setOf("logcat", "anr", "exit_info", "process", "memory", "perfetto", "binder"),
            ),
            YDiagPreset(
                "root",
                setOf(
                    "logcat", "crash", "exit_info", "process",
                    "kernel", "selinux", "root_module", "lsposed_log",
                ),
            ),
            YDiagPreset(
                "complete",
                options.mapTo(linkedSetOf()) { it.id },
            ),
        )
}
