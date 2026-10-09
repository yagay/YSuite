package com.yagay.ysuite.feature.yfiles

import android.content.Context
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class YFilesViewMode {
    List,
    Grid,
}

data class YFilesBrowserTabRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val ref: YFileRef,
)

data class YFilesDirectoryPreference(
    val viewMode: YFilesViewMode =
        YFilesViewMode.List,
    val sort: YFileSort = YFileSort.Name,
    val descending: Boolean = false,
    val showHidden: Boolean = false,
)

data class YFilesSavedSearch(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val root: YFileRef,
    val query: String,
    val recursive: Boolean = true,
    val showHidden: Boolean = false,
)

class YFilesWorkspaceStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                "yfiles_workspace",
                Context.MODE_PRIVATE,
            )

    fun tabs(
        workspaceId: String,
    ): List<YFilesBrowserTabRecord> =
        runCatching {
            val array =
                JSONArray(
                    prefs.getString(
                        keyTabs(workspaceId),
                        "[]",
                    ),
                )
            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(index)
                    add(
                        YFilesBrowserTabRecord(
                            id =
                                item.getString(
                                    "id",
                                ),
                            title =
                                item.getString(
                                    "title",
                                ),
                            ref =
                                YFileRef(
                                    item.getString(
                                        "provider",
                                    ),
                                    item.getString(
                                        "path",
                                    ),
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun activeTabId(
        workspaceId: String,
    ): String? =
        prefs.getString(
            keyActiveTab(workspaceId),
            null,
        )

    fun saveTabs(
        workspaceId: String,
        tabs: List<YFilesBrowserTabRecord>,
        activeId: String?,
    ) {
        val array = JSONArray()
        tabs.forEach { tab ->
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("title", tab.title)
                    .put(
                        "provider",
                        tab.ref.providerId,
                    )
                    .put("path", tab.ref.path),
            )
        }
        prefs.edit()
            .putString(
                keyTabs(workspaceId),
                array.toString(),
            )
            .apply {
                if (activeId == null) {
                    remove(
                        keyActiveTab(
                            workspaceId,
                        ),
                    )
                } else {
                    putString(
                        keyActiveTab(
                            workspaceId,
                        ),
                        activeId,
                    )
                }
            }
            .apply()
    }

    fun directoryPreference(
        ref: YFileRef,
    ): YFilesDirectoryPreference {
        val raw =
            prefs.getString(
                KEY_DIRECTORY_PREFS,
                "{}",
            )
        return runCatching {
            val all = JSONObject(raw)
            val item =
                all.optJSONObject(
                    refKey(ref),
                )
            if (item == null) {
                YFilesDirectoryPreference()
            } else {
                YFilesDirectoryPreference(
                    viewMode =
                        runCatching {
                            YFilesViewMode.valueOf(
                                item.optString(
                                    "viewMode",
                                    YFilesViewMode
                                        .List.name,
                                ),
                            )
                        }.getOrDefault(
                            YFilesViewMode.List,
                        ),
                    sort =
                        runCatching {
                            YFileSort.valueOf(
                                item.optString(
                                    "sort",
                                    YFileSort.Name.name,
                                ),
                            )
                        }.getOrDefault(
                            YFileSort.Name,
                        ),
                    descending =
                        item.optBoolean(
                            "descending",
                            false,
                        ),
                    showHidden =
                        item.optBoolean(
                            "showHidden",
                            false,
                        ),
                )
            }
        }.getOrDefault(
            YFilesDirectoryPreference(),
        )
    }

    fun saveDirectoryPreference(
        ref: YFileRef,
        preference: YFilesDirectoryPreference,
    ) {
        val all =
            runCatching {
                JSONObject(
                    prefs.getString(
                        KEY_DIRECTORY_PREFS,
                        "{}",
                    ),
                )
            }.getOrDefault(JSONObject())
        all.put(
            refKey(ref),
            JSONObject()
                .put(
                    "viewMode",
                    preference.viewMode.name,
                )
                .put(
                    "sort",
                    preference.sort.name,
                )
                .put(
                    "descending",
                    preference.descending,
                )
                .put(
                    "showHidden",
                    preference.showHidden,
                ),
        )
        prefs.edit()
            .putString(
                KEY_DIRECTORY_PREFS,
                all.toString(),
            )
            .apply()
    }

    fun dualPaneEnabled(): Boolean =
        prefs.getBoolean(
            KEY_DUAL_PANE,
            false,
        )

    fun setDualPaneEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_DUAL_PANE,
                enabled,
            )
            .apply()
    }

    fun tags(ref: YFileRef): Set<String> {
        val all =
            runCatching {
                JSONObject(
                    prefs.getString(
                        KEY_TAGS,
                        "{}",
                    ),
                )
            }.getOrDefault(JSONObject())
        val array =
            all.optJSONArray(
                refKey(ref),
            ) ?: return emptySet()
        return buildSet {
            for (
                index in 0 until
                    array.length()
            ) {
                array.optString(index)
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.let(::add)
            }
        }
    }

    fun setTags(
        ref: YFileRef,
        tags: Set<String>,
    ) {
        val all =
            runCatching {
                JSONObject(
                    prefs.getString(
                        KEY_TAGS,
                        "{}",
                    ),
                )
            }.getOrDefault(JSONObject())
        if (tags.isEmpty()) {
            all.remove(refKey(ref))
        } else {
            val array = JSONArray()
            tags.map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .forEach(array::put)
            all.put(
                refKey(ref),
                array,
            )
        }
        prefs.edit()
            .putString(
                KEY_TAGS,
                all.toString(),
            )
            .apply()
    }

    fun allTaggedRefs():
        Map<YFileRef, Set<String>> {
        val all =
            runCatching {
                JSONObject(
                    prefs.getString(
                        KEY_TAGS,
                        "{}",
                    ),
                )
            }.getOrDefault(JSONObject())
        return buildMap {
            val keys = all.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val ref =
                    parseRefKey(key)
                        ?: continue
                val array =
                    all.optJSONArray(key)
                        ?: continue
                val tags = buildSet {
                    for (
                        index in 0 until
                            array.length()
                    ) {
                        array.optString(index)
                            .trim()
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let(::add)
                    }
                }
                if (tags.isNotEmpty()) {
                    put(ref, tags)
                }
            }
        }
    }

    fun savedSearches():
        List<YFilesSavedSearch> =
        runCatching {
            val array =
                JSONArray(
                    prefs.getString(
                        KEY_SAVED_SEARCHES,
                        "[]",
                    ),
                )
            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(index)
                    add(
                        YFilesSavedSearch(
                            id =
                                item.getString(
                                    "id",
                                ),
                            name =
                                item.getString(
                                    "name",
                                ),
                            root =
                                YFileRef(
                                    item.getString(
                                        "provider",
                                    ),
                                    item.getString(
                                        "path",
                                    ),
                                ),
                            query =
                                item.getString(
                                    "query",
                                ),
                            recursive =
                                item.optBoolean(
                                    "recursive",
                                    true,
                                ),
                            showHidden =
                                item.optBoolean(
                                    "showHidden",
                                    false,
                                ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun saveSearch(
        search: YFilesSavedSearch,
    ) {
        persistSearches(
            savedSearches()
                .filterNot {
                    it.id == search.id
                } + search,
        )
    }

    fun deleteSearch(id: String) {
        persistSearches(
            savedSearches()
                .filterNot { it.id == id },
        )
    }

    private fun persistSearches(
        searches: List<YFilesSavedSearch>,
    ) {
        val array = JSONArray()
        searches.sortedBy {
            it.name.lowercase()
        }.forEach { search ->
            array.put(
                JSONObject()
                    .put("id", search.id)
                    .put("name", search.name)
                    .put(
                        "provider",
                        search.root.providerId,
                    )
                    .put(
                        "path",
                        search.root.path,
                    )
                    .put(
                        "query",
                        search.query,
                    )
                    .put(
                        "recursive",
                        search.recursive,
                    )
                    .put(
                        "showHidden",
                        search.showHidden,
                    ),
            )
        }
        prefs.edit()
            .putString(
                KEY_SAVED_SEARCHES,
                array.toString(),
            )
            .apply()
    }

    private fun refKey(
        ref: YFileRef,
    ): String =
        ref.providerId +
            REF_SEPARATOR +
            ref.path

    private fun parseRefKey(
        value: String,
    ): YFileRef? {
        val index =
            value.indexOf(
                REF_SEPARATOR,
            )
        if (index <= 0) return null
        return YFileRef(
            providerId =
                value.substring(0, index),
            path =
                value.substring(
                    index +
                        REF_SEPARATOR.length,
                ),
        )
    }

    private fun keyTabs(
        workspaceId: String,
    ) = "tabs_$workspaceId"

    private fun keyActiveTab(
        workspaceId: String,
    ) = "active_tab_$workspaceId"

    companion object {
        private const val KEY_DIRECTORY_PREFS =
            "directory_preferences"
        private const val KEY_DUAL_PANE =
            "dual_pane"
        private const val KEY_TAGS = "tags"
        private const val KEY_SAVED_SEARCHES =
            "saved_searches"
        private const val REF_SEPARATOR =
            "\u0001"
    }
}
