package com.yagay.ysuite.feature.yfiles

import android.content.Context
import com.yagay.ysuite.feature.yfiles.api.YFilesPlacesRepository
import com.yagay.ysuite.feature.yfiles.api.YFilesPlacesSnapshot

class LocalYFilesPlacesRepository(
    context: Context,
) : YFilesPlacesRepository {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    override fun snapshot(): YFilesPlacesSnapshot =
        YFilesPlacesSnapshot(
            favoritePaths = preferences
                .getStringSet(KEY_FAVORITES, emptySet())
                .orEmpty()
                .toSet(),
            recentPaths = readRecent(),
        )

    @Synchronized
    override fun toggleFavorite(
        path: String,
    ): YFilesPlacesSnapshot {
        val favorites = preferences
            .getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .toMutableSet()

        if (!favorites.add(path)) {
            favorites.remove(path)
        }

        preferences.edit()
            .putStringSet(KEY_FAVORITES, favorites)
            .apply()

        return snapshot()
    }

    @Synchronized
    override fun rememberRecent(
        path: String,
    ): YFilesPlacesSnapshot {
        val recent = readRecent()
            .filterNot { it == path }
            .toMutableList()
        recent.add(0, path)

        preferences.edit()
            .putString(
                KEY_RECENT,
                recent.take(MAX_RECENT)
                    .joinToString(SEPARATOR),
            )
            .apply()

        return snapshot()
    }

    private fun readRecent(): List<String> =
        preferences.getString(KEY_RECENT, null)
            ?.split(SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()

    companion object {
        private const val PREFERENCES_NAME = "yfiles_places"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent"
        private const val SEPARATOR = "\n"
        private const val MAX_RECENT = 16
    }
}
