package com.yagay.yui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject

/** Host-wide validated appearance parameters. Existing feature preferences are untouched. */
enum class YSettingKey(val key: String, val default: String) {
    THEME("theme", "system"),
    DYNAMIC_COLOR("dynamic_color", "false"),
    DENSITY("density", "standard"),
    BUTTON_RADIUS("button_radius", "12"),
    BUTTON_PADDING("button_horizontal_padding", "16"),
    HOME_SWIPE_PIN("home_swipe_pin", "true"),
    HOME_STATUS("home_status", "true"),
    CONTROL_GAP("control_gap", "12"),
    FONT_PERCENT("font_percent", "100"),
    ACCENT("accent", "default"),
    BUTTON_HEIGHT("button_height", "48"),
    BUTTON_VERTICAL_PADDING("button_vertical_padding", "4"),
    ROW_HEIGHT("row_height", "56"),
    ROW_HORIZONTAL_PADDING("row_horizontal_padding", "16"),
    ROW_VERTICAL_PADDING("row_vertical_padding", "6"),
    CARD_RADIUS("card_radius", "16"),
    CARD_PADDING("card_padding", "16"),
    FIELD_RADIUS("field_radius", "12"),
    DIALOG_RADIUS("dialog_radius", "28"),
    SWITCH_SLOT_WIDTH("switch_slot_width", "56"),
    ICON_TOUCH_TARGET("icon_touch_target", "48"),
    NAV_RADIUS("nav_radius", "12"),
    SCREEN_PADDING("screen_padding", "16"),
    SECTION_SPACING("section_spacing", "16");

    fun validate(value: String): String {
        val valid = when (this) {
            THEME -> value in setOf("system", "light", "dark")
            DYNAMIC_COLOR, HOME_SWIPE_PIN, HOME_STATUS -> value in setOf("true", "false")
            DENSITY -> value in setOf("compact", "standard", "comfortable")
            ACCENT -> value in setOf("default", "blue", "teal", "green", "purple", "orange")
            BUTTON_RADIUS -> (value.toIntOrNull() ?: -1) in 0..32
            BUTTON_PADDING -> (value.toIntOrNull() ?: -1) in 8..32
            CONTROL_GAP -> (value.toIntOrNull() ?: -1) in 4..24
            FONT_PERCENT -> (value.toIntOrNull() ?: -1) in 85..130
            BUTTON_HEIGHT, ICON_TOUCH_TARGET -> (value.toIntOrNull() ?: -1) in 48..72
            BUTTON_VERTICAL_PADDING -> (value.toIntOrNull() ?: -1) in 0..16
            ROW_HEIGHT -> (value.toIntOrNull() ?: -1) in 48..88
            ROW_HORIZONTAL_PADDING, SCREEN_PADDING -> (value.toIntOrNull() ?: -1) in 8..32
            ROW_VERTICAL_PADDING -> (value.toIntOrNull() ?: -1) in 2..16
            CARD_RADIUS, FIELD_RADIUS, DIALOG_RADIUS, NAV_RADIUS -> (value.toIntOrNull() ?: -1) in 0..32
            CARD_PADDING -> (value.toIntOrNull() ?: -1) in 8..32
            SWITCH_SLOT_WIDTH -> (value.toIntOrNull() ?: -1) in 56..84
            SECTION_SPACING -> (value.toIntOrNull() ?: -1) in 0..32
        }
        require(valid) { "Invalid " + key + " value" }
        return value
    }
}

data class YAppearance(
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val density: String = "standard",
    val buttonRadiusDp: Int = 12,
    val buttonPaddingHorizontalDp: Int = 16,
    val homeSwipePin: Boolean = true,
    val homeStatusVisible: Boolean = true,
    val controlGapDp: Int = 12,
    val fontPercent: Int = 100,
    val accent: String = "default",
    val buttonHeightDp: Int = 48,
    val buttonVerticalPaddingDp: Int = 4,
    val rowHeightDp: Int = 56,
    val rowHorizontalPaddingDp: Int = 16,
    val rowVerticalPaddingDp: Int = 6,
    val cardRadiusDp: Int = 16,
    val cardPaddingDp: Int = 16,
    val fieldRadiusDp: Int = 12,
    val dialogRadiusDp: Int = 28,
    val switchSlotWidthDp: Int = 56,
    val iconTouchTargetDp: Int = 48,
    val navRadiusDp: Int = 12,
    val screenPaddingDp: Int = 16,
    val sectionSpacingDp: Int = 16,
) {
    val effectiveGapDp: Int get() = when (density) {
        "compact" -> (controlGapDp * 2 / 3).coerceAtLeast(4)
        "comfortable" -> (controlGapDp * 4 / 3).coerceAtMost(32)
        else -> controlGapDp
    }
}

