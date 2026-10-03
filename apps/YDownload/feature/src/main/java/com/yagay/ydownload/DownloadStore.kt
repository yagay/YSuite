package com.yagay.ydownload

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class DownloadBackend { SYSTEM, ENHANCED }

data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val uri: String? = null,
    val done: Long = 0L,
    val total: Long = -1L,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
    val backend: DownloadBackend = DownloadBackend.SYSTEM,
    val systemId: Long? = null,
)

class DownloadStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ydownload", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items = _items.asStateFlow()

    @Synchronized fun add(url: String, fileName: String, backend: DownloadBackend = DownloadBackend.SYSTEM): DownloadItem {
        val item = DownloadItem(System.currentTimeMillis(), url, fileName, backend = backend)
        save(listOf(item) + _items.value)
        return item
    }

    @Synchronized fun get(id: Long): DownloadItem? = _items.value.firstOrNull { it.id == id }

    @Synchronized fun update(id: Long, block: (DownloadItem) -> DownloadItem): DownloadItem? {
        var updated: DownloadItem? = null
        save(_items.value.map { if (it.id == id) block(it).also { value -> updated = value } else it })
        return updated
    }

    @Synchronized fun remove(id: Long) = save(_items.value.filterNot { it.id == id })

    private fun save(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("url", item.url)
                put("fileName", item.fileName)
                put("uri", item.uri)
                put("done", item.done)
                put("total", item.total)
                put("state", item.state.name)
                put("error", item.error)
                put("backend", item.backend.name)
                put("systemId", item.systemId)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
        _items.value = items
    }

    private fun load(): List<DownloadItem> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val backend = runCatching {
                    DownloadBackend.valueOf(o.optString("backend", DownloadBackend.ENHANCED.name))
                }.getOrDefault(DownloadBackend.ENHANCED)
                add(
                    DownloadItem(
                        id = o.getLong("id"),
                        url = o.getString("url"),
                        fileName = o.getString("fileName"),
                        uri = o.optString("uri").takeIf { it.isNotBlank() && it != "null" },
                        done = o.optLong("done", 0L),
                        total = o.optLong("total", -1L),
                        state = runCatching { DownloadState.valueOf(o.optString("state")) }
                            .getOrDefault(DownloadState.PAUSED),
                        error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                        backend = backend,
                        systemId = if (o.has("systemId") && !o.isNull("systemId")) o.optLong("systemId") else null,
                    ),
                )
            }
        }.map {
            if (it.backend == DownloadBackend.ENHANCED && it.state == DownloadState.RUNNING) {
                it.copy(state = DownloadState.PAUSED)
            } else {
                it
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: DownloadStore? = null
        fun get(context: Context): DownloadStore = instance ?: synchronized(this) {
            instance ?: DownloadStore(context).also { instance = it }
        }
    }
}
