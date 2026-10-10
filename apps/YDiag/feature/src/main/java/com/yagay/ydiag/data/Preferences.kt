package com.yagay.ydiag.data

import android.content.Context
import com.yagay.suite.api.FeatureSettings
import com.yagay.ydiag.model.DiagnosticCatalog

class Preferences(context: Context) {
    private val prefs = FeatureSettings.named(context, "ydiag")

    var selectedPackages: Set<String>
        get() = prefs.stringSet("selected_packages", emptySet())
        set(value) { prefs.putStringSet("selected_packages", value) }

    var enabledOptions: Set<String>
        get() = prefs.stringSet("enabled_options", DiagnosticCatalog.defaultEnabled)
        set(value) { prefs.putStringSet("enabled_options", value) }

    var presetId: String
        get() = prefs.string("preset_id", "quick") ?: "quick"
        set(value) { prefs.putString("preset_id", value) }

    var customExportTree: String?
        get() = prefs.string("custom_export_tree", null)
        set(value) { prefs.putString("custom_export_tree", value) }

    var exportMode: String
        get() = prefs.string("export_mode", "download") ?: "download"
        set(value) { prefs.putString("export_mode", value) }

    var maxSessionMb: Int
        get() = prefs.int("max_session_mb", 128)
        set(value) { prefs.putInt("max_session_mb", value.coerceIn(32, 1024)) }

    var deepActivationMode: String
        get() = prefs.string("deep_activation_mode", ACTIVATION_AUTO)
            ?.takeIf { it in ACTIVATION_MODES } ?: ACTIVATION_AUTO
        set(value) {
            prefs.putString(
                "deep_activation_mode",
                value.takeIf { it in ACTIVATION_MODES } ?: ACTIVATION_AUTO,
            )
        }

    fun optionsFor(packageName: String): Set<String> =
        if (prefs.contains("options:$packageName")) prefs.stringSet("options:$packageName") else enabledOptions

    fun setOptionsFor(packageName: String, options: Set<String>) {
        prefs.putStringSet("options:$packageName", options)
    }

    companion object {
        const val ACTIVATION_AUTO = "auto"
        const val ACTIVATION_MANUAL = "manual"
        const val ACTIVATION_ROOT_ONLY = "root_only"
        val ACTIVATION_MODES = setOf(ACTIVATION_AUTO, ACTIVATION_MANUAL, ACTIVATION_ROOT_ONLY)
    }
}