val LocalYAppearance = staticCompositionLocalOf { YAppearance() }

class YAppearanceStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun prefix(moduleId: String?): String =
        if (moduleId == null) "global."
        else "module." + validateModule(moduleId) + "."

    private fun readValue(key: YSettingKey, moduleId: String?): String {
        val overridden = moduleId?.let { prefs.getString(prefix(it) + key.key, null) }
        val global = prefs.getString(prefix(null) + key.key, null)
        val candidate = overridden ?: global ?: key.default
        return runCatching { key.validate(candidate) }.getOrDefault(key.default)
    }

    fun value(key: YSettingKey, moduleId: String? = null): String = readValue(key, moduleId)

    fun appearance(moduleId: String? = null): YAppearance = YAppearance(
        theme = value(YSettingKey.THEME, moduleId),
        dynamicColor = value(YSettingKey.DYNAMIC_COLOR, moduleId) == "true",
        density = value(YSettingKey.DENSITY, moduleId),
        buttonRadiusDp = value(YSettingKey.BUTTON_RADIUS, moduleId).toInt(),
        buttonPaddingHorizontalDp = value(YSettingKey.BUTTON_PADDING, moduleId).toInt(),
        homeSwipePin = value(YSettingKey.HOME_SWIPE_PIN, moduleId) == "true",
        homeStatusVisible = value(YSettingKey.HOME_STATUS, moduleId) == "true",
        controlGapDp = value(YSettingKey.CONTROL_GAP, moduleId).toInt(),
        fontPercent = value(YSettingKey.FONT_PERCENT, moduleId).toInt(),
        accent = value(YSettingKey.ACCENT, moduleId),
        buttonHeightDp = value(YSettingKey.BUTTON_HEIGHT, moduleId).toInt(),
        buttonVerticalPaddingDp = value(YSettingKey.BUTTON_VERTICAL_PADDING, moduleId).toInt(),
        rowHeightDp = value(YSettingKey.ROW_HEIGHT, moduleId).toInt(),
        rowHorizontalPaddingDp = value(YSettingKey.ROW_HORIZONTAL_PADDING, moduleId).toInt(),
        rowVerticalPaddingDp = value(YSettingKey.ROW_VERTICAL_PADDING, moduleId).toInt(),
        cardRadiusDp = value(YSettingKey.CARD_RADIUS, moduleId).toInt(),
        cardPaddingDp = value(YSettingKey.CARD_PADDING, moduleId).toInt(),
        fieldRadiusDp = value(YSettingKey.FIELD_RADIUS, moduleId).toInt(),
        dialogRadiusDp = value(YSettingKey.DIALOG_RADIUS, moduleId).toInt(),
        switchSlotWidthDp = value(YSettingKey.SWITCH_SLOT_WIDTH, moduleId).toInt(),
        iconTouchTargetDp = value(YSettingKey.ICON_TOUCH_TARGET, moduleId).toInt(),
        navRadiusDp = value(YSettingKey.NAV_RADIUS, moduleId).toInt(),
        screenPaddingDp = value(YSettingKey.SCREEN_PADDING, moduleId).toInt(),
        sectionSpacingDp = value(YSettingKey.SECTION_SPACING, moduleId).toInt(),
    )

    fun set(key: YSettingKey, value: String, moduleId: String? = null) {
        prefs.edit().putString(prefix(moduleId) + key.key, key.validate(value)).apply()
    }

    fun isOverridden(key: YSettingKey, moduleId: String): Boolean =
        prefs.contains(prefix(moduleId) + key.key)

    fun inherit(key: YSettingKey, moduleId: String) {
        prefs.edit().remove(prefix(moduleId) + key.key).apply()
    }

    fun reset(moduleId: String? = null) {
        val editor = prefs.edit()
        YSettingKey.entries.forEach { editor.remove(prefix(moduleId) + it.key) }
        editor.apply()
    }

    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Export ONLY appearance values; do not copy module secrets or root/hook state. */
    fun exportJson(): String {
        val global = JSONObject()
        YSettingKey.entries.forEach { global.put(it.key, value(it)) }
        val modules = JSONObject()
        val ids = prefs.all.keys.mapNotNull {
            MODULE_KEY.matchEntire(it)?.groupValues?.get(1)
        }.distinct().sorted()
        for (id in ids) {
            val overrides = JSONObject()
            YSettingKey.entries.forEach { key ->
                val raw = prefs.getString(prefix(id) + key.key, null)
                if (raw != null) overrides.put(key.key, key.validate(raw))
            }
            if (overrides.length() > 0) modules.put(id, overrides)
        }
        return JSONObject().put("version", 1)
            .put("global", global).put("modules", modules).toString(2)
    }

    /** Validate the entire payload before atomically replacing only appearance keys. */
    fun importJson(json: String) {
        require(json.length <= 262_144) { "Settings document is too large" }
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported settings version" }
        val pending = linkedMapOf<String, String>()
        fun collect(source: JSONObject, moduleId: String?) {
            val iter = source.keys()
            while (iter.hasNext()) {
                val name = iter.next()
                val key = YSettingKey.entries.firstOrNull { it.key == name }
                    ?: error("Unknown settings key")
                pending[prefix(moduleId) + key.key] = key.validate(source.getString(name))
            }
        }
        collect(root.getJSONObject("global"), null)
        val modules = root.optJSONObject("modules") ?: JSONObject()
        val ids = modules.keys()
        while (ids.hasNext()) {
            val id = validateModule(ids.next())
            collect(modules.getJSONObject(id), id)
        }
        val editor = prefs.edit()
        prefs.all.keys.filter {
            it.startsWith("global.") || (it.startsWith("module.") && MODULE_KEY.matches(it))
        }.forEach { editor.remove(it) }
        pending.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Unable to save settings" }
    }

    companion object {
        const val MODULE_EXTRA = "com.yagay.yui.MODULE_ID"
        private const val FILE = "yui_appearance_v1"
        private val MODULE_ID = Regex("[a-z][a-z0-9_-]{0,63}")
        private val MODULE_KEY = Regex("""module\.([a-z][a-z0-9_-]{0,63})\.[a-z_]+""")

        private fun validateModule(moduleId: String): String {
            require(MODULE_ID.matches(moduleId)) { "Invalid module ID" }
            return moduleId
        }

        /** Identify existing built-in feature Activity packages without touching feature logic. */
        fun moduleIdFor(context: Context): String? {
            var current: Context? = context
            while (current is ContextWrapper) {
                if (current is Activity) {
                    val explicit = current.intent?.getStringExtra(MODULE_EXTRA)
                    if (explicit != null && MODULE_ID.matches(explicit)) return explicit
                }
                // Also resolve feature services (not only Activities), so overlays inherit their
                // module-specific appearance even when inflated with a Service context.
                val klass = current.javaClass.name.lowercase()
                val feature = listOf(
                    "yentrycleaner", "ydiag", "ynotify", "ypower", "yminiguard",
                    "ynfc", "ytaskmanager", "yparam", "yfloat", "ydownload", "yfiles",
                ).firstOrNull { klass.contains("." + it + ".") }
                if (feature != null) return feature
                current = current.baseContext
            }
            return null
        }
    }
}

@Composable
fun rememberYAppearance(moduleId: String? = null): YAppearance {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { YAppearanceStore(context) }
    var appearance by remember(store, moduleId) { mutableStateOf(store.appearance(moduleId)) }
    DisposableEffect(store, moduleId) {
        val dispose = store.listen { appearance = store.appearance(moduleId) }
        onDispose(dispose)
    }
    return appearance
}
