package com.yagay.suite.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Host-owned, tokenized read-only file sharing.
 *
 * Features never receive a broad filesystem-backed FileProvider. They register one concrete file
 * through [SuiteShareBroker] and only the returned random content URI can be opened by a recipient
 * that was granted URI permission by Android.
 */
class SuiteShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val file = resolve(uri) ?: return null
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val columns = projection?.toList().orEmpty().ifEmpty {
            listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        }
        val supported = columns.filter {
            it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE
        }
        val cursor = MatrixCursor(supported.toTypedArray(), 1)
        val row = cursor.newRow()
        supported.forEach { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
            }
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("YSuite shared files are read-only")
        val file = resolve(uri) ?: throw FileNotFoundException("Share token is invalid or expired")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val app = context?.applicationContext ?: return 0
        val token = SuiteShareBroker.tokenFromUri(uri) ?: return 0
        return if (SuiteShareBroker.revoke(app, token)) 1 else 0
    }

    private fun resolve(uri: Uri): File? {
        val app = context?.applicationContext ?: return null
        return SuiteShareBroker.resolve(app, uri)
    }
}

internal object SuiteShareBroker {
    private const val PREFS_NAME = "ysuite.share.tokens"
    private const val PATH_PREFIX = "share"
    private const val TTL_MS = 6L * 60L * 60L * 1000L
    private val TOKEN_PATTERN = Regex("^[a-f0-9]{32}$")

    fun register(context: Context, featureId: String, source: File): Uri? {
        val app = context.applicationContext
        val canonical = runCatching { source.canonicalFile }.getOrNull() ?: return null
        if (!canonical.isFile || !canonical.canRead()) return null

        cleanupExpired(app)
        val token = UUID.randomUUID().toString().replace("-", "")
        val encodedPath = Base64.encodeToString(
            canonical.absolutePath.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
        val value = "${System.currentTimeMillis()}|$featureId|$encodedPath"
        prefs(app).edit().putString(token, value).apply()

        return Uri.Builder()
            .scheme("content")
            .authority("${app.packageName}.files")
            .appendPath(PATH_PREFIX)
            .appendPath(token)
            .build()
    }

    fun resolve(context: Context, uri: Uri): File? {
        val token = tokenFromUri(uri) ?: return null
        if (uri.authority != "${context.packageName}.files") return null
        val stored = prefs(context).getString(token, null) ?: return null
        val parts = stored.split('|', limit = 3)
        if (parts.size != 3) {
            revoke(context, token)
            return null
        }
        val createdAt = parts[0].toLongOrNull() ?: 0L
        if (createdAt <= 0L || System.currentTimeMillis() - createdAt > TTL_MS) {
            revoke(context, token)
            return null
        }
        val path = runCatching {
            String(Base64.decode(parts[2], Base64.NO_WRAP or Base64.URL_SAFE), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        return file.takeIf { it.isFile && it.canRead() }
    }

    fun tokenFromUri(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != PATH_PREFIX) return null
        return segments[1].takeIf { TOKEN_PATTERN.matches(it) }
    }

    fun revoke(context: Context, token: String): Boolean {
        if (!TOKEN_PATTERN.matches(token)) return false
        val store = prefs(context)
        if (!store.contains(token)) return false
        store.edit().remove(token).apply()
        return true
    }

    private fun cleanupExpired(context: Context) {
        val now = System.currentTimeMillis()
        val store = prefs(context)
        val editor = store.edit()
        var changed = false
        store.all.forEach { (token, raw) ->
            val createdAt = (raw as? String)?.substringBefore('|')?.toLongOrNull() ?: 0L
            if (!TOKEN_PATTERN.matches(token) || createdAt <= 0L || now - createdAt > TTL_MS) {
                editor.remove(token)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
