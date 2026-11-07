package com.yagay.YNFC

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException

/**
 * Public export only. HAL services do not read this folder and no UID state
 * is inferred from a successful MediaStore write. No broad storage permission.
 */
internal object NfcPublicDownloads {
    const val RELATIVE_PATH = "Download/YSuite/YNFC/"
    private val validFileName = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
    private const val MAX_BYTES = 1024 * 1024
    private val collection get() = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    fun write(context: Context, name: String, bytes: ByteArray, mime: String): String {
        require(validFileName.matches(name) && name != "." && name != "..") {
            "Invalid export file name"
        }
        require(bytes.size <= MAX_BYTES) { "Refusing to export file larger than 1 MiB" }
        val resolver = context.contentResolver
        val existing = findOwned(context, name)
        var uri: Uri? = existing
        var inserted = false
        try {
            if (uri == null) {
                uri = resolver.insert(collection, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }) ?: throw IOException("Cannot create Downloads/YSuite/YNFC/" + name)
                inserted = true
            }
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("Cannot open Downloads output: " + name)
            if (inserted) {
                val published = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                if (published < 1) throw IOException("Cannot publish Downloads export: " + name)
            }
            return RELATIVE_PATH + name
        } catch (error: Throwable) {
            // Never delete an existing user-visible snapshot after an IO error.
            if (inserted) uri?.let { runCatching { resolver.delete(it, null, null) } }
            throw error
        }
    }

    private fun findOwned(context: Context, name: String): Uri? {
        val resolver = context.contentResolver
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        resolver.query(
            collection, columns,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, RELATIVE_PATH), null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val ownerIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            while (cursor.moveToNext()) {
                if (context.packageName == cursor.getString(ownerIndex)) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(idIndex))
                }
            }
        }
        return null
    }
}
