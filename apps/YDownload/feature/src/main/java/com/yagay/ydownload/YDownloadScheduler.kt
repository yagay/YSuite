package com.yagay.ydownload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YActionRow
import com.yagay.yui.YSection
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YStatusLine
import com.yagay.yui.YStatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class YDownloadPriority { HIGH, NORMAL, LOW }
enum class YDownloadNetworkRule { ANY, UNMETERED, WIFI_ONLY }

data class YDownloadTaskMeta(
    val group: String = "",
    val tag: String = "",
    val priority: YDownloadPriority = YDownloadPriority.NORMAL,
    val networkRule: YDownloadNetworkRule = YDownloadNetworkRule.ANY,
    /** Internal marker: true only when the scheduler, not the user, paused this task. */
    val schedulerPaused: Boolean = false,
) {
    fun normalized(): YDownloadTaskMeta = copy(
        group = group.trim().take(64),
        tag = tag.trim().take(96),
    )
}

data class YDownloadScheduleResult(
    val started: Int,
    val paused: Int,
    val blocked: Int,
    val managed: Int,
)

class YDownloadTaskMetaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun defaults(): YDownloadTaskMeta = YDownloadTaskMeta(
        group = prefs.getString(KEY_DEFAULT_GROUP, "").orEmpty(),
        tag = prefs.getString(KEY_DEFAULT_TAG, "").orEmpty(),
        priority = enumValue(
            prefs.getString(KEY_DEFAULT_PRIORITY, YDownloadPriority.NORMAL.name),
            YDownloadPriority.NORMAL,
        ),
        networkRule = enumValue(
            prefs.getString(KEY_DEFAULT_NETWORK, YDownloadNetworkRule.ANY.name),
            YDownloadNetworkRule.ANY,
        ),
        schedulerPaused = false,
    ).normalized()

    fun updateDefaults(block: (YDownloadTaskMeta) -> YDownloadTaskMeta): YDownloadTaskMeta {
        val next = block(defaults()).copy(schedulerPaused = false).normalized()
        prefs.edit()
            .putString(KEY_DEFAULT_GROUP, next.group)
            .putString(KEY_DEFAULT_TAG, next.tag)
            .putString(KEY_DEFAULT_PRIORITY, next.priority.name)
            .putString(KEY_DEFAULT_NETWORK, next.networkRule.name)
            .apply()
        return next
    }

    fun autoRebalance(): Boolean = prefs.getBoolean(KEY_AUTO_REBALANCE, true)

    fun setAutoRebalance(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REBALANCE, enabled).apply()
    }

    fun get(id: Long): YDownloadTaskMeta {
        val raw = prefs.getString(taskKey(id), null) ?: return defaults()
        return runCatching {
            val json = JSONObject(raw)
            YDownloadTaskMeta(
                group = json.optString("group"),
                tag = json.optString("tag"),
                priority = enumValue(json.optString("priority"), YDownloadPriority.NORMAL),
                networkRule = enumValue(json.optString("networkRule"), YDownloadNetworkRule.ANY),
                schedulerPaused = json.optBoolean("schedulerPaused", false),
            ).normalized()
        }.getOrElse { defaults() }
    }

    fun put(id: Long, meta: YDownloadTaskMeta) {
        val value = meta.normalized()
        val json = JSONObject().apply {
            put("group", value.group)
            put("tag", value.tag)
            put("priority", value.priority.name)
            put("networkRule", value.networkRule.name)
            put("schedulerPaused", value.schedulerPaused)
        }
        prefs.edit().putString(taskKey(id), json.toString()).apply()
    }

    fun cleanup(existingIds: Set<Long>) {
        val editor = prefs.edit()
        var changed = false
        prefs.all.keys.forEach { key ->
            if (!key.startsWith(TASK_PREFIX)) return@forEach
            val id = key.removePrefix(TASK_PREFIX).toLongOrNull() ?: return@forEach
            if (id !in existingIds) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun taskKey(id: Long): String = TASK_PREFIX + id

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)

    companion object {
        private const val PREFS = "ydownload_scheduler"
        private const val KEY_DEFAULT_GROUP = "default_group"
        private const val KEY_DEFAULT_TAG = "default_tag"
        private const val KEY_DEFAULT_PRIORITY = "default_priority"
        private const val KEY_DEFAULT_NETWORK = "default_network"
        private const val KEY_AUTO_REBALANCE = "auto_rebalance"
        private const val TASK_PREFIX = "task_"
    }
}

