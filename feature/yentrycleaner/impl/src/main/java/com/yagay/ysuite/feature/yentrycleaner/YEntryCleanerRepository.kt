package com.yagay.ysuite.feature.yentrycleaner

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidateState
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryOpenQualifiers
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryRuleSelection
import com.yagay.ysuite.feature.yentrycleaner.runtime.YEntryRuntimeBridge
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

internal enum class YEntryImportResult {
    Invalid,
    SavedLocally,
    Synced,
}

internal enum class YEntryManagedKind { Activity, Service, Receiver, Provider }

internal data class YEntryManagedComponent(
    val id: String,
    val kind: YEntryManagedKind,
    val packageName: String,
    val className: String,
    val appLabel: String,
    val label: String,
    val system: Boolean,
    val enabled: Boolean,
    val locked: Boolean,
    val blocked: Boolean,
)

internal data class YEntryCustomDraft(
    val title: String = "",
    val mimeTypes: String = "",
    val extensions: String = "",
)

internal class YEntryCleanerRepository(
    private val context: Context,
    private val root: RootGateway,
    private val hooks: HookGateway,
) {
    private val prefs =
        context.getSharedPreferences(
            "ysuite_yentrycleaner",
            Context.MODE_PRIVATE,
        )
    private val pm = context.packageManager
    private val user = Process.myUid() / 100_000
    private val syncMutex = Mutex()

    fun hidden(): Set<String> =
        prefs.getStringSet("hidden_rules", emptySet())
            ?.toSet().orEmpty()

    fun locked(): Set<String> =
        prefs.getStringSet("locked_rules", emptySet())
            ?.toSet().orEmpty()

    private fun shown(): Set<String> =
        prefs.getStringSet("shown_rules", emptySet())?.toSet().orEmpty()

    private fun unlocked(): Set<String> =
        prefs.getStringSet("unlocked_rules", emptySet())?.toSet().orEmpty()

    private fun componentTitles(): Map<String, String> =
        runCatching {
            val json = JSONObject(prefs.getString("component_titles", "{}") ?: "{}")
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())

    fun setComponentTitle(id: String, title: String): Boolean {
        val normalized = title.trim()
        if (normalized.length > 64 || normalized.any { it.isISOControl() }) {
            return false
        }
        val titles = componentTitles().toMutableMap()
        if (normalized.isBlank()) titles.remove(id) else titles[id] = normalized
        return prefs.edit()
            .putString("component_titles", JSONObject(titles).toString())
            .commit()
    }

    fun disabledComponents(): Set<String> =
        prefs.getStringSet(
            "disabled_components",
            emptySet(),
        )?.toSet().orEmpty()

    fun displayMode(): String =
        prefs.getString(
            "display_mode",
            "HIDE_SELECTED",
        ) ?: "HIDE_SELECTED"

    fun diagnostic(): Boolean =
        prefs.getBoolean("diagnostic", false)

    fun browserHost(): String =
        prefs.getString(
            "browser_host",
            "example.com",
        ).orEmpty()
            .ifBlank { "example.com" }

    fun openMime(): String =
        prefs.getString(
            "open_mime",
            "application/pdf",
        ).orEmpty()
            .ifBlank {
                "application/pdf"
            }

    fun setOpenMime(value: String) {
        val input = value.trim()
        val normalized =
            if (input.startsWith("preset:", ignoreCase = true)) {
                "preset:" + input.substringAfter(':').uppercase()
            } else input.lowercase().ifBlank { "application/pdf" }
        prefs.edit()
            .putString(
                "open_mime",
                normalized,
            )
            .apply()
    }


    /** Enumerates actual package components, not just resolver/tile candidates. */
    @Suppress("DEPRECATION")
    fun managedComponents(): List<YEntryManagedComponent> {
        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val disabled = disabledComponents()
        val locks = locked()
        val result = linkedMapOf<String, YEntryManagedComponent>()
        pm.getInstalledPackages(flags).forEach { pkg ->
            val application = pkg.applicationInfo ?: return@forEach
            val appLabel = runCatching {
                pm.getApplicationLabel(application).toString()
            }.getOrDefault(pkg.packageName)
            fun record(kind: YEntryManagedKind, info: ComponentInfo) {
                val component = ComponentName(info.packageName, info.name)
                val id = YEntryRuntimeBridge.componentKey(
                    user, info.packageName, info.name,
                )
                val setting = runCatching {
                    pm.getComponentEnabledSetting(component)
                }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                val enabled = when (setting) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                    else -> info.enabled && application.enabled
                }
                val blocked = info.packageName == context.packageName ||
                    info.packageName == "android" ||
                    info.packageName == "com.android.systemui" ||
                    application.uid % 100_000 < Process.FIRST_APPLICATION_UID
                result[kind.name + ":" + id] = YEntryManagedComponent(
                    id = id,
                    kind = kind,
                    packageName = info.packageName,
                    className = info.name,
                    appLabel = appLabel,
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.name),
                    system = application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    enabled = enabled,
                    locked = id in locks,
                    blocked = blocked,
                )
            }
            pkg.activities?.forEach { record(YEntryManagedKind.Activity, it) }
            pkg.services?.forEach { record(YEntryManagedKind.Service, it) }
            pkg.receivers?.forEach { record(YEntryManagedKind.Receiver, it) }
            pkg.providers?.forEach { record(YEntryManagedKind.Provider, it) }
        }
        return result.values.sortedWith(
            compareBy<YEntryManagedComponent> { it.enabled }
                .thenBy { it.appLabel.lowercase() }
                .thenBy { it.label.lowercase() },
        )
    }

    suspend fun changeManagedComponent(
        component: YEntryManagedComponent,
        enable: Boolean,
    ): Boolean {
        if (component.blocked || component.enabled == enable) return false
        return changeComponent(
            YEntryCandidate(
                id = component.id,
                surface = YEntrySurface.Tile,
                qualifier = "*",
                packageName = component.packageName,
                className = component.className,
                label = component.label,
                system = component.system,
                hidden = !component.enabled,
                locked = component.locked,
                priority = null,
                rootEnabled = component.enabled,
                rootBlocked = component.blocked,
            ),
            enable,
        )
    }

    suspend fun invertManagedComponents(
        components: List<YEntryManagedComponent>,
    ): Pair<Int, Int> {
        var changed = 0
        var failed = 0
        components.filter { !it.locked && !it.blocked }
            .forEach {
                if (changeManagedComponent(it, !it.enabled)) changed++ else failed++
            }
        return changed to failed
    }

    suspend fun changeManagedComponents(
        components: List<YEntryManagedComponent>,
        enable: Boolean,
    ): Pair<Int, Int> {
        var changed = 0
        var failed = 0
        components.filter { !it.locked && !it.blocked && it.enabled != enable }
            .forEach {
                if (changeManagedComponent(it, enable)) changed++ else failed++
            }
        return changed to failed
    }

    suspend fun rootStatus():
        CapabilityStatus =
        root.status()

    suspend fun hookStatus():
        CapabilityStatus =
        hooks.status()

    fun setBrowserHost(value: String) {
        prefs.edit()
            .putString(
                "browser_host",
                value.trim()
                    .lowercase()
                    .removePrefix("www."),
            )
            .apply()
    }

    fun setDisplayMode(value: String) {
        prefs.edit()
            .putString("display_mode", value)
            .apply()
    }

    fun setDiagnostic(value: Boolean) {
        prefs.edit()
            .putBoolean("diagnostic", value)
            .apply()
    }

    fun customDraft(slot: String): YEntryCustomDraft =
        runCatching {
            val raw = prefs.getString("open_custom_definitions", "{}") ?: "{}"
            val definition = JSONObject(raw).optJSONObject(slot)
                ?: return@runCatching YEntryCustomDraft()
            fun values(key: String): String {
                val array = definition.optJSONArray(key) ?: JSONArray()
                return (0 until array.length()).joinToString(", ") { array.getString(it) }
            }
            YEntryCustomDraft(
                title = definition.optString("title"),
                mimeTypes = values("mimeTypes"),
                extensions = values("extensions"),
            )
        }.getOrDefault(YEntryCustomDraft())

    fun saveCustomDefinition(slot: String, draft: YEntryCustomDraft): Boolean {
        if (slot !in (1..8).map { "CUSTOM_" + it }) return false
        val source = runCatching {
            JSONObject(prefs.getString("open_custom_definitions", "{}") ?: "{}")
        }.getOrNull() ?: return false
        fun items(text: String): List<String> =
            text.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)
                .map(String::lowercase).distinct()
        val mimeTypes = items(draft.mimeTypes)
        val extensions = items(draft.extensions).map { it.removePrefix(".") }
        if (draft.title.isBlank() && mimeTypes.isEmpty() && extensions.isEmpty()) {
            source.remove(slot)
        } else {
            source.put(slot, JSONObject().apply {
                put("title", draft.title.trim())
                put("mimeTypes", JSONArray(mimeTypes))
                put("extensions", JSONArray(extensions))
            })
        }
        val cleaned = YEntryBackupParser.validateDefinitions(source.toString())
            ?: return false
        return prefs.edit()
            .putString("open_custom_definitions", cleaned.toString())
            .commit()
    }

    private fun openCustomDefinitions(): Map<String, YEntryOpenQualifiers.CustomDefinition> =
        runCatching {
            val raw = prefs.getString("open_custom_definitions", "{}") ?: "{}"
            val source = JSONObject(raw)
            source.keys().asSequence().associateWith { name ->
                val definition = source.getJSONObject(name)
                fun strings(key: String): Set<String> {
                    val array = definition.optJSONArray(key) ?: JSONArray()
                    return (0 until array.length())
                        .map { array.getString(it).lowercase() }.toSet()
                }
                YEntryOpenQualifiers.CustomDefinition(
                    strings("mimeTypes"), strings("extensions"),
                )
            }
        }.getOrDefault(emptyMap())

    private fun qualifierCandidates(
        surface: YEntrySurface, qualifier: String,
        scheme: String? = null, path: String? = null,
    ): List<String> = when (surface) {
        YEntrySurface.Open -> {
            if (qualifier.startsWith("preset:")) {
                listOf(qualifier, "*")
            } else {
                val protocol = scheme ?: qualifier.takeIf { it.startsWith("scheme:") }
                    ?.removePrefix("scheme:")
                YEntryOpenQualifiers.qualifiers(
                    qualifier.takeUnless { it.startsWith("scheme:") }, protocol,
                    path, openCustomDefinitions(),
                )
            }
        }
        YEntrySurface.ShareText, YEntrySurface.ShareImage, YEntrySurface.ProcessText ->
            if ('/' in qualifier) listOf(qualifier, qualifier.substringBefore('/') + "/*", "*").distinct()
            else listOf(qualifier, "*").distinct()
        else -> listOf(qualifier, "*").distinct()
    }

    private fun hasInheritedRule(id: String, rules: Set<String>): Boolean {
        val parts = id.split('|', limit = 4)
        if (parts.size != 4) return false
        val surface = YEntrySurface.entries.firstOrNull { it.name == parts[0] }
            ?: return false
        return qualifierCandidates(surface, parts[1]).any { qualifier ->
            qualifier != parts[1] && YEntryRuntimeBridge.ruleKey(
                surface.name, qualifier, parts[2], parts[3],
            ) in rules
        }
    }

    private fun priorityKey(
        surface: YEntrySurface,
        qualifier: String,
    ) = "priority_" +
        surface.name +
        "_" +
        qualifier.hashCode()

    fun priority(
        surface: YEntrySurface,
        qualifier: String,
    ): List<String> =
        prefs.getString(
            priorityKey(surface, qualifier),
            "",
        ).orEmpty()
            .split('>')
            .map(String::trim)
            .filter(String::isNotEmpty)

    fun setPriority(
        surface: YEntrySurface,
        qualifier: String,
        ids: List<String>,
    ) {
        val indexed = prefs.getStringSet("priority_qualifiers", emptySet())
            .orEmpty().toMutableSet()
        indexed += surface.name + "|" + qualifier
        prefs.edit()
            .putString(
                priorityKey(surface, qualifier),
                ids.distinct().joinToString(">"),
            )
            .putStringSet("priority_qualifiers", indexed)
            .apply()
    }

    private fun inheritedRuleKey(id: String): String? {
        val parts = id.split('|', limit = 4)
        if (parts.size != 4 || parts[1] == "*") return null
        if (YEntrySurface.entries.none { it.name == parts[0] }) return null
        return YEntryRuntimeBridge.ruleKey(
            parts[0], "*", parts[2], parts[3],
        )
    }

    fun setHidden(id: String, value: Boolean) {
        val next = hidden().toMutableSet()
        val exceptions = shown().toMutableSet()
        if (value) {
            next += id
            exceptions -= id
        } else {
            next -= id
            if (hasInheritedRule(id, next)) exceptions += id
            else exceptions -= id
        }
        prefs.edit()
            .putStringSet("hidden_rules", next)
            .putStringSet("shown_rules", exceptions)
            .apply()
    }

    fun setLocked(id: String, value: Boolean) {
        setLocked(setOf(id), value)
    }

    fun setLocked(ids: Set<String>, value: Boolean) {
        val next = locked().toMutableSet()
        val exceptions = unlocked().toMutableSet()
        if (value) {
            next.addAll(ids)
            exceptions.removeAll(ids)
        } else {
            next.removeAll(ids)
            for (id in ids) {
                if (hasInheritedRule(id, next)) exceptions += id
                else exceptions -= id
            }
        }
        prefs.edit()
            .putStringSet("locked_rules", next)
            .putStringSet("unlocked_rules", exceptions)
            .apply()
    }

    suspend fun candidates(
        surface: YEntrySurface,
    ): List<YEntryCandidate> {
        val current =
            when (surface) {
                YEntrySurface.ShareText ->
                    queryIntent(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain"),
                        surface,
                        "text/plain",
                    )
                YEntrySurface.ShareImage ->
                    queryIntent(
                        Intent(Intent.ACTION_SEND)
                            .setType("image/*"),
                        surface,
                        "image/*",
                    )
                YEntrySurface.ShareMultiple ->
                    queryIntent(
                        Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType("*/*"),
                        surface,
                        "*",
                    )
                YEntrySurface.ProcessText ->
                    queryIntent(
                        Intent(Intent.ACTION_PROCESS_TEXT)
                            .setType("text/plain"),
                        surface,
                        "text/plain",
                    )
                YEntrySurface.Open -> {
                    val qualifier = openMime()
                    val targetMime = if (qualifier.startsWith("preset:")) {
                        val slot = qualifier.removePrefix("preset:")
                        if (slot.startsWith("CUSTOM_")) {
                            customDraft(slot).mimeTypes.substringBefore(',').trim()
                                .ifBlank { "application/octet-stream" }
                        } else {
                            YEntryOpenQualifiers.sampleMime(slot)
                        }
                    } else qualifier
                    val intent = if (targetMime.startsWith("scheme:")) {
                        Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse(targetMime.removePrefix("scheme:") + ":example"))
                    } else Intent(Intent.ACTION_VIEW).setType(targetMime)
                    queryIntent(intent, YEntrySurface.Open, qualifier)
                }
                YEntrySurface.Browser ->
                    queryIntent(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse(
                                "https://" +
                                    browserHost() +
                                    "/",
                            ),
                        ),
                        surface,
                        browserHost(),
                    )
                YEntrySurface.Tile ->
                    queryComponents(
                        Intent(
                            "android.service.quicksettings.action.QS_TILE",
                        ),
                        surface,
                    )
                YEntrySurface.Shortcut ->
                    queryComponents(
                        Intent(
                            "android.intent.action.CREATE_SHORTCUT",
                        ),
                        surface,
                    )
                YEntrySurface.Widget ->
                    queryComponents(
                        Intent(
                            "android.appwidget.action.APPWIDGET_UPDATE",
                        ),
                        surface,
                    )
                YEntrySurface.Historical ->
                    emptyList()
            }
        remember(current)
        return if (surface == YEntrySurface.Historical) {
            historical()
        } else {
            current
        }
    }

    @Suppress("DEPRECATION")
    private fun queryIntent(
        intent: Intent,
        surface: YEntrySurface,
        qualifier: String,
    ): List<YEntryCandidate> {
        val hidden = hidden()
        val locked = locked()
        val shown = shown()
        val unlocked = unlocked()
        val componentTitles = componentTitles()
        val qualifiers = qualifierCandidates(
            surface, qualifier, intent.data?.scheme, intent.data?.toString(),
        )
        val configuredOrder = qualifiers.firstNotNullOfOrNull { key ->
            if (prefs.contains(priorityKey(surface, key))) priority(surface, key)
            else null
        } ?: emptyList()
        val rank =
            configuredOrder
                .withIndex()
                .associate {
                    it.value to it.index
                }
        return pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL or
                PackageManager.MATCH_DISABLED_COMPONENTS,
        ).mapNotNull { ri ->
            val ai = ri.activityInfo
                ?: return@mapNotNull null
            val id =
                YEntryRuntimeBridge.ruleKey(
                    surface.name,
                    qualifier,
                    ai.packageName,
                    ai.name,
                )
            val wildcardId =
                YEntryRuntimeBridge.ruleKey(
                    surface.name,
                    "*",
                    ai.packageName,
                    ai.name,
                )
            YEntryCandidate(
                id = id,
                surface = surface,
                qualifier = qualifier,
                packageName = ai.packageName,
                className = ai.name,
                label =
                    componentTitles[id] ?: componentTitles[wildcardId]
                    ?: runCatching {
                        ri.loadLabel(pm).toString()
                    }.getOrDefault(ai.packageName),
                system =
                    ai.applicationInfo.flags and
                        ApplicationInfo.FLAG_SYSTEM != 0,
                hidden = YEntryRuleSelection.isSelected(
                    id, qualifiers.map { key ->
                        YEntryRuntimeBridge.ruleKey(surface.name, key, ai.packageName, ai.name)
                    }, hidden, shown,
                ),
                locked = YEntryRuleSelection.isSelected(
                    id, qualifiers.map { key ->
                        YEntryRuntimeBridge.ruleKey(surface.name, key, ai.packageName, ai.name)
                    }, locked, unlocked,
                ),
                priority = rank[id] ?: rank[wildcardId] ?: rank[ai.packageName],
            )
        }.sortedWith(
            compareBy<YEntryCandidate> {
                it.priority ?: Int.MAX_VALUE
            }.thenBy { it.label.lowercase() },
        )
    }

    @Suppress("DEPRECATION")
    private fun queryComponents(
        intent: Intent,
        surface: YEntrySurface,
    ): List<YEntryCandidate> {
        val flags =
            PackageManager.MATCH_ALL or
                PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
                PackageManager.GET_META_DATA

        fun merge(
            vararg groups: List<ComponentInfo>,
        ): List<ComponentInfo> {
            val result =
                linkedMapOf<ComponentName, ComponentInfo>()
            groups.asSequence()
                .flatten()
                .forEach { info ->
                    result[
                        ComponentName(
                            info.packageName,
                            info.name,
                        )
                    ] = info
                }
            return result.values.toList()
        }

        val infos: List<ComponentInfo> =
            when (surface) {
                YEntrySurface.Tile ->
                    pm.queryIntentServices(
                        intent,
                        flags,
                    ).mapNotNull {
                        it.serviceInfo
                    }.filter {
                        it.exported &&
                            it.permission ==
                            "android.permission.BIND_QUICK_SETTINGS_TILE"
                    }
                YEntrySurface.Shortcut -> {
                    val legacy =
                        pm.queryIntentActivities(
                            intent,
                            flags,
                        ).mapNotNull {
                            it.activityInfo
                        }.filter {
                            it.exported
                        }
                    val registered =
                        runCatching {
                            context.getSystemService(
                                LauncherApps::class.java,
                            )?.getShortcutConfigActivityList(
                                null,
                                Process.myUserHandle(),
                            ).orEmpty()
                                .mapNotNull {
                                    launcher ->
                                    runCatching {
                                        pm.getActivityInfo(
                                            launcher.componentName,
                                            flags,
                                        )
                                    }.getOrNull()
                                }.filter {
                                    it.exported
                                }
                        }.getOrDefault(
                            emptyList(),
                        )
                    merge(
                        registered,
                        legacy,
                    )
                }
                YEntrySurface.Widget -> {
                    val manifest =
                        pm.queryBroadcastReceivers(
                            intent,
                            flags,
                        ).mapNotNull {
                            it.activityInfo
                        }.filter {
                            it.metaData?.getInt(
                                "android.appwidget.provider",
                                0,
                            ) != 0
                        }
                    val registered =
                        if (
                            YEntryRuntimeBridge
                                .COMPONENT_DISCOVERY_PROTOCOL >=
                            2
                        ) {
                            runCatching {
                                AppWidgetManager
                                    .getInstance(context)
                                    .getInstalledProvidersForProfile(
                                        Process.myUserHandle(),
                                    ).mapNotNull {
                                        provider ->
                                        val component =
                                            provider.provider
                                        runCatching {
                                            pm.getReceiverInfo(
                                                component,
                                                flags,
                                            )
                                        }.getOrNull()
                                    }
                            }.getOrDefault(
                                emptyList(),
                            )
                        } else {
                            emptyList()
                        }
                    merge(
                        registered,
                        manifest,
                    )
                }
                else -> emptyList()
            }

        val disabled = disabledComponents()
        val locks = locked()
        return infos.map { info ->
            val component =
                ComponentName(
                    info.packageName,
                    info.name,
                )
            val key =
                YEntryRuntimeBridge.componentKey(
                    user,
                    info.packageName,
                    info.name,
                )
            val setting =
                runCatching {
                    pm.getComponentEnabledSetting(
                        component,
                    )
                }.getOrDefault(
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DEFAULT,
                )
            val enabled =
                when (setting) {
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager
                        .COMPONENT_ENABLED_STATE_DISABLED_USER ->
                        false
                    PackageManager
                        .COMPONENT_ENABLED_STATE_ENABLED ->
                        true
                    else -> info.enabled
                }
            val appBlocked =
                info.packageName ==
                    context.packageName ||
                    info.packageName ==
                    "android" ||
                    info.packageName ==
                    "com.android.systemui" ||
                    info.applicationInfo.uid %
                    100_000 <
                    Process.FIRST_APPLICATION_UID
            YEntryCandidate(
                id = key,
                surface = surface,
                qualifier = "*",
                packageName = info.packageName,
                className = info.name,
                label =
                    runCatching {
                        info.loadLabel(pm)
                            .toString()
                    }.getOrDefault(
                        info.packageName,
                    ),
                system =
                    info.applicationInfo.flags and
                        ApplicationInfo.FLAG_SYSTEM != 0,
                hidden = key in disabled,
                locked = key in locks,
                priority = null,
                rootEnabled = enabled,
                rootBlocked = appBlocked,
            )
        }.sortedBy {
            it.label.lowercase()
        }
    }

    private fun remember(
        candidates: List<YEntryCandidate>,
    ) {
        val previous =
            prefs.getStringSet("seen_candidates", emptySet()).orEmpty()
        val seen = previous.toMutableSet()
        candidates.forEach {
            if (
                it.surface !in
                setOf(
                    YEntrySurface.Tile,
                    YEntrySurface.Shortcut,
                    YEntrySurface.Widget,
                )
            ) {
                seen += it.id
            }
        }
        if (seen != previous) {
            prefs.edit()
                .putStringSet("seen_candidates", seen)
                .apply()
        }
    }

    private fun historical(): List<YEntryCandidate> {
        val seen =
            prefs.getStringSet(
                "seen_candidates",
                emptySet(),
            ).orEmpty()
        val hidden = hidden()
        val locked = locked()
        val shown = shown()
        val unlocked = unlocked()
        val componentTitles = componentTitles()
        return seen.mapNotNull { id ->
            val p = id.split('|', limit = 4)
            if (p.size != 4) return@mapNotNull null
            val packageName = p[2]
            val installed =
                runCatching {
                    pm.getApplicationInfo(
                        packageName,
                        0,
                    )
                }.getOrNull()
            YEntryCandidate(
                id = id,
                surface = YEntrySurface.Historical,
                qualifier = p[1],
                packageName = packageName,
                className = p[3],
                label =
                    componentTitles[id]
                        ?: installed?.let {
                            runCatching {
                                pm.getApplicationLabel(it)
                                    .toString()
                            }.getOrNull()
                        } ?: packageName,
                system =
                    installed?.flags
                        ?.and(
                            ApplicationInfo.FLAG_SYSTEM,
                        ) != 0,
                hidden = YEntryRuleSelection.isSelected(
                    id, inheritedRuleKey(id) ?: id, hidden, shown,
                ),
                locked = YEntryRuleSelection.isSelected(
                    id, inheritedRuleKey(id) ?: id, locked, unlocked,
                ),
                priority = null,
                state =
                    if (installed == null) {
                        YEntryCandidateState.Historical
                    } else {
                        YEntryCandidateState.Restricted
                    },
            )
        }
    }

    suspend fun changeComponent(
        candidate: YEntryCandidate,
        enable: Boolean,
    ): Boolean {
        require(
            candidate.surface in
                setOf(
                    YEntrySurface.Tile,
                    YEntrySurface.Shortcut,
                    YEntrySurface.Widget,
                ),
        )
        require(!candidate.rootBlocked)
        val component =
            ComponentName(
                candidate.packageName,
                candidate.className,
            )
        val flat = component.flattenToString()
        val command =
            if (enable) {
                "pm enable --user " +
                    user +
                    " '" +
                    flat.replace("'", "'\\''") +
                    "'"
            } else {
                "pm disable --user " +
                    user +
                    " '" +
                    flat.replace("'", "'\\''") +
                    "'"
            }
        val result =
            when (
                val outcome =
                    root.execute(
                        RootRequest(
                            command = command,
                            timeoutMillis = 8_000L,
                        ),
                    )
            ) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> return false
            }
        if (result.exitCode != 0) return false

        val expected =
            if (enable) {
                PackageManager
                    .COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager
                    .COMPONENT_ENABLED_STATE_DISABLED
            }
        var observed = -1
        repeat(5) {
            observed =
                runCatching {
                    pm.getComponentEnabledSetting(
                        component,
                    )
                }.getOrDefault(-1)
            if (observed == expected) {
                return@repeat
            }
            delay(100L)
        }
        if (observed != expected) return false

        val next =
            disabledComponents().toMutableSet()
        val key =
            YEntryRuntimeBridge.componentKey(
                user,
                candidate.packageName,
                candidate.className,
            )
        if (enable) next -= key else next += key
        prefs.edit()
            .putStringSet(
                "disabled_components",
                next,
            )
            .apply()
        sync()
        return true
    }

    fun movePriority(
        candidate: YEntryCandidate,
        delta: Int,
    ) {
        val current = effectivePriority(candidate).toMutableList()
        val previous = current.indexOfFirst {
            it == candidate.id || it == candidate.packageName
        }
        if (previous >= 0) current.removeAt(previous)
        val index = if (previous >= 0) {
            (previous + delta).coerceIn(0, current.size)
        } else if (delta < 0) {
            0
        } else {
            current.size
        }
        current.add(index, candidate.id)
        setPriority(candidate.surface, candidate.qualifier, current)
    }

    fun removePriority(candidate: YEntryCandidate) {
        val next = effectivePriority(candidate)
            .filterNot { it == candidate.id || it == candidate.packageName }
        setPriority(candidate.surface, candidate.qualifier, next)
    }

    fun setAppPriority(
        candidates: List<YEntryCandidate>,
        packageName: String,
        pinned: Boolean,
    ) {
        val first = candidates.firstOrNull {
            it.packageName == packageName && !it.locked
        } ?: return
        if (first.surface in setOf(
            YEntrySurface.Tile, YEntrySurface.Shortcut,
            YEntrySurface.Widget, YEntrySurface.Historical,
        )) return
        val relevant = candidates.filter { it.packageName == packageName }
            .mapTo(hashSetOf()) { it.id }
        val retained = effectivePriority(first).filterNot {
            it == packageName || it in relevant
        }
        setPriority(
            first.surface, first.qualifier,
            if (pinned) (listOf(packageName) + retained).take(200) else retained,
        )
    }

    fun moveAppPriority(
        candidates: List<YEntryCandidate>,
        packageName: String,
        delta: Int,
    ) {
        if (delta !in setOf(-1, 1)) return
        val first = candidates.firstOrNull {
            it.packageName == packageName && !it.locked
        } ?: return
        if (first.surface in setOf(
            YEntrySurface.Tile, YEntrySurface.Shortcut,
            YEntrySurface.Widget, YEntrySurface.Historical,
        )) return
        val ordered = candidates.groupBy { it.packageName }.entries.sortedWith(
            compareBy<Map.Entry<String, List<YEntryCandidate>>> {
                it.value.minOfOrNull { candidate -> candidate.priority ?: Int.MAX_VALUE }
                    ?: Int.MAX_VALUE
            }.thenBy { it.key.lowercase() },
        ).map { it.key }.toMutableList()
        val from = ordered.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return
        ordered.add(to, ordered.removeAt(from))
        val visiblePackages = ordered.toSet()
        val visibleIds = candidates.mapTo(hashSetOf()) { it.id }
        val retained = effectivePriority(first).filterNot {
            it in visiblePackages || it in visibleIds
        }
        setPriority(first.surface, first.qualifier, (ordered + retained).take(200))
    }

    private fun effectivePriority(candidate: YEntryCandidate): List<String> =
        qualifierCandidates(candidate.surface, candidate.qualifier)
            .firstNotNullOfOrNull { qualifier ->
                if (prefs.contains(priorityKey(candidate.surface, qualifier))) {
                    priority(candidate.surface, qualifier)
                } else null
            } ?: emptyList()

    suspend fun bulkComponents(
        candidates: List<YEntryCandidate>,
        enable: Boolean,
    ): Pair<Int, Int> {
        var changed = 0
        var failed = 0
        val locks = locked()
        candidates
            .filter {
                !it.locked &&
                    it.id !in locks &&
                    !it.rootBlocked
            }
            .forEach {
                if (
                    changeComponent(
                        it,
                        enable,
                    )
                ) {
                    changed += 1
                } else {
                    failed += 1
                }
            }
        return changed to failed
    }

    suspend fun invertComponents(
        candidates: List<YEntryCandidate>,
    ): Pair<Int, Int> {
        var changed = 0
        var failed = 0
        val locks = locked()
        candidates
            .filter {
                !it.locked &&
                    it.id !in locks &&
                    !it.rootBlocked &&
                    it.rootEnabled != null
            }
            .forEach {
                if (
                    changeComponent(
                        it,
                        !(it.rootEnabled ?: true),
                    )
                ) {
                    changed += 1
                } else {
                    failed += 1
                }
            }
        return changed to failed
    }

    suspend fun discoverBrowserHosts():
        List<String> {
        val result =
            when (
                val outcome =
                    root.execute(
                        RootRequest(
                            command =
                                "pm get-app-links --user cur 2>/dev/null || true",
                            timeoutMillis =
                                15_000L,
                        ),
                    )
            ) {
                is Outcome.Success ->
                    outcome.value.stdout
                is Outcome.Failure ->
                    ""
            }
        val hosts = linkedSetOf<String>()
        hosts.addAll(
            prefs.getStringSet("browser_hosts", emptySet()).orEmpty(),
        )
        result.lineSequence()
            .forEach { raw ->
                val line = raw.trim()
                val match =
                    Regex(
                        "([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)(?::|\\s)",
                    ).find(line)
                val host =
                    match?.groupValues
                        ?.getOrNull(1)
                        ?.lowercase()
                        ?.removePrefix(
                            "www.",
                        )
                if (
                    !host.isNullOrBlank() &&
                    host.count {
                        it == '.'
                    } >= 1
                ) {
                    hosts += host
                }
            }
        return hosts
            .take(192)
    }

    fun exportBackup(): String {
        val values =
            JSONObject()
        prefs.all.forEach {
            (key, value) ->
            when (value) {
                is String ->
                    values.put(
                        key,
                        value,
                    )
                is Boolean ->
                    values.put(
                        key,
                        value,
                    )
                is Int ->
                    values.put(
                        key,
                        value,
                    )
                is Long ->
                    values.put(
                        key,
                        value,
                    )
                is Float ->
                    values.put(
                        key,
                        value.toDouble(),
                    )
                is Set<*> ->
                    values.put(
                        key,
                        JSONArray(
                            value.filterIsInstance<String>()
                                .sorted(),
                        ),
                    )
            }
        }
        val payload =
            JSONObject().apply {
                put("format", "YSuite.YEntryCleaner")
                put("version", 1)
                put("values", values)
            }.toString(2)

        val resolver =
            context.contentResolver
        val uri =
            checkNotNull(
                resolver.insert(
                    MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(
                            MediaStore.MediaColumns
                                .DISPLAY_NAME,
                            "YEntryCleaner-" +
                                System.currentTimeMillis() +
                                ".json",
                        )
                        put(
                            MediaStore.MediaColumns
                                .MIME_TYPE,
                            "application/json",
                        )
                        put(
                            MediaStore.MediaColumns
                                .RELATIVE_PATH,
                            Environment
                                .DIRECTORY_DOWNLOADS +
                                "/YSuite",
                        )
                    },
                ),
            )
        resolver.openOutputStream(uri)
            ?.bufferedWriter()
            ?.use {
                it.write(payload)
            }
            ?: error(
                "Unable to export backup",
            )
        return uri.toString()
    }

    suspend fun importBackup(
        uri: Uri,
    ): YEntryImportResult {
        val text =
            context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { reader ->
                    val out =
                        StringBuilder()
                    val buffer =
                        CharArray(8192)
                    while (true) {
                        val count =
                            reader.read(buffer)
                        if (count < 0) break
                        if (
                            out.length + count >
                            2_000_000
                        ) {
                            return YEntryImportResult.Invalid
                        }
                        out.append(
                            buffer,
                            0,
                            count,
                        )
                    }
                    out.toString()
                } ?: return YEntryImportResult.Invalid
        val imported = YEntryBackupParser.parse(text) ?: return YEntryImportResult.Invalid

        // Validate the entire document before changing anything. Preserve the
        // device's actual disabled components and unrelated local settings.
        val editor = prefs.edit()
            .remove("hidden_rules")
            .remove("locked_rules")
            .remove("shown_rules")
            .remove("unlocked_rules")
            .remove("seen_candidates")
            .remove("open_custom_definitions")
            .remove("component_titles")
            .remove("browser_hosts")
            .remove("priority_qualifiers")
        prefs.all.keys
            .filter { it.startsWith("priority_") }
            .forEach { editor.remove(it) }
        for ((key, value) in imported) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(
                    key,
                    value.filterIsInstance<String>().toSet(),
                )
                else -> return YEntryImportResult.Invalid
            }
        }
        if (!editor.commit()) return YEntryImportResult.Invalid
        return if (sync() is Outcome.Success) {
            YEntryImportResult.Synced
        } else {
            YEntryImportResult.SavedLocally
        }
    }

    suspend fun bulkHidden(
        candidates: List<YEntryCandidate>,
        hidden: Boolean,
    ): Outcome<Unit> {
        val locks = locked()
        val next = this.hidden().toMutableSet()
        val exceptions = shown().toMutableSet()
        candidates
            .filter { !it.locked && it.id !in locks }
            .forEach {
                if (hidden) {
                    next += it.id
                    exceptions -= it.id
                } else {
                    next -= it.id
                    if (hasInheritedRule(it.id, next)) exceptions += it.id
                    else exceptions -= it.id
                }
            }
        prefs.edit()
            .putStringSet("hidden_rules", next)
            .putStringSet("shown_rules", exceptions)
            .apply()
        return sync()
    }

    /** Publish settings without restarting framework processes merely by opening the screen. */
    /**
     * OEM resolver processes (ColorOS/OPlus, HyperOS, etc.) may host chooser
     * UI outside com.android.intentresolver. Only detect real system resolver
     * activities; never request Hook scope for arbitrary user apps.
     */
    @Suppress("DEPRECATION")
    private fun resolverHostPackages(): Set<String> {
        val probes = listOf(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain"), null,
            ),
            Intent.createChooser(
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*"), null,
            ),
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    Uri.parse("content://com.yagay.ysuite.probe/document"),
                    "application/pdf",
                ),
        )
        return probes.mapNotNull { intent ->
            runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo
            }.getOrNull()?.takeIf { info ->
                val name = info.name
                val system =
                    info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                system && (name.endsWith("ResolverActivity") ||
                    name.endsWith("ChooserActivity") ||
                    name.contains("ResolverActivity") ||
                    name.contains("ChooserActivity"))
            }?.packageName
        }.filterNot { it == "android" || it == context.packageName }.toSet()
    }

    suspend fun sync(reload: Boolean = true): Outcome<Unit> =
        syncMutex.withLock {
        val resolverHosts = resolverHostPackages()
        val indexedQualifiers =
            prefs.getStringSet("priority_qualifiers", emptySet()).orEmpty()
        val browserHosts =
            prefs.getStringSet("browser_hosts", emptySet()).orEmpty() +
                browserHost()
        val priorityLines =
            YEntrySurface.entries
                .filter {
                    it !in
                        setOf(
                            YEntrySurface.Tile,
                            YEntrySurface.Shortcut,
                            YEntrySurface.Widget,
                            YEntrySurface.Historical,
                        )
                }
                .flatMap { surface ->
                    val qualifiers =
                        when (surface) {
                            YEntrySurface.ShareText ->
                                listOf("text/plain", "*")
                            YEntrySurface.ShareImage ->
                                listOf("image/*", "*")
                            YEntrySurface.ShareMultiple ->
                                listOf("*")
                            YEntrySurface.ProcessText ->
                                listOf("text/plain", "*")
                            YEntrySurface.Browser ->
                                browserHosts.toList() + "*"
                            YEntrySurface.Open ->
                                listOf(openMime(), "*")
                            else -> emptyList()
                        }
                    val knownQualifiers = indexedQualifiers.mapNotNull { key ->
                        val prefix = surface.name + "|"
                        key.takeIf { it.startsWith(prefix) }
                            ?.removePrefix(prefix)
                    }
                    (qualifiers + knownQualifiers).distinct().mapNotNull {
                        qualifier ->
                        val list =
                            priority(
                                surface,
                                qualifier,
                            )
                        if (
                            list.isEmpty() &&
                            !prefs.contains(priorityKey(surface, qualifier))
                        ) null
                        else
                            surface.name +
                                "|" +
                                qualifier +
                                "\t" +
                                list.joinToString(">")
                    }
                }
        val values =
            linkedMapOf(
                YEntryRuntimeBridge.KEY_MODE to
                    displayMode(),
                YEntryRuntimeBridge.KEY_RESOLVER_HOSTS to
                    resolverHosts.sorted().joinToString("\n"),
                YEntryRuntimeBridge.KEY_HIDDEN_RULES to
                    hidden().sorted()
                        .joinToString("\n"),
                YEntryRuntimeBridge.KEY_SHOWN_RULES to
                    shown().sorted()
                        .joinToString("\n"),
                YEntryRuntimeBridge.KEY_OPEN_CUSTOM_DEFINITIONS to
                    (prefs.getString("open_custom_definitions", "{}") ?: "{}"),
                YEntryRuntimeBridge.KEY_PRIORITIES to
                    priorityLines
                        .joinToString("\n"),
                YEntryRuntimeBridge.KEY_DISABLED_COMPONENTS to
                    disabledComponents().sorted()
                        .joinToString("\n"),
                YEntryRuntimeBridge.KEY_DIAGNOSTIC to
                    diagnostic().toString(),
                YEntryRuntimeBridge.KEY_MANAGER_APP_ID to
                    (Process.myUid() % 100_000)
                        .toString(),
                YEntryRuntimeBridge
                    .KEY_COMPONENT_DISCOVERY_PROTOCOL to
                    YEntryRuntimeBridge
                        .COMPONENT_DISCOVERY_PROTOCOL
                        .toString(),
            )
        for ((key, value) in values) {
            val result =
                hooks.writeConfig(
                    YEntryRuntimeBridge.GROUP,
                    key,
                    value,
                )
            if (result is Outcome.Failure) {
                return@withLock result
            }
        }
        if (reload) {
            hooks.reload(
                setOf(
                    "android",
                    "com.android.intentresolver",
                ) + resolverHosts,
            )
        } else {
            Outcome.Success(Unit)
        }
    }
}
