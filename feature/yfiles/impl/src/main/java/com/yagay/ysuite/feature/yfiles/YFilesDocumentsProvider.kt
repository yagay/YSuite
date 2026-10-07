package com.yagay.ysuite.feature.yfiles

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class YFilesDocumentsProvider :
    DocumentsProvider() {
    private val storageRoot: File
        get() =
            Environment
                .getExternalStorageDirectory()
                .canonicalFile

    override fun onCreate(): Boolean = true

    override fun queryRoots(
        projection: Array<out String>?,
    ): Cursor {
        val columns =
            projection ?: ROOT_COLUMNS
        return MatrixCursor(columns)
            .apply {
                val root = storageRoot
                val row = newRow()
                columns.forEach { column ->
                    when (column) {
                        DocumentsContract.Root
                            .COLUMN_ROOT_ID ->
                            row.add(ROOT_ID)
                        DocumentsContract.Root
                            .COLUMN_DOCUMENT_ID ->
                            row.add(documentId(root))
                        DocumentsContract.Root
                            .COLUMN_TITLE ->
                            row.add("YFiles")
                        DocumentsContract.Root
                            .COLUMN_SUMMARY ->
                            row.add(
                                "Shared storage",
                            )
                        DocumentsContract.Root
                            .COLUMN_FLAGS ->
                            row.add(
                                DocumentsContract.Root
                                    .FLAG_SUPPORTS_CREATE or
                                    DocumentsContract.Root
                                        .FLAG_SUPPORTS_SEARCH or
                                    DocumentsContract.Root
                                        .FLAG_SUPPORTS_RECENTS or
                                    DocumentsContract.Root
                                        .FLAG_LOCAL_ONLY,
                            )
                        DocumentsContract.Root
                            .COLUMN_MIME_TYPES ->
                            row.add("*/*")
                        DocumentsContract.Root
                            .COLUMN_AVAILABLE_BYTES ->
                            row.add(
                                root.usableSpace,
                            )
                        DocumentsContract.Root
                            .COLUMN_ICON ->
                            row.add(
                                android.R.drawable
                                    .ic_menu_manage,
                            )
                        else -> row.add(null)
                    }
                }
            }
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        val columns =
            projection ?: DOCUMENT_COLUMNS
        val cursor = MatrixCursor(columns)
        includeFile(
            cursor,
            fileForId(documentId),
            columns,
        )
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns =
            projection ?: DOCUMENT_COLUMNS
        val cursor = MatrixCursor(columns)
        val parent =
            fileForId(parentDocumentId)
        requireDirectory(parent)
        parent.listFiles()
            .orEmpty()
            .sortedWith(
                compareByDescending<File> {
                    it.isDirectory
                }.thenBy {
                    it.name.lowercase()
                },
            )
            .forEach {
                includeFile(
                    cursor,
                    it,
                    columns,
                )
            }
        return cursor
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?,
    ): Cursor {
        val columns =
            projection ?: DOCUMENT_COLUMNS
        val cursor = MatrixCursor(columns)
        if (rootId != ROOT_ID) {
            return cursor
        }
        val needle = query.trim()
        var count = 0
        storageRoot.walkTopDown()
            .onEnter {
                count < MAX_SEARCH_SCAN
            }
            .forEach { file ->
                if (count >= MAX_SEARCH_SCAN) {
                    return@forEach
                }
                count += 1
                if (
                    file != storageRoot &&
                    file.name.contains(
                        needle,
                        ignoreCase = true,
                    )
                ) {
                    includeFile(
                        cursor,
                        file,
                        columns,
                    )
                    if (
                        cursor.count >=
                        MAX_SEARCH_RESULTS
                    ) {
                        return@forEach
                    }
                }
            }
        return cursor
    }

    override fun queryRecentDocuments(
        rootId: String,
        projection: Array<out String>?,
    ): Cursor {
        val columns =
            projection ?: DOCUMENT_COLUMNS
        val cursor = MatrixCursor(columns)
        if (rootId != ROOT_ID) {
            return cursor
        }
        storageRoot.walkTopDown()
            .filter { it.isFile }
            .take(MAX_RECENT_SCAN)
            .sortedByDescending {
                it.lastModified()
            }
            .take(MAX_RECENT_RESULTS)
            .forEach {
                includeFile(
                    cursor,
                    it,
                    columns,
                )
            }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileForId(documentId)
        if (!file.isFile) {
            throw FileNotFoundException(
                "Not a file",
            )
        }
        return ParcelFileDescriptor.open(
            file,
            parseMode(mode),
        )
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent =
            fileForId(parentDocumentId)
        requireDirectory(parent)
        val safeName =
            sanitizeName(displayName)
        val target =
            uniqueChild(
                parent,
                safeName,
            )
        val created =
            if (
                mimeType ==
                DocumentsContract.Document
                    .MIME_TYPE_DIR
            ) {
                target.mkdir()
            } else {
                target.createNewFile()
            }
        if (!created) {
            throw FileNotFoundException(
                "Unable to create document",
            )
        }
        return documentId(target)
    }

    override fun deleteDocument(
        documentId: String,
    ) {
        val file = fileForId(documentId)
        if (file == storageRoot) {
            throw FileNotFoundException(
                "Root cannot be deleted",
            )
        }
        val deleted =
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        if (!deleted) {
            throw FileNotFoundException(
                "Unable to delete document",
            )
        }
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val file = fileForId(documentId)
        if (file == storageRoot) {
            throw FileNotFoundException(
                "Root cannot be renamed",
            )
        }
        val parent =
            file.parentFile
                ?: throw FileNotFoundException(
                    "Parent is unavailable",
                )
        val target =
            uniqueChild(
                parent,
                sanitizeName(displayName),
                allowSame = file,
            )
        if (!file.renameTo(target)) {
            throw FileNotFoundException(
                "Unable to rename document",
            )
        }
        return documentId(target)
    }


    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val source =
            fileForId(sourceDocumentId)
        if (source == storageRoot) {
            throw FileNotFoundException(
                "Root cannot be copied",
            )
        }
        val targetParent =
            fileForId(
                targetParentDocumentId,
            )
        requireDirectory(targetParent)
        val target =
            uniqueChild(
                targetParent,
                source.name,
            )

        val copied =
            if (source.isDirectory) {
                runCatching {
                    source.copyRecursively(
                        target = target,
                        overwrite = false,
                    )
                }.getOrDefault(false)
            } else {
                runCatching {
                    source.copyTo(
                        target = target,
                        overwrite = false,
                    )
                    true
                }.getOrDefault(false)
            }
        if (!copied) {
            throw FileNotFoundException(
                "Unable to copy document",
            )
        }
        return documentId(target)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val source =
            fileForId(sourceDocumentId)
        if (source == storageRoot) {
            throw FileNotFoundException(
                "Root cannot be moved",
            )
        }
        val sourceParent =
            fileForId(
                sourceParentDocumentId,
            )
        val targetParent =
            fileForId(
                targetParentDocumentId,
            )
        requireDirectory(sourceParent)
        requireDirectory(targetParent)

        if (
            source.parentFile
                ?.canonicalFile !=
            sourceParent.canonicalFile
        ) {
            throw FileNotFoundException(
                "Source parent mismatch",
            )
        }

        val target =
            uniqueChild(
                targetParent,
                source.name,
            )
        if (source.renameTo(target)) {
            return documentId(target)
        }

        val copied =
            if (source.isDirectory) {
                runCatching {
                    source.copyRecursively(
                        target = target,
                        overwrite = false,
                    )
                }.getOrDefault(false)
            } else {
                runCatching {
                    source.copyTo(
                        target = target,
                        overwrite = false,
                    )
                    true
                }.getOrDefault(false)
            }
        if (!copied) {
            throw FileNotFoundException(
                "Unable to move document",
            )
        }

        val deleted =
            if (source.isDirectory) {
                source.deleteRecursively()
            } else {
                source.delete()
            }
        if (!deleted) {
            if (target.isDirectory) {
                target.deleteRecursively()
            } else {
                target.delete()
            }
            throw FileNotFoundException(
                "Unable to remove source after move",
            )
        }
        return documentId(target)
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean {
        val parent =
            runCatching {
                fileForId(parentDocumentId)
            }.getOrNull() ?: return false
        val child =
            runCatching {
                fileForId(documentId)
            }.getOrNull() ?: return false
        return child != parent &&
            child.path.startsWith(
                parent.path +
                    File.separator,
            )
    }

    override fun getDocumentType(
        documentId: String,
    ): String =
        mimeType(fileForId(documentId))

    private fun includeFile(
        cursor: MatrixCursor,
        file: File,
        columns: Array<out String>,
    ) {
        if (!isContained(file)) {
            return
        }
        val row = cursor.newRow()
        columns.forEach { column ->
            when (column) {
                DocumentsContract.Document
                    .COLUMN_DOCUMENT_ID ->
                    row.add(documentId(file))
                DocumentsContract.Document
                    .COLUMN_DISPLAY_NAME ->
                    row.add(
                        if (file == storageRoot) {
                            "Internal storage"
                        } else {
                            file.name
                        },
                    )
                DocumentsContract.Document
                    .COLUMN_MIME_TYPE ->
                    row.add(mimeType(file))
                DocumentsContract.Document
                    .COLUMN_SIZE ->
                    row.add(
                        if (file.isFile) {
                            file.length()
                        } else {
                            null
                        },
                    )
                DocumentsContract.Document
                    .COLUMN_LAST_MODIFIED ->
                    row.add(file.lastModified())
                DocumentsContract.Document
                    .COLUMN_FLAGS ->
                    row.add(flags(file))
                DocumentsContract.Document
                    .COLUMN_ICON ->
                    row.add(null)
                else -> row.add(null)
            }
        }
    }

    private fun flags(file: File): Int {
        var flags =
            DocumentsContract.Document
                .FLAG_SUPPORTS_DELETE or
                DocumentsContract.Document
                    .FLAG_SUPPORTS_RENAME or
                DocumentsContract.Document
                    .FLAG_SUPPORTS_MOVE or
                DocumentsContract.Document
                    .FLAG_SUPPORTS_COPY
        if (file.isDirectory) {
            flags =
                flags or
                    DocumentsContract.Document
                        .FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags =
                flags or
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_WRITE
        }
        if (file == storageRoot) {
            flags =
                flags and
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_DELETE
                        .inv() and
                    DocumentsContract.Document
                        .FLAG_SUPPORTS_RENAME
                        .inv()
        }
        return flags
    }

    private fun fileForId(
        documentId: String,
    ): File {
        require(
            documentId.startsWith(
                ID_PREFIX,
            ),
        ) {
            "Invalid document id"
        }
        val relative =
            documentId.removePrefix(
                ID_PREFIX,
            )
        require(
            !relative.split('/')
                .any {
                    it == ".." ||
                        it == "."
                },
        ) {
            "Unsafe document id"
        }
        val file =
            if (relative.isBlank()) {
                storageRoot
            } else {
                File(
                    storageRoot,
                    relative,
                ).canonicalFile
            }
        require(isContained(file)) {
            "Document escapes storage root"
        }
        if (!file.exists()) {
            throw FileNotFoundException(
                "Document does not exist",
            )
        }
        return file
    }

    private fun documentId(
        file: File,
    ): String {
        val canonical =
            file.canonicalFile
        require(isContained(canonical))
        val relative =
            if (canonical == storageRoot) {
                ""
            } else {
                canonical.relativeTo(
                    storageRoot,
                ).invariantSeparatorsPath
            }
        return ID_PREFIX + relative
    }

    private fun isContained(
        file: File,
    ): Boolean {
        val root = storageRoot.path
        val path =
            runCatching {
                file.canonicalFile.path
            }.getOrDefault(file.absolutePath)
        return path == root ||
            path.startsWith(
                root + File.separator,
            )
    }

    private fun mimeType(
        file: File,
    ): String =
        if (file.isDirectory) {
            DocumentsContract.Document
                .MIME_TYPE_DIR
        } else {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(
                    file.extension.lowercase(),
                )
                ?: "application/octet-stream"
        }

    private fun parseMode(
        mode: String,
    ): Int {
        var result =
            ParcelFileDescriptor
                .MODE_READ_ONLY
        if ("w" in mode) {
            result =
                ParcelFileDescriptor
                    .MODE_READ_WRITE or
                    ParcelFileDescriptor
                        .MODE_CREATE
        }
        if ("t" in mode) {
            result =
                result or
                    ParcelFileDescriptor
                        .MODE_TRUNCATE
        }
        if ("a" in mode) {
            result =
                result or
                    ParcelFileDescriptor
                        .MODE_APPEND
        }
        return result
    }

    private fun requireDirectory(
        file: File,
    ) {
        if (!file.isDirectory) {
            throw FileNotFoundException(
                "Not a directory",
            )
        }
    }

    private fun sanitizeName(
        value: String,
    ): String =
        value.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .also {
                require(it.isNotBlank()) {
                    "Name is empty"
                }
            }

    private fun uniqueChild(
        parent: File,
        requested: String,
        allowSame: File? = null,
    ): File {
        var candidate =
            File(parent, requested)
        if (
            !candidate.exists() ||
            candidate == allowSame
        ) {
            return candidate
        }
        val dot = requested.lastIndexOf('.')
        val base =
            if (dot > 0) {
                requested.substring(0, dot)
            } else {
                requested
            }
        val extension =
            if (dot > 0) {
                requested.substring(dot)
            } else {
                ""
            }
        var index = 1
        do {
            candidate =
                File(
                    parent,
                    "$base ($index)$extension",
                )
            index += 1
        } while (
            candidate.exists() &&
            candidate != allowSame
        )
        return candidate
    }

    companion object {
        private const val ROOT_ID =
            "yfiles-primary"
        private const val ID_PREFIX =
            "primary:"
        private const val MAX_SEARCH_SCAN =
            100_000
        private const val MAX_SEARCH_RESULTS =
            10_000
        private const val MAX_RECENT_SCAN =
            20_000
        private const val MAX_RECENT_RESULTS =
            64

        private val ROOT_COLUMNS =
            arrayOf(
                DocumentsContract.Root
                    .COLUMN_ROOT_ID,
                DocumentsContract.Root
                    .COLUMN_DOCUMENT_ID,
                DocumentsContract.Root
                    .COLUMN_TITLE,
                DocumentsContract.Root
                    .COLUMN_SUMMARY,
                DocumentsContract.Root
                    .COLUMN_FLAGS,
                DocumentsContract.Root
                    .COLUMN_MIME_TYPES,
                DocumentsContract.Root
                    .COLUMN_AVAILABLE_BYTES,
                DocumentsContract.Root
                    .COLUMN_ICON,
            )

        private val DOCUMENT_COLUMNS =
            arrayOf(
                DocumentsContract.Document
                    .COLUMN_DOCUMENT_ID,
                DocumentsContract.Document
                    .COLUMN_DISPLAY_NAME,
                DocumentsContract.Document
                    .COLUMN_MIME_TYPE,
                DocumentsContract.Document
                    .COLUMN_SIZE,
                DocumentsContract.Document
                    .COLUMN_LAST_MODIFIED,
                DocumentsContract.Document
                    .COLUMN_FLAGS,
            )
    }
}
