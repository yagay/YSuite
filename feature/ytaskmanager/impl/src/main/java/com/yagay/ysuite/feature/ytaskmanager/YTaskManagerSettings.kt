package com.yagay.ysuite.feature.ytaskmanager

import android.content.Context
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessSort

internal class YTaskManagerSettings(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "ytaskmanager_settings",
            Context.MODE_PRIVATE,
        )

    var autoRefresh: Boolean
        get() = prefs.getBoolean("auto_refresh", true)
        set(value) {
            prefs.edit().putBoolean("auto_refresh", value).apply()
        }

    var refreshIntervalMs: Long
        get() =
            prefs.getLong("refresh_interval_ms", 800L)
                .coerceIn(500L, 2_000L)
        set(value) {
            prefs.edit()
                .putLong(
                    "refresh_interval_ms",
                    value.coerceIn(500L, 2_000L),
                )
                .apply()
        }

    var showUser: Boolean
        get() = prefs.getBoolean("show_user", true)
        set(value) {
            prefs.edit().putBoolean("show_user", value).apply()
        }

    var showSystem: Boolean
        get() = prefs.getBoolean("show_system", true)
        set(value) {
            prefs.edit().putBoolean("show_system", value).apply()
        }

    var showLinux: Boolean
        get() = prefs.getBoolean("show_linux", false)
        set(value) {
            prefs.edit().putBoolean("show_linux", value).apply()
        }

    var confirmKill: Boolean
        get() = prefs.getBoolean("confirm_kill", true)
        set(value) {
            prefs.edit().putBoolean("confirm_kill", value).apply()
        }

    var sort: YTaskProcessSort
        get() =
            runCatching {
                YTaskProcessSort.valueOf(
                    prefs.getString(
                        "sort",
                        YTaskProcessSort.Memory.name,
                    ) ?: YTaskProcessSort.Memory.name,
                )
            }.getOrDefault(YTaskProcessSort.Memory)
        set(value) {
            prefs.edit().putString("sort", value.name).apply()
        }

    var pinned: Set<String>
        get() =
            prefs.getStringSet(
                "pinned",
                emptySet(),
            )?.toSet().orEmpty()
        set(value) {
            prefs.edit()
                .putStringSet("pinned", value.toSet())
                .apply()
        }

    fun togglePin(process: YTaskProcess): Boolean {
        val key = pinKey(process)
        val next = pinned.toMutableSet()
        val enabled =
            if (key in next) {
                next.remove(key)
                false
            } else {
                next.add(key)
                true
            }
        pinned = next
        return enabled
    }

    fun isPinned(process: YTaskProcess): Boolean =
        pinKey(process) in pinned

    private fun pinKey(process: YTaskProcess): String =
        process.packageName
            ?: process.command.takeIf { it.isNotBlank() }
            ?: "pid:" + process.pid
}