object YDownloadScheduler {
    private val lock = Any()

    fun rebalance(context: Context, store: DownloadStore): YDownloadScheduleResult = synchronized(lock) {
        val appContext = context.applicationContext
        val metaStore = YDownloadTaskMetaStore(appContext)
        val items = store.items.value
        metaStore.cleanup(items.mapTo(mutableSetOf()) { it.id })
        val managed = items.filter {
            it.backend == DownloadBackend.ENHANCED &&
                it.state in setOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED)
        }
        val network = currentNetwork(appContext)
        val eligible = managed.filter { task ->
            val meta = metaStore.get(task.id)
            network.allows(meta.networkRule) &&
                (task.state != DownloadState.PAUSED || meta.schedulerPaused)
        }.sortedWith(
            compareBy<DownloadItem> { priorityRank(metaStore.get(it.id).priority) }
                .thenBy { it.id },
        )
        val concurrency = YDownloadEnhancedSettings.load(appContext).maxConcurrent.coerceAtLeast(1)
        val targets = eligible.take(concurrency)
        val targetIds = targets.mapTo(mutableSetOf()) { it.id }

        var paused = 0
        managed.filter {
            it.id !in targetIds && it.state in setOf(DownloadState.RUNNING, DownloadState.QUEUED)
        }.forEach { task ->
            val meta = metaStore.get(task.id)
            if (!meta.schedulerPaused) metaStore.put(task.id, meta.copy(schedulerPaused = true))
            DownloadService.pause(appContext, task.id)
            paused++
        }

        var started = 0
        targets.forEach { task ->
            val meta = metaStore.get(task.id)
            when {
                task.state == DownloadState.RUNNING -> {
                    if (meta.schedulerPaused) metaStore.put(task.id, meta.copy(schedulerPaused = false))
                }
                task.state == DownloadState.QUEUED ||
                    (task.state == DownloadState.PAUSED && meta.schedulerPaused) -> {
                    if (meta.schedulerPaused) metaStore.put(task.id, meta.copy(schedulerPaused = false))
                    if (task.state == DownloadState.PAUSED) {
                        store.update(task.id) { it.copy(state = DownloadState.QUEUED, error = null) }
                    }
                    DownloadService.start(appContext, task.id)
                    started++
                }
            }
        }

        YDownloadScheduleResult(
            started = started,
            paused = paused,
            blocked = managed.count { !network.allows(metaStore.get(it.id).networkRule) },
            managed = managed.size,
        )
    }

    fun pauseGroup(context: Context, store: DownloadStore, group: String): Int {
        val normalized = group.trim()
        if (normalized.isBlank()) return 0
        val metaStore = YDownloadTaskMetaStore(context)
        val targets = store.items.value.filter {
            it.backend == DownloadBackend.ENHANCED &&
                metaStore.get(it.id).group == normalized &&
                it.state in setOf(DownloadState.RUNNING, DownloadState.QUEUED, DownloadState.PAUSED)
        }
        targets.forEach { task ->
            val meta = metaStore.get(task.id)
            if (meta.schedulerPaused) metaStore.put(task.id, meta.copy(schedulerPaused = false))
            if (task.state in setOf(DownloadState.RUNNING, DownloadState.QUEUED)) {
                DownloadService.pause(context, task.id)
            }
        }
        return targets.size
    }

    fun resumeGroup(context: Context, store: DownloadStore, group: String): YDownloadScheduleResult {
        val normalized = group.trim()
        if (normalized.isNotBlank()) {
            val metaStore = YDownloadTaskMetaStore(context)
            store.items.value.filter {
                it.backend == DownloadBackend.ENHANCED &&
                    metaStore.get(it.id).group == normalized &&
                    it.state == DownloadState.PAUSED
            }.forEach { task ->
                val meta = metaStore.get(task.id)
                metaStore.put(task.id, meta.copy(schedulerPaused = false))
                store.update(task.id) { it.copy(state = DownloadState.QUEUED, error = null) }
            }
        }
        return rebalance(context, store)
    }

    private fun currentNetwork(context: Context): NetworkState {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkState(false, true, false)
        val network = manager.activeNetwork ?: return NetworkState(false, manager.isActiveNetworkMetered, false)
        val caps = manager.getNetworkCapabilities(network)
            ?: return NetworkState(false, manager.isActiveNetworkMetered, false)
        return NetworkState(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            metered = manager.isActiveNetworkMetered,
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        )
    }

    private fun priorityRank(priority: YDownloadPriority): Int = when (priority) {
        YDownloadPriority.HIGH -> 0
        YDownloadPriority.NORMAL -> 1
        YDownloadPriority.LOW -> 2
    }

    private data class NetworkState(val connected: Boolean, val metered: Boolean, val wifi: Boolean) {
        fun allows(rule: YDownloadNetworkRule): Boolean = when (rule) {
            YDownloadNetworkRule.ANY -> connected
            YDownloadNetworkRule.UNMETERED -> connected && !metered
            YDownloadNetworkRule.WIFI_ONLY -> connected && wifi
        }
    }
}

