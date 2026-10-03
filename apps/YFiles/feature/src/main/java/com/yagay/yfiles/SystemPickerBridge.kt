package com.yagay.yfiles

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract

/** Helpers that enhance the stock SAF/DocumentsUI flow without replacing it. */
object SystemPickerBridge {
    const val REQUEST_OPEN = 0x5941
    const val REQUEST_OPEN_MULTIPLE = 0x5942
    const val REQUEST_TREE = 0x5943
    const val REQUEST_DEFAULT_TREE = 0x5944
    const val REQUEST_CREATE = 0x5945

    fun openDocument(context: Context, multiple: Boolean = false): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            .also { applyPatch(context, it, forceMultiple = multiple) }

    fun openTree(context: Context): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            .also { applyPatch(context, it) }

    fun createDocument(context: Context, suggestedName: String = "new-file.txt"): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, suggestedName)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            .also { applyPatch(context, it) }

    fun rememberReturnedUri(context: Context, uri: Uri?, flags: Int) {
        if (uri == null) return
        val takeFlags = flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        runCatching {
            if (takeFlags != 0) context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
    }

    fun applyPatch(context: Context, intent: Intent, forceMultiple: Boolean = false) {
        val settings = YFilesPatchSettings.load(context)
        if (!settings.enabled) return
        settings.initialUri?.let { raw ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { Uri.parse(raw) }.getOrNull()?.let { uri ->
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }
        }
        if (settings.localOnly) intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        if (forceMultiple || settings.allowMultiple) {
            val action = intent.action
            if (action == Intent.ACTION_OPEN_DOCUMENT || action == Intent.ACTION_GET_CONTENT) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
    }
}
