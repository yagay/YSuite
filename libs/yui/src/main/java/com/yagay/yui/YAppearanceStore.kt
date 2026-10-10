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
enum class YSettingKey(
    val key: String,
    val default: String,
    val minimum: Int? = null,
    val maximum: Int? = null,
) {
    THEME("theme", "system"),
    DYNAMIC_COLOR("dynamic_color", "false"),
    DENSITY("density", "standard"),
    BUTTON_RADIUS("button_radius", "12", 0, 512),
    BUTTON_PADDING("button_horizontal_padding", "16", 0, 512),
    HOME_SWIPE_PIN("home_swipe_pin", "true"),
    HOME_STATUS("home_status", "true"),
    CONTROL_GAP("control_gap", "12", 0, 512),
    FONT_PERCENT("font_percent", "100", 25, 400),
    ACCENT("accent", "default"),
    BUTTON_HEIGHT("button_height", "48", 16, 320),
    BUTTON_VERTICAL_PADDING("button_vertical_padding", "4", 0, 256),
    ROW_HEIGHT("row_height", "56", 0, 320),
    ROW_HORIZONTAL_PADDING("row_horizontal_padding", "16", 0, 512),
    ROW_VERTICAL_PADDING("row_vertical_padding", "6", 0, 256),
    CARD_RADIUS("card_radius", "16", 0, 512),
    CARD_PADDING("card_padding", "16", 0, 512),
    FIELD_RADIUS("field_radius", "12", 0, 512),
    DIALOG_RADIUS("dialog_radius", "28", 0, 512),
    SWITCH_SLOT_WIDTH("switch_slot_width", "56", 0, 320),
    ICON_TOUCH_TARGET("icon_touch_target", "48", 16, 256),
    NAV_RADIUS("nav_radius", "12", 0, 512),
    SCREEN_PADDING("screen_padding", "16", 0, 512),
    SECTION_SPACING("section_spacing", "16", 0, 512),
    PAGE_VERTICAL_PADDING("page_vertical_padding", "12", 0, 256),
    ICON_VISUAL_SIZE("icon_visual_size", "24", 8, 160),
    LIST_ICON_SIZE("list_icon_size", "48", 8, 160),
    TOOLBAR_HEIGHT("toolbar_height", "64", 24, 256),
    FLOAT_ICON_ALPHA("float_icon_alpha", "62", 1, 100),
    FLOAT_ICON_SIZE("float_icon_size", "48", 16, 192),
    FLOAT_EDGE_VISIBLE("float_edge_visible", "72", 1, 100),
    FLOAT_BORDER_WIDTH("float_border_width", "3", 0, 48),
    FLOAT_TRAIL_ALPHA("float_trail_alpha", "80", 0, 100),
    FLOAT_TRAIL_WIDTH("float_trail_width", "6", 0, 64),
    FLOAT_MENU_COUNT("float_menu_count", "6", 2, 32),
    FLOAT_BORDER_COLOR("float_border_color", "blue"),
    FLOAT_ICON_STYLE("float_icon_style", "blue"),
    FLOAT_TRAIL_STYLE("float_trail_style", "round"),
    FLOAT_TRAIL_GRADIENT("float_trail_gradient", "false"),
    FLOAT_TRAIL_COLORS("float_trail_colors", "#FFFFFF"),
    FLOAT_BORDER_VISIBLE("float_border_visible", "true"),
    FLOAT_TRAIL_VISIBLE("float_trail_visible", "false");

    fun validate(value: String): String {
        val valid = if (minimum != null && maximum != null) {
            value.toIntOrNull()?.let { it in minimum..maximum } ?: false
        } else when (this) {
            THEME -> value in setOf("system", "light", "dark")
            DYNAMIC_COLOR, HOME_SWIPE_PIN, HOME_STATUS, FLOAT_TRAIL_GRADIENT, FLOAT_BORDER_VISIBLE, FLOAT_TRAIL_VISIBLE -> value in setOf("true", "false")
            DENSITY -> value in setOf("compact", "standard", "comfortable")
            ACCENT -> value in setOf("default", "blue", "teal", "green", "purple", "orange")
            FLOAT_BORDER_COLOR -> value in setOf("blue", "green", "cyan", "purple", "orange", "red", "white")
            FLOAT_ICON_STYLE -> value in setOf("blue", "dark", "light", "custom", "slideshow")
            FLOAT_TRAIL_STYLE -> value in setOf("round", "square", "enhanced")
            FLOAT_TRAIL_COLORS -> value.length <= 256 && value.none { it == '\u0000' }
            else -> false
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
    val pageVerticalPaddingDp: Int = 12,
    val iconVisualSizeDp: Int = 24,
    val listIconSizeDp: Int = 48,
    val toolbarHeightDp: Int = 64,
    val floatIconAlpha: Int = 62,
    val floatIconSizeDp: Int = 48,
    val floatEdgeVisiblePercent: Int = 72,
    val floatBorderWidthDp: Int = 3,
    val floatTrailAlpha: Int = 80,
    val floatTrailWidthDp: Int = 6,
    val floatMenuCount: Int = 6,
    val floatBorderColor: String = "blue",
    val floatIconStyle: String = "blue",
    val floatTrailStyle: String = "round",
    val floatTrailGradient: Boolean = false,
    val floatTrailColors: String = "#FFFFFF",
    val floatBorderVisible: Boolean = true,
    val floatTrailVisible: Boolean = false,
) {
    val effectiveGapDp: Int get() = when (density) {
        "compact" -> controlGapDp * 2 / 3
        "comfortable" -> controlGapDp * 4 / 3
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
        pageVerticalPaddingDp = value(YSettingKey.PAGE_VERTICAL_PADDING, moduleId).toInt(),
        iconVisualSizeDp = value(YSettingKey.ICON_VISUAL_SIZE, moduleId).toInt(),
        listIconSizeDp = value(YSettingKey.LIST_ICON_SIZE, moduleId).toInt(),
        toolbarHeightDp = value(YSettingKey.TOOLBAR_HEIGHT, moduleId).toInt(),
        floatIconAlpha = value(YSettingKey.FLOAT_ICON_ALPHA, moduleId).toInt(),
        floatIconSizeDp = value(YSettingKey.FLOAT_ICON_SIZE, moduleId).toInt(),
        floatEdgeVisiblePercent = value(YSettingKey.FLOAT_EDGE_VISIBLE, moduleId).toInt(),
        floatBorderWidthDp = value(YSettingKey.FLOAT_BORDER_WIDTH, moduleId).toInt(),
        floatTrailAlpha = value(YSettingKey.FLOAT_TRAIL_ALPHA, moduleId).toInt(),
        floatTrailWidthDp = value(YSettingKey.FLOAT_TRAIL_WIDTH, moduleId).toInt(),
        floatMenuCount = value(YSettingKey.FLOAT_MENU_COUNT, moduleId).toInt(),
        floatBorderColor = value(YSettingKey.FLOAT_BORDER_COLOR, moduleId),
        floatIconStyle = value(YSettingKey.FLOAT_ICON_STYLE, moduleId),
        floatTrailStyle = value(YSettingKey.FLOAT_TRAIL_STYLE, moduleId),
        floatTrailGradient = value(YSettingKey.FLOAT_TRAIL_GRADIENT, moduleId) == "true",
        floatTrailColors = value(YSettingKey.FLOAT_TRAIL_COLORS, moduleId),
        floatBorderVisible = value(YSettingKey.FLOAT_BORDER_VISIBLE, moduleId) == "true",
        floatTrailVisible = value(YSettingKey.FLOAT_TRAIL_VISIBLE, moduleId) == "true",
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

    /** Java/Service bridge for notifications from the same centralized appearance store. */
    fun registerPreferenceListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
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
