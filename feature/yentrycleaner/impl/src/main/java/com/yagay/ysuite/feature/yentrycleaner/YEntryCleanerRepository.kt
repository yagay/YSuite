package com.yagay.ysuite.feature.yentrycleaner

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidate
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCandidateState
import com.yagay.ysuite.feature.yentrycleaner.api.YEntrySurface
import com.yagay.ysuite.feature.yentrycleaner.runtime.YEntryRuntimeBridge
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import kotlinx.coroutines.delay

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
        val next = locked().toMutableSet()
        if (value) next += id else next -= id
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
                YEntrySurface.Open ->
                    queryOpen()
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

    private fun queryOpen(): List<YEntryCandidate> {
        val types =
            listOf(
                "application/pdf",
                "image/*",
                "video/*",
                "audio/*",
                "text/plain",
            )
        return types.flatMap { mime ->
            queryIntent(
                Intent(Intent.ACTION_VIEW)
                    .setType(mime),
                YEntrySurface.Open,
                mime,
            )
        }.distinctBy { it.id }
    }

    @Suppress("DEPRECATION")
    private fun queryIntent(
        intent: Intent,
        surface: YEntrySurface,
        qualifier: String,
    ): List<YEntryCandidate> {
        val hidden = hidden()
        val locked = locked()
        val rank =
            priority(surface, qualifier)
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
                hidden = id in hidden,
                locked = id in locked,
                priority = rank[id],
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
        val infos =
            when (surface) {
                YEntrySurface.Tile ->
                    pm.queryIntentServices(
                        intent,
                        PackageManager.MATCH_ALL or
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                    ).mapNotNull {
                        it.serviceInfo
                    }
                YEntrySurface.Shortcut ->
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.MATCH_ALL or
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                    ).mapNotNull {
                        it.activityInfo
                    }
                YEntrySurface.Widget ->
                    pm.queryBroadcastReceivers(
                        intent,
                        PackageManager.MATCH_ALL or
                            PackageManager.MATCH_DISABLED_COMPONENTS,
                    ).mapNotNull {
                        it.activityInfo
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
                rootBlocked =
                    info.packageName ==
                        context.packageName ||
                        info.packageName ==
                        "android" ||
                        info.packageName ==
                        "com.android.systemui",
            )
        }.sortedBy { it.label.lowercase() }
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
        val current =
            priority(
                candidate.surface,
                candidate.qualifier,
            ).toMutableList()
        current.remove(candidate.id)
        val base =
            candidate.priority
                ?: if (delta < 0) {
                    current.size
                } else {
                    -1
                }
        val index =
            (base + delta)
                .coerceIn(0, current.size)
        current.add(index, candidate.id)
        setPriority(
            candidate.surface,
            candidate.qualifier,
            current,
        )
    }

    suspend fun bulkHidden(
        candidates: List<YEntryCandidate>,
        hidden: Boolean,
    ) {
        val locks = locked()
        val next = this.hidden().toMutableSet()
        candidates
            .filter { it.id !in locks }
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
        sync()
    }

    suspend fun sync(): Outcome<Unit> {
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
                                listOf("text/plain")
                            YEntrySurface.ShareImage ->
                                listOf("image/*")
                            YEntrySurface.Browser ->
                                listOf(
                                    browserHost(),
                                    "*",
                                )
                            YEntrySurface.Open ->
                                listOf(
                                    "application/pdf",
                                    "image/*",
                                    "video/*",
                                    "audio/*",
                                    "text/plain",
                                )
                            else -> emptyList()
                        }
                    qualifiers.mapNotNull {
                        qualifier ->
                        val list =
                            priority(
                                surface,
                                qualifier,
                            )
                        if (list.isEmpty()) null
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
        return hooks.reload(
            setOf(
                "android",
                "com.android.intentresolver",
            ),
        )
    }
}