@Composable
fun YDownloadSchedulerCard(context: Context) {
    val store = remember(context) { DownloadStore.get(context) }
    val metaStore = remember(context) { YDownloadTaskMetaStore(context) }
    val items by store.items.collectAsState()
    val scope = rememberCoroutineScope()
    var defaults by remember { mutableStateOf(metaStore.defaults()) }
    var autoRebalance by remember { mutableStateOf(metaStore.autoRebalance()) }
    var defaultGroupDraft by remember { mutableStateOf(defaults.group) }
    var defaultTagDraft by remember { mutableStateOf(defaults.tag) }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    var groupDraft by remember { mutableStateOf("") }
    var tagDraft by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(YDownloadPriority.NORMAL) }
    var selectedNetwork by remember { mutableStateOf(YDownloadNetworkRule.ANY) }
    var selectedGroupAction by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }

    val enhanced = remember(items, revision) { items.filter { it.backend == DownloadBackend.ENHANCED } }
    val groups = remember(enhanced, revision) {
        enhanced.map { metaStore.get(it.id).group }.filter(String::isNotBlank).distinct().sorted()
    }

    fun runRebalance() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { YDownloadScheduler.rebalance(context, store) }
            message = context.getString(
                R.string.ydownload_scheduler_result,
                result.started,
                result.paused,
                result.blocked,
            )
            revision++
        }
    }

    LaunchedEffect(autoRebalance) {
        while (autoRebalance) {
            delay(5_000L)
            val result = withContext(Dispatchers.IO) { YDownloadScheduler.rebalance(context, store) }
            if (result.started > 0 || result.paused > 0 || result.blocked > 0) {
                message = context.getString(
                    R.string.ydownload_scheduler_result,
                    result.started,
                    result.paused,
                    result.blocked,
                )
            }
            revision++
        }
    }

    YSection(
        title = stringResource(R.string.ydownload_scheduler_title),
        subtitle = stringResource(R.string.ydownload_scheduler_summary),
    ) {
        YSwitchItem(
            title = stringResource(R.string.ydownload_scheduler_auto),
            subtitle = stringResource(R.string.ydownload_scheduler_auto_summary),
            checked = autoRebalance,
            onCheckedChange = {
                autoRebalance = it
                metaStore.setAutoRebalance(it)
            },
        )
        OutlinedTextField(
            value = defaultGroupDraft,
            onValueChange = { defaultGroupDraft = it.take(64) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ydownload_scheduler_default_group)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = defaultTagDraft,
            onValueChange = { defaultTagDraft = it.take(96) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ydownload_scheduler_default_tag)) },
            singleLine = true,
        )
        YActionRow {
            YDownloadPriority.values().forEach { priority ->
                OutlinedButton(onClick = {
                    defaults = metaStore.updateDefaults { it.copy(priority = priority) }
                    revision++
                }) { Text(priorityLabel(priority)) }
            }
        }
        YActionRow {
            YDownloadNetworkRule.values().forEach { rule ->
                OutlinedButton(onClick = {
                    defaults = metaStore.updateDefaults { it.copy(networkRule = rule) }
                    revision++
                }) { Text(networkRuleLabel(rule)) }
            }
        }
        YActionRow {
            Button(onClick = {
                defaults = metaStore.updateDefaults {
                    it.copy(group = defaultGroupDraft, tag = defaultTagDraft)
                }
                revision++
            }) { Text(stringResource(R.string.ydownload_scheduler_save_defaults)) }
            OutlinedButton(onClick = { runRebalance() }) {
                Text(stringResource(R.string.ydownload_scheduler_rebalance))
            }
        }
        YStatusLine(
            stringResource(R.string.ydownload_scheduler_default_priority),
            priorityLabel(defaults.priority),
            YStatusTone.Neutral,
        )
        YStatusLine(
            stringResource(R.string.ydownload_scheduler_default_network),
            networkRuleLabel(defaults.networkRule),
            YStatusTone.Neutral,
        )

        if (groups.isNotEmpty()) {
            Text(stringResource(R.string.ydownload_scheduler_groups))
            groups.take(8).forEach { group ->
                val count = enhanced.count { metaStore.get(it.id).group == group }
                YActionRow {
                    OutlinedButton(onClick = {
                        selectedGroupAction = group
                        val countPaused = YDownloadScheduler.pauseGroup(context, store, group)
                        message = context.getString(R.string.ydownload_scheduler_group_paused, group, countPaused)
                    }) { Text(stringResource(R.string.ydownload_scheduler_pause_group, group)) }
                    OutlinedButton(onClick = {
                        selectedGroupAction = group
                        val result = YDownloadScheduler.resumeGroup(context, store, group)
                        message = context.getString(R.string.ydownload_scheduler_group_resumed, group, result.started)
                        revision++
                    }) { Text(stringResource(R.string.ydownload_scheduler_resume_group, group)) }
                }
                YStatusLine(
                    group,
                    count.toString(),
                    if (selectedGroupAction == group) YStatusTone.Good else YStatusTone.Neutral,
                )
            }
        }

        val editable = enhanced.filter {
            it.state !in setOf(DownloadState.COMPLETED, DownloadState.CANCELLED)
        }.take(10)
        if (editable.isNotEmpty()) {
            Text(stringResource(R.string.ydownload_scheduler_task_metadata))
            editable.forEach { task ->
                val meta = metaStore.get(task.id)
                OutlinedButton(onClick = {
                    selectedTaskId = task.id
                    groupDraft = meta.group
                    tagDraft = meta.tag
                    selectedPriority = meta.priority
                    selectedNetwork = meta.networkRule
                }) { Text(task.fileName) }
            }
        }

        selectedTaskId?.let { id ->
            val task = items.firstOrNull { it.id == id }
            if (task != null) {
                Text(stringResource(R.string.ydownload_scheduler_editing, task.fileName))
                OutlinedTextField(
                    value = groupDraft,
                    onValueChange = { groupDraft = it.take(64) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ydownload_scheduler_group)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it.take(96) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ydownload_scheduler_tag)) },
                    singleLine = true,
                )
                YActionRow {
                    YDownloadPriority.values().forEach { priority ->
                        OutlinedButton(onClick = { selectedPriority = priority }) {
                            Text(priorityLabel(priority))
                        }
                    }
                }
                YActionRow {
                    YDownloadNetworkRule.values().forEach { rule ->
                        OutlinedButton(onClick = { selectedNetwork = rule }) {
                            Text(networkRuleLabel(rule))
                        }
                    }
                }
                Button(onClick = {
                    val currentMeta = metaStore.get(id)
                    metaStore.put(
                        id,
                        YDownloadTaskMeta(
                            group = groupDraft,
                            tag = tagDraft,
                            priority = selectedPriority,
                            networkRule = selectedNetwork,
                            schedulerPaused = currentMeta.schedulerPaused,
                        ),
                    )
                    revision++
                    runRebalance()
                }) { Text(stringResource(R.string.ydownload_scheduler_apply_task)) }
            }
        }
        message?.let { Text(it) }
        Text(stringResource(R.string.ydownload_scheduler_note))
    }
}

@Composable
private fun priorityLabel(priority: YDownloadPriority): String = when (priority) {
    YDownloadPriority.HIGH -> stringResource(R.string.ydownload_scheduler_priority_high)
    YDownloadPriority.NORMAL -> stringResource(R.string.ydownload_scheduler_priority_normal)
    YDownloadPriority.LOW -> stringResource(R.string.ydownload_scheduler_priority_low)
}

@Composable
private fun networkRuleLabel(rule: YDownloadNetworkRule): String = when (rule) {
    YDownloadNetworkRule.ANY -> stringResource(R.string.ydownload_scheduler_network_any)
    YDownloadNetworkRule.UNMETERED -> stringResource(R.string.ydownload_scheduler_network_unmetered)
    YDownloadNetworkRule.WIFI_ONLY -> stringResource(R.string.ydownload_scheduler_network_wifi)
}
