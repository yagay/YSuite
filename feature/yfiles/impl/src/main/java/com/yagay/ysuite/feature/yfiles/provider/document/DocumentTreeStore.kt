package com.yagay.ysuite.feature.yfiles.provider.document

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri

class DocumentTreeStore(
    private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    fun trees(): List<Uri> =
        preferences
            .getStringSet(KEY_TREES, emptySet())
            .orEmpty()
            .map(Uri::parse)
            .sortedBy(Uri::toString)

    fun add(
        uri: Uri,
        flags: Int,
    ): Boolean {
        val permissionFlags = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        runCatching {
            context.contentResolver
                .takePersistableUriPermission(
                    uri,
                    permissionFlags,
                )
        }.getOrElse {
            return false
        }

        val next = preferences
            .getStringSet(KEY_TREES, emptySet())
            .orEmpty()
            .toMutableSet()
        next += uri.toString()
        preferences.edit()
            .putStringSet(KEY_TREES, next)
            .apply()
        return true
    }

    fun remove(uri: Uri) {
        val next = preferences
            .getStringSet(KEY_TREES, emptySet())
            .orEmpty()
            .toMutableSet()
        next -= uri.toString()
        preferences.edit()
            .putStringSet(KEY_TREES, next)
            .apply()

        runCatching {
            context.contentResolver
                .releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
        }
    }

    companion object {
        private const val PREFERENCES_NAME =
            "yfiles_document_trees"
        private const val KEY_TREES = "trees"
    }
}
