package com.yagay.ydiag.model

import java.util.Locale

data class DiagnosticOption(
    val id: String,
    val title: String,
    val description: String,
    val category: DiagnosticCategory,
    val recommendation: Recommendation,
    val load: LoadLevel,
    val defaultEnabled: Boolean = false,
)

data class DiagnosticPreset(
    val id: String,
    val title: String,
    val description: String,
    val options: Set<String>,
)

private fun t(en: String, zh: String): String =
    if (Locale.getDefault().language.equals("zh", true)) zh else en

object DiagnosticCatalog {
    val options = listOf(
        DiagnosticOption("logcat", "Logcat", t("Common app and system runtime logs", "应用与系统最常用运行日志"), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
        DiagnosticOption("crash", "Crash", t("Java and Kotlin crashes and the crash buffer", "Java 和 Kotlin 崩溃与 crash buffer"), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
        DiagnosticOption("exit_info", "ApplicationExitInfo", t("Identifies crash, ANR, OOM, signal, and system process termination reasons", "判断崩溃、ANR、OOM、Signal 与系统杀进程原因"), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
        DiagnosticOption("anr", "ANR", t("Analyzes freezes, main-thread stalls, and ANR traces", "分析卡死、主线程无响应与 ANR traces"), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
        DiagnosticOption("events", t("System events", "系统 Events"), t("System event buffers such as ActivityManager", "ActivityManager 等系统事件缓冲"), DiagnosticCategory.SYSTEM, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("process", t("Process relation", "进程关联"), t("Relates the target app main process, child processes, and UID", "关联目标应用的主进程、子进程与 UID"), DiagnosticCategory.SYSTEM, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),

        DiagnosticOption("lsposed_status", t("LSPosed module status", "LSPosed 模块状态"), t("Module, scope, and runtime target state", "模块、Scope 与运行目标状态"), DiagnosticCategory.LSPOSED, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
        DiagnosticOption("hook_health", t("Hook health check", "Hook 健康检查"), t("Records class, method, hook installation, and hook hits", "记录 Class、Method、Hook 安装和命中"), DiagnosticCategory.LSPOSED, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
        DiagnosticOption("lifecycle", t("Lifecycle", "生命周期"), t("Important Activity lifecycle events", "Activity 生命周期关键事件"), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("intent", "Intent", t("Important startActivity and related Intent calls", "startActivity 等关键 Intent 调用"), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("method_trace", t("Method trace", "方法调用 Trace"), t("Entry point for deep method tracing", "深度方法调用追踪入口"), DiagnosticCategory.LSPOSED, Recommendation.DEEP, LoadLevel.HIGH),
        DiagnosticOption("stack_trace", t("Full call stack", "完整调用栈"), t("Records stack traces for exceptions or deep events", "异常或深度事件记录 StackTrace"), DiagnosticCategory.LSPOSED, Recommendation.DEEP, LoadLevel.VERY_HIGH),

        DiagnosticOption("webview", "WebView", t("Page loading, renderer, and file chooser events", "页面加载、Renderer 与文件选择相关事件"), DiagnosticCategory.WEBVIEW, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("network", t("Network metadata", "网络元数据"), t("DNS, socket, TLS, and URL error metadata without message bodies", "DNS、Socket、TLS、URL 错误元数据，不记录消息正文"), DiagnosticCategory.NETWORK, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
        DiagnosticOption("file_io", t("File access", "文件访问"), t("Important file open and write failures and paths", "关键文件打开、写入失败与路径"), DiagnosticCategory.FILES, Recommendation.DEEP, LoadLevel.HIGH),
        DiagnosticOption("binder", "Binder", t("Binder call failures and system-service errors", "Binder 调用异常与系统服务错误"), DiagnosticCategory.SYSTEM, Recommendation.DEEP, LoadLevel.HIGH),

        DiagnosticOption("memory", t("Memory", "内存"), t("PSS, RSS, meminfo, and OOM evidence", "PSS、RSS、meminfo 与 OOM 证据"), DiagnosticCategory.PERFORMANCE, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("perfetto", "Perfetto", t("System scheduling and performance traces for reproducing stalls", "系统调度与性能 Trace，适合卡顿复现"), DiagnosticCategory.PERFORMANCE, Recommendation.DEEP, LoadLevel.HIGH),
        DiagnosticOption("kernel", "Kernel and dmesg", t("Kernel and driver logs", "内核与驱动日志"), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
        DiagnosticOption("selinux", "SELinux denial", t("Permission denials and avc denied events", "权限拒绝与 avc denied"), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.LOW),
        DiagnosticOption("root_module", t("Root module logs", "Root 模块日志"), t("Magisk and KernelSU module-related logs", "Magisk、KernelSU 模块相关日志"), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
        DiagnosticOption("lsposed_log", t("Full LSPosed logs", "LSPosed 完整日志"), t("LSPosed framework and module log files", "LSPosed 框架与模块日志文件"), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
        DiagnosticOption("tombstone", "Tombstone", t("Native SIGSEGV and SIGABRT crash backtraces", "Native SIGSEGV、SIGABRT 崩溃回溯"), DiagnosticCategory.NATIVE, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
        DiagnosticOption("native", "Native Crash", t("Native clues from crash_dump, linker, libc, and related sources", "crash_dump、linker、libc 等 Native 线索"), DiagnosticCategory.NATIVE, Recommendation.ON_DEMAND, LoadLevel.LOW),
    )

    private fun ids(vararg value: String) = value.toSet()

    val presets = listOf(
        DiagnosticPreset("quick", t("Quick diagnostics", "快速诊断"), t("Low load for everyday use", "低负载，适合日常使用"),
            ids("logcat","crash","exit_info","anr","process","lsposed_status","hook_health","tombstone")),
        DiagnosticPreset("crash", t("Crash diagnostics", "崩溃诊断"), t("Java, Native, and system exit causes", "Java、Native 和系统退出原因"),
            ids("logcat","crash","exit_info","anr","process","tombstone","native","lsposed_status")),
        DiagnosticPreset("hook", t("Hook diagnostics", "Hook 诊断"), t("For ineffective LSPosed features or hooks that are not hit", "LSPosed 功能无效或 Hook 未命中"),
            ids("logcat","process","lsposed_status","hook_health","lifecycle","intent","lsposed_log")),
        DiagnosticPreset("webview", t("WebView diagnostics", "WebView 诊断"), t("Page, upload, renderer, and callback problems", "页面、上传、Renderer 与回调问题"),
            ids("logcat","crash","exit_info","process","lsposed_status","hook_health","lifecycle","intent","webview","network")),
        DiagnosticPreset("freeze", t("Freeze diagnostics", "卡顿诊断"), t("ANR, threads, memory, and Perfetto", "ANR、线程、内存与 Perfetto"),
            ids("logcat","anr","exit_info","process","memory","perfetto","binder")),
        DiagnosticPreset("root", t("Root diagnostics", "Root 诊断"), t("Root, SELinux, kernel, and module problems", "Root、SELinux、内核与模块问题"),
            ids("logcat","crash","exit_info","process","kernel","selinux","root_module","lsposed_log")),
        DiagnosticPreset("complete", t("Complete diagnostics", "完整诊断"), t("Enables all evidence sources with a higher load", "启用全部证据源，负载较高"), options.map { it.id }.toSet()),
    )

    val defaultEnabled: Set<String> = options.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.id }
}
