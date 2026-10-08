package com.yagay.ysuite.feature.yentrycleaner

import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.feature.yentrycleaner.runtime.YEntryRuntimeBridge
import org.json.JSONArray
import org.json.JSONObject

/**
 * Validates and stages import values before mutating SharedPreferences.
 *
 * Old YEntryCleaner v1-v11 backups are accepted when their configuration can be
 * represented without loss. Unsupported per-open-type or visibility rules are
 * rejected instead of silently claiming a successful restore.
 */
internal object YEntryBackupParser {
    fun parse(text: String): Map<String, Any>? {
        val source = runCatching { JSONObject(text) }.getOrNull() ?: return null
        return if (source.optString("format") == "YSuite.YEntryCleaner") {
            parseSuite(source)
        } else {
            parseLegacy(source)
        }
    }

    private fun parseSuite(source: JSONObject): Map<String, Any>? {
        if (source.optInt("version", -1) != 1) return null
        val values = source.optJSONObject("values") ?: return null
        val result = linkedMapOf<String, Any>()
        for (key in values.keys()) {
            val value = values.opt(key)
            when {
                key == "display_mode" -> {
                    val mode = value as? String ?: return null
                    if (mode !in setOf("HIDE_SELECTED", "SHOW_SELECTED", "SHOW_ALL")) return null
                    result[key] = mode
                }
                key == "diagnostic" -> result[key] = value as? Boolean ?: return null
                key == "component_titles" -> {
                    val raw = value as? String ?: return null
                    if (raw.length > 250_000) return null
                    val titles = runCatching { JSONObject(raw) }.getOrNull() ?: return null
                    if (titles.length() > 2_000) return null
                    for (id in titles.keys()) {
                        val name = titles.opt(id) as? String ?: return null
                        if (id.length > 1024 || id.count { it == '|' } != 3 ||
                            name.isBlank() || name.length > 64 ||
                            name.any { it.isISOControl() }
                        ) return null
                    }
                    result[key] = raw
                }
                key == "browser_host" || key == "open_mime" -> {
                    val string = value as? String ?: return null
                    if (string.length > 255 || string.contains('\n')) return null
                    result[key] = string
                }
                key in setOf("hidden_rules", "locked_rules", "seen_candidates", "browser_hosts") -> {
                    result[key] = readStrings(value as? JSONArray ?: return null, 10_000) ?: return null
                }
                key == "priority_qualifiers" -> {
                    val qualifiers = readStrings(value as? JSONArray ?: return null, 1_000)
                        ?: return null
                    if (!qualifiers.all { key ->
                        val parts = key.split('|', limit = 2)
                        parts.size == 2 &&
                            YEntrySurface.entries.any { it.name == parts[0] } &&
                            parts[1].isNotBlank() && parts[1].length <= 255 &&
                            parts[1].none { it.isWhitespace() || it.isISOControl() }
                    }) return null
                    result[key] = qualifiers
                }
                key.startsWith("priority_") && key.length < 90 -> {
                    val order = value as? String ?: return null
                    if (order.length > 50_000) return null
                    result[key] = order
                }
                // Physical component disablement is never restored from a rules backup.
                // Keep the installed device's actual Root component state unchanged.
                key == "disabled_components" -> Unit
                // Refuse unknown future configuration rather than silently
                // reporting a lossy successful restore.
                else -> return null
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun parseLegacy(source: JSONObject): Map<String, Any>? {
        val version = source.optInt("version", -1)
        if (version !in 1..11) return null
        val rawRules = source.optJSONArray("rules") ?: return null
        if (rawRules.length() > 10_000) return null

        // These older capabilities have no equivalent in the rebuilt runtime yet.
        // A silent partial restore would discard the user's protections/preferences.
        if (nonEmpty(source.optJSONArray("hiddenFromApps"))) return null
        if (nonEmpty(source.optJSONArray("visibilityScopes"))) return null
        val openTypes = source.optJSONObject("openTypes")
        if (openTypes != null &&
            listOf("rules", "priorities", "customDefinitions")
                .any { (openTypes.optJSONObject(it)?.length() ?: 0) > 0 }
        ) return null

        val mode = if (version >= 3) {
            source.optString("displayMode", "")
        } else if (source.optBoolean("blacklist", true)) {
            "HIDE_SELECTED"
        } else {
            "SHOW_SELECTED"
        }
        if (mode !in setOf("HIDE_SELECTED", "SHOW_SELECTED", "SHOW_ALL")) return null
        val rules = linkedSetOf<String>()
        for (i in 0 until rawRules.length()) {
            val rule = rawRules.optJSONObject(i) ?: return null
            val oldKind = rule.optString("kind")
            val pkg = rule.optString("packageName")
            val className = canonicalClass(pkg, rule.optString("className")) ?: return null
            val surfaces = surfaces(oldKind) ?: return null
            for (surface in surfaces) {
                rules += YEntryRuntimeBridge.ruleKey(surface.name, "*", pkg, className)
            }
        }

        val result = linkedMapOf<String, Any>(
            "hidden_rules" to rules,
            "display_mode" to mode,
        )
        val oldPriorityConfig = source.optJSONObject("priorities")
        val indexedQualifiers = linkedSetOf<String>()
        val titles = oldPriorityConfig?.optJSONObject("titles")
        if (titles != null) {
            if (titles.length() > 2_000) return null
            val converted = linkedMapOf<String, String>()
            for (oldId in titles.keys()) {
                val parts = oldId.split('|', limit = 3)
                if (parts.size != 3) return null
                val targets = surfaces(parts[0]) ?: return null
                val className = canonicalClass(parts[1], parts[2]) ?: return null
                val title = titles.opt(oldId) as? String ?: return null
                if (title.isBlank() || title.length > 64 ||
                    title.any { it.isISOControl() }
                ) return null
                for (surface in targets) {
                    val key = YEntryRuntimeBridge.ruleKey(
                        surface.name, "*", parts[1], className,
                    )
                    if (converted.containsKey(key) && converted[key] != title) return null
                    converted[key] = title
                }
            }
            result["component_titles"] = JSONObject(converted).toString()
        }
        val oldPriorities = oldPriorityConfig?.optJSONObject("apps")
        if (oldPriorities != null) {
            for (kind in oldPriorities.keys()) {
                val mapped = surfaces(kind) ?: return null
                val values = readStrings(oldPriorities.optJSONArray(kind) ?: return null, 200)
                    ?: return null
                if (!values.all(::validPackage)) return null
                for (surface in mapped) {
                    val storageKey = priorityKey(surface, "*")
                    val encoded = values.joinToString(">")
                    if (result.containsKey(storageKey) &&
                        result[storageKey] != encoded
                    ) return null
                    result[storageKey] = encoded
                    indexedQualifiers += surface.name + "|*"
                }
            }
        }

        val browser = source.optJSONObject("browserLinks")
        if (browser != null) {
            val hosts = readStrings(browser.optJSONArray("hosts") ?: JSONArray(), 64)
                ?: return null
            if (!hosts.all(::validHost)) return null
            result["browser_hosts"] = hosts
            hosts.firstOrNull()?.let { result["browser_host"] = it }
            val domainRules = browser.optJSONObject("rules")
            if (domainRules != null) {
                for (host in domainRules.keys()) {
                    if (!validHost(host) || host !in hosts) return null
                    val ids = readStrings(domainRules.optJSONArray(host) ?: return null, 2000)
                        ?: return null
                    for (id in ids) {
                        val parsed = id.split('|', limit = 3)
                        if (parsed.size != 3 || parsed[0] !in setOf("BROWSER", "DEEP_LINK")) return null
                        val className = canonicalClass(parsed[1], parsed[2]) ?: return null
                        rules += YEntryRuntimeBridge.ruleKey(
                            YEntrySurface.Browser.name, host, parsed[1], className,
                        )
                    }
                }
            }
            val priorities = browser.optJSONObject("priorities")
            if (priorities != null) {
                for (host in priorities.keys()) {
                    if (!validHost(host) || host !in hosts) return null
                    val apps = readStrings(priorities.optJSONArray(host) ?: return null, 200)
                        ?: return null
                    if (!apps.all(::validPackage)) return null
                    result[priorityKey(YEntrySurface.Browser, host)] = apps.joinToString(">")
                    indexedQualifiers += YEntrySurface.Browser.name + "|" + host
                }
            }
        }
        result["priority_qualifiers"] = indexedQualifiers
        return result
    }

    private fun surfaces(oldKind: String): List<YEntrySurface>? = when (oldKind) {
        "SHARE" -> listOf(YEntrySurface.ShareText, YEntrySurface.ShareImage)
        "SHARE_MULTIPLE" -> listOf(YEntrySurface.ShareMultiple)
        "PROCESS_TEXT" -> listOf(YEntrySurface.ProcessText)
        "OPEN" -> listOf(YEntrySurface.Open)
        "BROWSER", "DEEP_LINK" -> listOf(YEntrySurface.Browser)
        else -> null
    }

    private fun readStrings(array: JSONArray, limit: Int): Set<String>? {
        if (array.length() > limit) return null
        val values = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val value = array.opt(i) as? String ?: return null
            if (value.length > 1024 || value.contains('\n')) return null
            values += value
        }
        return values
    }

    private fun nonEmpty(array: JSONArray?): Boolean = array != null && array.length() > 0

    private fun validPackage(pkg: String): Boolean =
        pkg.isNotBlank() && pkg.length <= 255 &&
            !pkg.contains('|') && pkg.none { it.isWhitespace() || it.isISOControl() }

    private fun canonicalClass(pkg: String, raw: String): String? {
        if (!validPackage(pkg) || raw.isBlank() || raw.length > 512 ||
            raw.contains('|') || raw.any { it.isWhitespace() || it.isISOControl() }
        ) return null
        return if (raw.startsWith('.')) pkg + raw else raw
    }

    private fun validHost(host: String): Boolean =
        host.isNotBlank() && host.length <= 253 && host == host.lowercase() &&
            host.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    !label.startsWith('-') && !label.endsWith('-') &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }

    private fun priorityKey(surface: YEntrySurface, qualifier: String): String =
        "priority_" + surface.name + "_" + qualifier.hashCode()
}
