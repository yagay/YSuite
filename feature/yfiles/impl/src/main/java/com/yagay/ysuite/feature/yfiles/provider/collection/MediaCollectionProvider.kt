package com.yagay.ysuite.feature.yfiles.provider.collection

import android.content.ContentResolver
import android.provider.MediaStore
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaCollectionProvider(
    private val resolver: ContentResolver,
) : YFileProvider {
    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Local,
            capabilities = setOf(
                YFileCapability.Browse,
                YFileCapability.Search,
            ),
        )

    override fun root(): YFileRef =
        YFileRef(PROVIDER_ID, ROOT)

    override fun parent(ref: YFileRef): YFileRef? =
        when {
            ref.providerId != PROVIDER_ID -> null
            ref.path == ROOT -> null
            else -> root()
        }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        withContext(Dispatchers.IO) {
            try {
                if (directory.path == ROOT) {
                    return@withContext Outcome.Success(
                        collections.map { (id, label) ->
                            YFileNode(
                                ref = YFileRef(PROVIDER_ID, id),
                                name = label,
                                type = YFileType.Directory,
                                mimeType = "inode/directory",
                                readable = true,
                                writable = false,
                            )
                        },
                    )
                }
                require(collections.any { it.first == directory.path }) {
                    "Unknown smart collection"
                }
                Outcome.Success(
                    queryCollection(directory.path, query),
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code = "collection_list_failed",
                    message = error.message
                        ?: "Unable to query media collection",
                    cause = error,
                )
            }
        }

    override suspend fun stat(ref: YFileRef): Outcome<YFileNode> {
        if (ref.path == ROOT) {
            return Outcome.Success(
                YFileNode(
                    ref = root(),
                    name = "Collections",
                    type = YFileType.Directory,
                ),
            )
        }
        val label = collections.firstOrNull { it.first == ref.path }?.second
            ?: return Outcome.Failure(
                code = "collection_not_found",
                message = "Unknown smart collection",
            )
        return Outcome.Success(
            YFileNode(
                ref = ref,
                name = label,
                type = YFileType.Directory,
                mimeType = "inode/directory",
                readable = true,
            ),
        )
    }

    override suspend fun createDirectory(parent: YFileRef, name: String): Outcome<YFileNode> = readOnly()
    override suspend fun createFile(parent: YFileRef, name: String): Outcome<YFileNode> = readOnly()
    override suspend fun rename(ref: YFileRef, newName: String): Outcome<YFileNode> = readOnly()
    override suspend fun delete(ref: YFileRef): Outcome<Unit> = readOnly()
    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> = readOnly()
    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> = readOnly()
    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> = readOnly()
    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> = readOnly()

    private fun queryCollection(
        category: String,
        query: YFileQuery,
    ): List<YFileNode> {
        val selectionParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (category) {
            PHOTOS -> {
                selectionParts +=
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            }
            VIDEOS -> {
                selectionParts +=
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            }
            MUSIC -> {
                selectionParts +=
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()
            }
            DOCUMENTS -> {
                selectionParts +=
                    "(${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ? OR " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?)"
                args += "text/%"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString()
            }
            DOWNLOADS -> {
                selectionParts +=
                    "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                args += "Download/%"
            }
            APKS -> {
                selectionParts +=
                    "LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE ?"
                args += "%.apk"
            }
        }
        query.text.trim().takeIf { it.isNotEmpty() }?.let { needle ->
            selectionParts +=
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            args += "%$needle%"
        }

        val nodes = mutableListOf<YFileNode>()
        resolver.query(
            MediaStore.Files.getContentUri("external"),
            PROJECTION,
            selectionParts.joinToString(" AND ").takeIf { it.isNotBlank() },
            args.toTypedArray().takeIf { it.isNotEmpty() },
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
            )
            val dataIndex = cursor.getColumnIndexOrThrow(
                MediaStore.Files.FileColumns.DATA,
            )
            val sizeIndex = cursor.getColumnIndexOrThrow(
                MediaStore.Files.FileColumns.SIZE,
            )
            val modifiedIndex = cursor.getColumnIndexOrThrow(
                MediaStore.Files.FileColumns.DATE_MODIFIED,
            )
            val mimeIndex = cursor.getColumnIndexOrThrow(
                MediaStore.Files.FileColumns.MIME_TYPE,
            )
            while (cursor.moveToNext() && nodes.size < query.maxResults) {
                val filePath = cursor.getString(dataIndex) ?: continue
                val file = File(filePath)
                if (!file.exists()) continue
                val name = cursor.getString(nameIndex) ?: file.name
                if (!query.showHidden && name.startsWith(".")) continue
                nodes += YFileNode(
                    ref = YFileRef("local", filePath),
                    name = name,
                    type = YFileType.File,
                    sizeBytes = cursor.getLong(sizeIndex),
                    modifiedAtMillis =
                        cursor.getLong(modifiedIndex) * 1000L,
                    mimeType = cursor.getString(mimeIndex),
                    hidden = name.startsWith("."),
                    readable = file.canRead(),
                    writable = file.canWrite(),
                )
            }
        }
        return sort(nodes, query)
    }

    private fun sort(
        nodes: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator = when (query.sort) {
            YFileSort.Name ->
                compareBy<YFileNode> { it.name.lowercase() }
            YFileSort.Modified ->
                compareBy { it.modifiedAtMillis ?: 0L }
            YFileSort.Size ->
                compareBy { it.sizeBytes ?: 0L }
            YFileSort.Type ->
                compareBy { it.mimeType.orEmpty() }
        }.thenBy { it.name.lowercase() }
        return nodes.sortedWith(
            if (query.descending) comparator.reversed() else comparator,
        )
    }

    private fun <T> readOnly(): Outcome<T> =
        Outcome.Failure(
            code = "collection_read_only",
            message =
                "Smart collections are virtual; edit the underlying file instead",
        )

    companion object {
        const val PROVIDER_ID = "collections"
        private const val ROOT = "collections://root"
        private const val PHOTOS = "collections://photos"
        private const val VIDEOS = "collections://videos"
        private const val MUSIC = "collections://music"
        private const val DOCUMENTS = "collections://documents"
        private const val DOWNLOADS = "collections://downloads"
        private const val APKS = "collections://apks"

        private val collections = listOf(
            PHOTOS to "Photos",
            VIDEOS to "Videos",
            MUSIC to "Music",
            DOCUMENTS to "Documents",
            DOWNLOADS to "Downloads",
            APKS to "APKs",
        )

        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
    }
}
