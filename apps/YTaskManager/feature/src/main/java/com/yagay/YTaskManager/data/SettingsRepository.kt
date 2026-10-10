package com.yagay.YTaskManager.data

import android.content.Context
import com.yagay.suite.api.FeatureSettings
import com.yagay.YTaskManager.model.ProcessSort

class SettingsRepository(context: Context) {
    private val prefs = FeatureSettings.named(context, "taskmanager_settings")

    var autoRefresh: Boolean
        get() = prefs.boolean("proc_auto_refresh", true)
        set(value) = prefs.putBoolean("proc_auto_refresh", value)

    var refreshIntervalMs: Long
        get() = prefs.long("update_frequency", 800L).coerceIn(250L, 10_000L)
        set(value) = prefs.putLong("update_frequency", value.coerceIn(250L, 10_000L))

    var showSystemApps: Boolean
        get() = prefs.boolean("show_system_apps", true)
        set(value) = prefs.putBoolean("show_system_apps", value)

    var showUserApps: Boolean
        get() = prefs.boolean("show_user_apps", true)
        set(value) = prefs.putBoolean("show_user_apps", value)

    var showLinuxProcesses: Boolean
        get() = prefs.boolean("show_linux_process", false)
        set(value) = prefs.putBoolean("show_linux_process", value)

    var confirmKill: Boolean
        get() = prefs.boolean("confirm_kill", true)
        set(value) = prefs.putBoolean("confirm_kill", value)

    var sort: ProcessSort
        get() = runCatching {
            ProcessSort.valueOf(prefs.string("sort_by", ProcessSort.MEMORY.name)!!)
        }.getOrDefault(ProcessSort.MEMORY)
        set(value) = prefs.putString("sort_by", value.name)

    var pinnedProcesses: Set<String>
        get() = prefs.stringSet("pinned_processes", emptySet())
        set(value) = prefs.putStringSet("pinned_processes", value.toSet())

    fun togglePinned(key: String): Boolean {
        val current = pinnedProcesses.toMutableSet()
        val nowPinned = if (key in current) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        pinnedProcesses = current
        return nowPinned
    }
}
