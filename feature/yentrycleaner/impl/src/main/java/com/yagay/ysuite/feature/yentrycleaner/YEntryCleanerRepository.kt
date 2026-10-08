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
import com.yagay.ysuite.feature.yentrycleaner.runtime.YEntryRuntimeBridge
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

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

    fun hidden(): Set<String> =
        prefs.getStringSet("hidden_rules", emptySet())
            ?.toSet().orEmpty()

    fun locked(): Set<String> =
        prefs.getStringSet("locked_rules", emptySet())
            ?.toSet().orEmpty()

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
        val normalized =
            value.trim()
                .lowercase()
                .ifBlank {
                    "application/pdf"
                }
        prefs.edit()
            .putString(
                "open_mime",
                normalized,
            )
            .apply()
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
        prefs.edit()
            .putString(
                priorityKey(surface, qualifier),
                ids.distinct().joinToString(">"),
            )
            .apply()
    }

    fun setHidden(id: String, value: Boolean) {
        val next = hidden().toMutableSet()
        if (value) next += id else next -= id
        prefs.edit()
            .putStringSet("hidden_rules", next)
            .apply()
    }

    fun setLocked(id: String, value: Boolean) {
        setLocked(setOf(id), value)
    }

    fun setLocked(ids: Set<String>, value: Boolean) {
        val next = locked().toMutableSet()
        if (value) next.addAll(ids) else next.removeAll(ids)
        prefs.edit()
            .putStringSet("locked_rules", next)
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
                YEntrySurface.Open ->
                    queryIntent(
                        Intent(Intent.ACTION_VIEW)
                            .setType(openMime()),
                        YEntrySurface.Open,
                        openMime(),
                    )
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
        val configuredOrder =
            if (prefs.contains(priorityKey(surface, qualifier))) {
                priority(surface, qualifier)
            } else {
                priority(surface, "*")
            }
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
                    runCatching {
                        ri.loadLabel(pm).toString()
                    }.getOrDefault(
                        ai.packageName,
                    ),
                system =
                    ai.applicationInfo.flags and
                        ApplicationInfo.FLAG_SYSTEM != 0,
                hidden = id in hidden || wildcardId in hidden,
                locked = id in locked || wildcardId in locked,
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
        val seen =
            prefs.getStringSet(
                "seen_candidates",
                emptySet(),
            )?.toMutableSet()
                ?: mutableSetOf()
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
        prefs.edit()
            .putStringSet(
                "seen_candidates",
                seen,
            )
            .apply()
    }

    private fun historical(): List<YEntryCandidate> {
        val seen =
            prefs.getStringSet(
                "seen_candidates",
                emptySet(),
            ).orEmpty()
        val hidden = hidden()
        val locked = locked()
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
                    installed?.let {
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
                hidden = id in hidden,
                locked = id in locked,
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

    private fun effectivePriority(candidate: YEntryCandidate): List<String> =
        if (prefs.contains(priorityKey(candidate.surface, candidate.qualifier))) {
            priority(candidate.surface, candidate.qualifier)
        } else {
            priority(candidate.surface, "*")
        }

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
    ): Boolean {
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
                            return false
                        }
                        out.append(
                            buffer,
                            0,
                            count,
                        )
                    }
                    out.toString()
                } ?: return false
        val imported = YEntryBackupParser.parse(text) ?: return false

        // Validate the entire document before changing anything. Preserve the
        // device's actual disabled components and unrelated local settings.
        val editor = prefs.edit()
            .remove("hidden_rules")
            .remove("locked_rules")
            .remove("seen_candidates")
            .remove("browser_hosts")
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
                else -> return false
            }
        }
        if (!editor.commit()) return false
        return sync() is Outcome.Success
    }

    suspend fun bulkHidden(
        candidates: List<YEntryCandidate>,
        hidden: Boolean,
    ): Outcome<Unit> {
        val locks = locked()
        val next = this.hidden().toMutableSet()
        candidates
            .filter { !it.locked && it.id !in locks }
            .forEach {
                if (hidden) next += it.id
                else next -= it.id
            }
        prefs.edit()
            .putStringSet(
                "hidden_rules",
                next,
            )
            .apply()
        return sync()
    }

    /** Publish settings without restarting framework processes merely by opening the screen. */
    suspend fun sync(reload: Boolean = true): Outcome<Unit> {
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
                                listOf(
                                    browserHost(),
                                    "*",
                                )
                            YEntrySurface.Open ->
                                listOf(openMime(), "*")
                            else -> emptyList()
                        }
                    qualifiers.mapNotNull {
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
                YEntryRuntimeBridge.KEY_HIDDEN_RULES to
                    hidden().sorted()
                        .joinToString("\n"),
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
                return result
            }
        }
        return if (reload) {
            hooks.reload(
                setOf(
                    "android",
                    "com.android.intentresolver",
                ),
            )
        } else {
            Outcome.Success(Unit)
        }
    }
}
