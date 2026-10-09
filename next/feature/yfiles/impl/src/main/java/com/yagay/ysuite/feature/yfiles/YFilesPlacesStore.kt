package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.util.Base64
import com.yagay.ysuite.feature.yfiles.api.YFileRef

class YFilesPlacesStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun snapshot(): YFilesPlacesSnapshot =
        YFilesPlacesSnapshot(
            favorites = decodeSet(
                preferences.getStringSet(
                    KEY_FAVORITES,
                    emptySet(),
                ).orEmpty(),
            ).sortedBy {
                it.label.lowercase()
            },
            recent = decodeRecent(
                preferences.getString(
                    KEY_RECENT,
                    "",
                ).orEmpty(),
            ),
        )

    @Synchronized
    fun toggleFavorite(
        ref: YFileRef,
        label: String,
    ): YFilesPlacesSnapshot {
        val current = snapshot()
            .favorites
            .associateBy {
                key(it.ref)
            }
            .toMutableMap()
        val refKey = key(ref)
        if (refKey in current) {
            current.remove(refKey)
        } else {
            current[refKey] =
                YFileLocationRecord(
                    ref = ref,
                    label = label,
                )
        }

        preferences.edit()
            .putStringSet(
                KEY_FAVORITES,
                current.values
                    .map(::encode)
                    .toSet(),
            )
            .apply()
        return snapshot()
    }

    @Synchronized
    fun rememberRecent(
        ref: YFileRef,
        label: String,
    ): YFilesPlacesSnapshot {
        val refKey = key(ref)
        val next = buildList {
            add(
                YFileLocationRecord(
                    ref = ref,
                    label = label,
                ),
            )
            addAll(
                snapshot().recent
                    .filterNot {
                        key(it.ref) == refKey
                    },
            )
        }.take(MAX_RECENT)

        preferences.edit()
            .putString(
                KEY_RECENT,
                next.joinToString(RECENT_SEPARATOR) {
                    encode(it)
                },
            )
            .apply()
        return snapshot()
    }

    private fun decodeSet(
        values: Set<String>,
    ): List<YFileLocationRecord> =
        values.mapNotNull(::decode)

    private fun decodeRecent(
        value: String,
    ): List<YFileLocationRecord> =
        value.split(RECENT_SEPARATOR)
            .filter(String::isNotBlank)
            .mapNotNull(::decode)

    private fun encode(
        record: YFileLocationRecord,
    ): String =
        encodePart(record.ref.providerId) +
            FIELD_SEPARATOR +
            encodePart(record.ref.path) +
            FIELD_SEPARATOR +
            encodePart(record.label)

    private fun decode(
        encoded: String,
    ): YFileLocationRecord? =
        runCatching {
            val parts =
                encoded.split(FIELD_SEPARATOR)
            require(parts.size == 3)
            YFileLocationRecord(
                ref = YFileRef(
                    providerId =
                        decodePart(parts[0]),
                    path =
                        decodePart(parts[1]),
                ),
                label =
                    decodePart(parts[2]),
            )
        }.getOrNull()

    private fun key(
        ref: YFileRef,
    ): String =
        ref.providerId + "\u0000" + ref.path

    private fun encodePart(
        value: String,
    ): String =
        Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or
                Base64.URL_SAFE,
        )

    private fun decodePart(
        value: String,
    ): String =
        String(
            Base64.decode(
                value,
                Base64.NO_WRAP or
                    Base64.URL_SAFE,
            ),
            Charsets.UTF_8,
        )

    companion object {
        private const val PREFERENCES_NAME =
            "yfiles_places"
        private const val KEY_FAVORITES =
            "favorites"
        private const val KEY_RECENT = "recent"
        private const val FIELD_SEPARATOR = "."
        private const val RECENT_SEPARATOR = ","
        private const val MAX_RECENT = 24
    }
}
