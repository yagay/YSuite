package com.yagay.ydiag.model

import com.yagay.suite.api.YLocale
import com.yagay.ydiag.R

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

private fun s(id: Int): String = YLocale.text(id)

object DiagnosticCatalog {
    val options: List<DiagnosticOption>
        get() = listOf(
            DiagnosticOption("logcat", s(R.string.ydiag_option_logcat_title), s(R.string.ydiag_option_logcat_desc), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
            DiagnosticOption("crash", s(R.string.ydiag_option_crash_title), s(R.string.ydiag_option_crash_desc), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
            DiagnosticOption("exit_info", s(R.string.ydiag_option_exit_info_title), s(R.string.ydiag_option_exit_info_desc), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
            DiagnosticOption("anr", s(R.string.ydiag_option_anr_title), s(R.string.ydiag_option_anr_desc), DiagnosticCategory.BASIC, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
            DiagnosticOption("events", s(R.string.ydiag_option_events_title), s(R.string.ydiag_option_events_desc), DiagnosticCategory.SYSTEM, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("process", s(R.string.ydiag_option_process_title), s(R.string.ydiag_option_process_desc), DiagnosticCategory.SYSTEM, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
            DiagnosticOption("lsposed_status", s(R.string.ydiag_option_lsposed_status_title), s(R.string.ydiag_option_lsposed_status_desc), DiagnosticCategory.LSPOSED, Recommendation.RECOMMENDED, LoadLevel.VERY_LOW, true),
            DiagnosticOption("hook_health", s(R.string.ydiag_option_hook_health_title), s(R.string.ydiag_option_hook_health_desc), DiagnosticCategory.LSPOSED, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
            DiagnosticOption("lifecycle", s(R.string.ydiag_option_lifecycle_title), s(R.string.ydiag_option_lifecycle_desc), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("intent", s(R.string.ydiag_option_intent_title), s(R.string.ydiag_option_intent_desc), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("method_trace", s(R.string.ydiag_option_method_trace_title), s(R.string.ydiag_option_method_trace_desc), DiagnosticCategory.LSPOSED, Recommendation.DEEP, LoadLevel.HIGH),
            DiagnosticOption("stack_trace", s(R.string.ydiag_option_stack_trace_title), s(R.string.ydiag_option_stack_trace_desc), DiagnosticCategory.LSPOSED, Recommendation.DEEP, LoadLevel.VERY_HIGH),
            DiagnosticOption("webview", s(R.string.ydiag_option_webview_title), s(R.string.ydiag_option_webview_desc), DiagnosticCategory.WEBVIEW, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("network", s(R.string.ydiag_option_network_title), s(R.string.ydiag_option_network_desc), DiagnosticCategory.NETWORK, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
            DiagnosticOption("file_io", s(R.string.ydiag_option_file_io_title), s(R.string.ydiag_option_file_io_desc), DiagnosticCategory.FILES, Recommendation.DEEP, LoadLevel.HIGH),
            DiagnosticOption("binder", s(R.string.ydiag_option_binder_title), s(R.string.ydiag_option_binder_desc), DiagnosticCategory.SYSTEM, Recommendation.DEEP, LoadLevel.HIGH),
            DiagnosticOption("memory", s(R.string.ydiag_option_memory_title), s(R.string.ydiag_option_memory_desc), DiagnosticCategory.PERFORMANCE, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("perfetto", s(R.string.ydiag_option_perfetto_title), s(R.string.ydiag_option_perfetto_desc), DiagnosticCategory.PERFORMANCE, Recommendation.DEEP, LoadLevel.HIGH),
            DiagnosticOption("kernel", s(R.string.ydiag_option_kernel_title), s(R.string.ydiag_option_kernel_desc), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
            DiagnosticOption("selinux", s(R.string.ydiag_option_selinux_title), s(R.string.ydiag_option_selinux_desc), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.LOW),
            DiagnosticOption("root_module", s(R.string.ydiag_option_root_module_title), s(R.string.ydiag_option_root_module_desc), DiagnosticCategory.ROOT, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
            DiagnosticOption("lsposed_log", s(R.string.ydiag_option_lsposed_log_title), s(R.string.ydiag_option_lsposed_log_desc), DiagnosticCategory.LSPOSED, Recommendation.ON_DEMAND, LoadLevel.MEDIUM),
            DiagnosticOption("tombstone", s(R.string.ydiag_option_tombstone_title), s(R.string.ydiag_option_tombstone_desc), DiagnosticCategory.NATIVE, Recommendation.RECOMMENDED, LoadLevel.LOW, true),
            DiagnosticOption("native", s(R.string.ydiag_option_native_title), s(R.string.ydiag_option_native_desc), DiagnosticCategory.NATIVE, Recommendation.ON_DEMAND, LoadLevel.LOW),
        )

    private fun ids(vararg value: String) = value.toSet()

    val presets: List<DiagnosticPreset>
        get() = listOf(
            DiagnosticPreset("quick", s(R.string.ydiag_preset_quick_title), s(R.string.ydiag_preset_quick_desc),
                ids("logcat","crash","exit_info","anr","process","lsposed_status","hook_health","tombstone")),
            DiagnosticPreset("crash", s(R.string.ydiag_preset_crash_title), s(R.string.ydiag_preset_crash_desc),
                ids("logcat","crash","exit_info","anr","process","tombstone","native","lsposed_status")),
            DiagnosticPreset("hook", s(R.string.ydiag_preset_hook_title), s(R.string.ydiag_preset_hook_desc),
                ids("logcat","process","lsposed_status","hook_health","lifecycle","intent","lsposed_log")),
            DiagnosticPreset("webview", s(R.string.ydiag_preset_webview_title), s(R.string.ydiag_preset_webview_desc),
                ids("logcat","crash","exit_info","process","lsposed_status","hook_health","lifecycle","intent","webview","network")),
            DiagnosticPreset("freeze", s(R.string.ydiag_preset_freeze_title), s(R.string.ydiag_preset_freeze_desc),
                ids("logcat","anr","exit_info","process","memory","perfetto","binder")),
            DiagnosticPreset("root", s(R.string.ydiag_preset_root_title), s(R.string.ydiag_preset_root_desc),
                ids("logcat","crash","exit_info","process","kernel","selinux","root_module","lsposed_log")),
            DiagnosticPreset("complete", s(R.string.ydiag_preset_complete_title), s(R.string.ydiag_preset_complete_desc), options.map { it.id }.toSet()),
        )

    val defaultEnabled: Set<String>
        get() = options.filter { it.defaultEnabled }.mapTo(linkedSetOf()) { it.id }
}
