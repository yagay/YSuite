package com.yagay.YNFC

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException
import java.security.MessageDigest

/**
 * Public export only. HAL services do not read this folder and no UID state
 * is inferred from a successful MediaStore write. No broad storage permission.
 */
internal object NfcPublicDownloads {
    const val RELATIVE_PATH = "Download/YSuite/YNFC/"
    private val validFileName = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val collection get() = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    fun write(
        context: Context,
        name: String,
        bytes: ByteArray,
        mime: String,
        relativePath: String = RELATIVE_PATH,
    ): String {
        require(validFileName.matches(name) && name != "." && name != "..") {
            "Invalid export file name"
        }
        require(bytes.size <= MAX_BYTES) { "Refusing to export file larger than 8 MiB" }
        // All files must remain inside our own public Downloads subtree.
        require(relativePath.startsWith(RELATIVE_PATH) &&
            relativePath.endsWith("/") &&
            relativePath.drop(RELATIVE_PATH.length).split("/")
                .all { it.isEmpty() || it.matches(Regex("[A-Za-z0-9._-]+")) && it != "." && it != ".." }
        ) { "Invalid YNFC download subdirectory" }
        val resolver = context.contentResolver
        val existing = findOwned(context, name, relativePath)
        var uri: Uri? = existing
        var inserted = false
        try {
            if (uri == null) {
                uri = resolver.insert(collection, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }) ?: throw IOException("Cannot create " + relativePath + name)
                inserted = true
            }
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("Cannot open Downloads output: " + name)
            // A successful stream close does not prove MediaStore preserved
            // the content. Verify exact bytes before publishing the manifest.
            val expected = MessageDigest.getInstance("SHA-256").digest(bytes)
            val actualDigest = MessageDigest.getInstance("SHA-256")
            var actualCount = 0L
            val input = resolver.openInputStream(uri)
                ?: throw IOException("Cannot reopen Downloads output for verification: " + name)
            input.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    actualDigest.update(buffer, 0, count)
                    actualCount += count
                    if (actualCount > MAX_BYTES) throw IOException("Downloads file grew unexpectedly")
                }
            }
            if (actualCount != bytes.size.toLong() ||
                !expected.contentEquals(actualDigest.digest())) {
                throw IOException("Downloads file failed SHA-256 readback verification: " + name)
            }
            if (inserted) {
                val published = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
                if (published < 1) throw IOException("Cannot publish Downloads export: " + name)
            }
            return relativePath + name
        } catch (error: Throwable) {
            // Never delete an existing user-visible snapshot after an IO error.
            if (inserted) uri?.let { runCatching { resolver.delete(it, null, null) } }
            throw error
        }
    }

    private fun findOwned(context: Context, name: String, relativePath: String): Uri? {
        val resolver = context.contentResolver
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        resolver.query(
            collection, columns,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relativePath), null
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
