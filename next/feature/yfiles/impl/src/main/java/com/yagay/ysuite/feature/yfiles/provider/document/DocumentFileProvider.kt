package com.yagay.ysuite.feature.yfiles.provider.document

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileType
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentFileProvider(
    private val resolver: ContentResolver,
    private val store: DocumentTreeStore,
) : YFileProvider {
    private val parentByPath =
        ConcurrentHashMap<String, String>()

    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Document,
            capabilities = setOf(
                YFileCapability.Browse,
                YFileCapability.Search,
                YFileCapability.Read,
                YFileCapability.Write,
                YFileCapability.Create,
                YFileCapability.Rename,
                YFileCapability.Delete,
                YFileCapability.Copy,
                YFileCapability.Move,
            ),
        )

    override fun root(): YFileRef =
        YFileRef(
            providerId = PROVIDER_ID,
            path = VIRTUAL_ROOT,
        )

    override fun parent(
        ref: YFileRef,
    ): YFileRef? {
        if (
            ref.providerId != PROVIDER_ID ||
            ref.path == VIRTUAL_ROOT
        ) {
            return null
        }
        return parentByPath[ref.path]?.let {
            YFileRef(
                providerId = PROVIDER_ID,
                path = it,
            )
        }
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        ioOutcome("document_list_failed") {
            if (directory.path == VIRTUAL_ROOT) {
                return@ioOutcome sort(
                    virtualRoots(),
                    query,
                )
            }

            val needle =
                query.text.trim().lowercase()
            val entries =
                if (
                    query.recursive &&
                    needle.isNotEmpty()
                ) {
                    recursiveSearch(
                        directory = directory,
                        needle = needle,
                        showHidden =
                            query.showHidden,
                        maxResults =
                            query.maxResults,
                    )
                } else {
                    listDirect(directory)
                        .asSequence()
                        .filter {
                            query.showHidden ||
                                !it.hidden
                        }
                        .filter {
                            needle.isEmpty() ||
                                it.name.lowercase()
                                    .contains(needle)
                        }
                        .take(query.maxResults)
                        .toList()
                }
            sort(entries, query)
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        ioOutcome("document_stat_failed") {
            if (ref.path == VIRTUAL_ROOT) {
                return@ioOutcome YFileNode(
                    ref = root(),
                    name = PROVIDER_ID,
                    type = YFileType.Directory,
                    writable = true,
                )
            }
            queryNode(uri(ref))
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        create(
            parent = parent,
            name = name,
            mimeType =
                DocumentsContract.Document.MIME_TYPE_DIR,
            code =
                "document_create_directory_failed",
        )

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        create(
            parent = parent,
            name = name,
            mimeType =
                URLConnection
                    .guessContentTypeFromName(name)
                    ?: DEFAULT_MIME_TYPE,
            code = "document_create_file_failed",
        )

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        ioOutcome("document_rename_failed") {
            val renamed =
                DocumentsContract.renameDocument(
                    resolver,
                    uri(ref),
                    sanitizeName(newName),
                ) ?: error(
                    "Provider did not return renamed document",
                )
            val parentPath = parentByPath.remove(
                ref.path,
            )
            if (parentPath != null) {
                parentByPath[renamed.toString()] =
                    parentPath
            }
            queryNode(renamed)
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        ioOutcome("document_delete_failed") {
            require(ref.path != VIRTUAL_ROOT) {
                "Virtual root cannot be deleted"
            }
            require(
                DocumentsContract.deleteDocument(
                    resolver,
                    uri(ref),
                ),
            ) {
                "Document provider rejected delete"
            }
            parentByPath.remove(ref.path)
            Unit
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        ioOutcome("document_copy_failed") {
            val copied =
                DocumentsContract.copyDocument(
                    resolver,
                    uri(source),
                    uri(destinationDirectory),
                ) ?: error(
                    "Document provider rejected copy",
                )

            val finalUri = if (
                queryNode(copied).name != targetName
            ) {
                DocumentsContract.renameDocument(
                    resolver,
                    copied,
                    sanitizeName(targetName),
                ) ?: copied
            } else {
                copied
            }
            parentByPath[finalUri.toString()] =
                destinationDirectory.path
            queryNode(finalUri)
        }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        ioOutcome("document_move_failed") {
            val parentPath =
                parentByPath[source.path]
                    ?: error(
                        "Source parent is unavailable",
                    )
            require(parentPath != VIRTUAL_ROOT) {
                "Tree root cannot be moved"
            }

            val moved =
                DocumentsContract.moveDocument(
                    resolver,
                    uri(source),
                    Uri.parse(parentPath),
                    uri(destinationDirectory),
                ) ?: error(
                    "Document provider rejected move",
                )

            val finalUri = if (
                queryNode(moved).name != targetName
            ) {
                DocumentsContract.renameDocument(
                    resolver,
                    moved,
                    sanitizeName(targetName),
                ) ?: moved
            } else {
                moved
            }
            parentByPath.remove(source.path)
            parentByPath[finalUri.toString()] =
                destinationDirectory.path
            queryNode(finalUri)
        }

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        ioOutcome("document_read_failed") {
            require(offset >= 0L && maxBytes > 0) {
                "Invalid read range"
            }
            resolver.openFileDescriptor(
                uri(ref),
                "r",
            ).use { descriptor ->
                requireNotNull(descriptor) {
                    "Unable to open document"
                }
                FileInputStream(
                    descriptor.fileDescriptor,
                ).channel.use { channel ->
                    val size = channel.size()
                    if (offset >= size) {
                        return@use YFileChunk(
                            data = ByteArray(0),
                            eof = true,
                        )
                    }
                    channel.position(offset)
                    val buffer =
                        ByteBuffer.allocate(maxBytes)
                    val count = channel.read(buffer)
                    if (count <= 0) {
                        YFileChunk(
                            data = ByteArray(0),
                            eof = true,
                        )
                    } else {
                        YFileChunk(
                            data =
                                buffer.array()
                                    .copyOf(count),
                            eof =
                                offset + count >=
                                    size,
                        )
                    }
                }
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        ioOutcome("document_write_failed") {
            require(offset >= 0L) {
                "Invalid write offset"
            }
            resolver.openFileDescriptor(
                uri(ref),
                "rw",
            ).use { descriptor ->
                requireNotNull(descriptor) {
                    "Unable to open document"
                }
                FileOutputStream(
                    descriptor.fileDescriptor,
                ).channel.use { channel ->
                    if (truncate) {
                        channel.truncate(0L)
                    }
                    channel.position(offset)
                    var buffer =
                        ByteBuffer.wrap(data)
                    while (buffer.hasRemaining()) {
                        channel.write(buffer)
                    }
                }
            }
            Unit
        }

    private suspend fun create(
        parent: YFileRef,
        name: String,
        mimeType: String,
        code: String,
    ): Outcome<YFileNode> =
        ioOutcome(code) {
            require(
                parent.path != VIRTUAL_ROOT,
            ) {
                "Select a document tree first"
            }
            val created =
                DocumentsContract.createDocument(
                    resolver,
                    uri(parent),
                    mimeType,
                    sanitizeName(name),
                ) ?: error(
                    "Document provider rejected create",
                )
            parentByPath[created.toString()] =
                parent.path
            queryNode(created)
        }

    private fun virtualRoots(): List<YFileNode> =
        store.trees().mapNotNull { treeUri ->
            runCatching {
                val documentId =
                    DocumentsContract
                        .getTreeDocumentId(treeUri)
                val documentUri =
                    DocumentsContract
                        .buildDocumentUriUsingTree(
                            treeUri,
                            documentId,
                        )
                parentByPath[
                    documentUri.toString()
                ] = VIRTUAL_ROOT
                queryNode(documentUri)
            }.getOrNull()
        }

    private fun listDirect(
        directory: YFileRef,
    ): List<YFileNode> {
        val directoryUri = uri(directory)
        val documentId =
            DocumentsContract.getDocumentId(
                directoryUri,
            )
        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    directoryUri,
                    documentId,
                )

        return resolver.query(
            childrenUri,
            PROJECTION,
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor == null) {
                return@use emptyList()
            }
            buildList {
                while (cursor.moveToNext()) {
                    val childId = cursor.string(
                        DocumentsContract.Document
                            .COLUMN_DOCUMENT_ID,
                    )
                    val childUri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                directoryUri,
                                childId,
                            )
                    parentByPath[
                        childUri.toString()
                    ] = directory.path
                    add(
                        cursorNode(
                            cursor,
                            childUri,
                        ),
                    )
                }
            }
        }
    }

    private fun recursiveSearch(
        directory: YFileRef,
        needle: String,
        showHidden: Boolean,
        maxResults: Int,
    ): List<YFileNode> {
        val queue = ArrayDeque<YFileRef>()
        val result = mutableListOf<YFileNode>()
        queue.add(directory)

        while (
            queue.isNotEmpty() &&
            result.size < maxResults
        ) {
            val current = queue.removeFirst()
            for (node in listDirect(current)) {
                if (!showHidden && node.hidden) {
                    continue
                }
                if (
                    node.name.lowercase()
                        .contains(needle)
                ) {
                    result += node
                    if (
                        result.size >= maxResults
                    ) {
                        break
                    }
                }
                if (
                    node.type ==
                        YFileType.Directory
                ) {
                    queue.addLast(node.ref)
                }
            }
        }

        return result
    }

    private fun queryNode(
        documentUri: Uri,
    ): YFileNode =
        resolver.query(
            documentUri,
            PROJECTION,
            null,
            null,
            null,
        ).use { cursor ->
            requireNotNull(cursor) {
                "Unable to query document"
            }
            require(cursor.moveToFirst()) {
                "Document does not exist"
            }
            cursorNode(cursor, documentUri)
        }

    private fun cursorNode(
        cursor: Cursor,
        documentUri: Uri,
    ): YFileNode {
        val name = cursor.string(
            DocumentsContract.Document
                .COLUMN_DISPLAY_NAME,
        )
        val mime = cursor.string(
            DocumentsContract.Document
                .COLUMN_MIME_TYPE,
        )
        val flags = cursor.int(
            DocumentsContract.Document
                .COLUMN_FLAGS,
        )
        val directory =
            mime ==
                DocumentsContract.Document
                    .MIME_TYPE_DIR
        val writable =
            flags and (
                DocumentsContract.Document
                    .FLAG_SUPPORTS_WRITE or
                    DocumentsContract.Document
                        .FLAG_DIR_SUPPORTS_CREATE
                ) != 0

        return YFileNode(
            ref = YFileRef(
                providerId = PROVIDER_ID,
                path = documentUri.toString(),
            ),
            name = name,
            type = if (directory) {
                YFileType.Directory
            } else {
                YFileType.File
            },
            sizeBytes = if (
                directory ||
                cursor.isNull(
                    cursor.column(
                        DocumentsContract.Document
                            .COLUMN_SIZE,
                    ),
                )
            ) {
                null
            } else {
                cursor.long(
                    DocumentsContract.Document
                        .COLUMN_SIZE,
                )
            },
            modifiedAtMillis = cursor.longOrNull(
                DocumentsContract.Document
                    .COLUMN_LAST_MODIFIED,
            ),
            mimeType = mime,
            hidden = name.startsWith("."),
            readable = true,
            writable = writable,
        )
    }

    private fun sort(
        entries: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator = when (query.sort) {
            YFileSort.Name ->
                compareBy<YFileNode> {
                    it.name.lowercase()
                }
            YFileSort.Modified ->
                compareBy<YFileNode> {
                    it.modifiedAtMillis ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Size ->
                compareBy<YFileNode> {
                    it.sizeBytes ?: 0L
                }.thenBy {
                    it.name.lowercase()
                }
            YFileSort.Type ->
                compareBy<YFileNode> {
                    it.type.ordinal
                }.thenBy {
                    it.name.lowercase()
                }
        }
        val directed =
            if (query.descending) {
                comparator.reversed()
            } else {
                comparator
            }
        return entries.sortedWith(
            compareByDescending<YFileNode> {
                it.type == YFileType.Directory
            }.then(directed),
        )
    }

    private fun uri(ref: YFileRef): Uri {
        require(
            ref.providerId == PROVIDER_ID &&
                ref.path != VIRTUAL_ROOT,
        ) {
            "Invalid document reference"
        }
        return Uri.parse(ref.path)
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

    private suspend inline fun <T> ioOutcome(
        code: String,
        crossinline block: () -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                Outcome.Success(block())
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = code,
                    message = error.message
                        ?: DOCUMENT_OPERATION_MESSAGE,
                    cause = error,
                    retryable = true,
                )
            }
        }

    private fun Cursor.column(
        name: String,
    ): Int =
        getColumnIndexOrThrow(name)

    private fun Cursor.string(
        name: String,
    ): String =
        getString(column(name)).orEmpty()

    private fun Cursor.int(
        name: String,
    ): Int =
        getInt(column(name))

    private fun Cursor.long(
        name: String,
    ): Long =
        getLong(column(name))

    private fun Cursor.longOrNull(
        name: String,
    ): Long? {
        val index = column(name)
        return if (isNull(index)) {
            null
        } else {
            getLong(index)
        }
    }

    companion object {
        const val PROVIDER_ID = "document"
        private const val VIRTUAL_ROOT =
            "document://virtual-root"
        private const val DEFAULT_MIME_TYPE =
            "application/octet-stream"
        private const val DOCUMENT_OPERATION_MESSAGE =
            "Document operation failed"

        private val PROJECTION = arrayOf(
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
