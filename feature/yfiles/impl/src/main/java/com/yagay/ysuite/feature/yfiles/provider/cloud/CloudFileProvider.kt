package com.yagay.ysuite.feature.yfiles.provider.cloud

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
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CloudFileProvider(
    private val store: YFilesCloudStore,
    private val client: OkHttpClient,
    private val cacheDirectory: File,
) : YFileProvider {
    private data class Parsed(
        val profileId: String,
        val remoteId: String,
    )

    private val parents =
        ConcurrentHashMap<String, String>()

    override val descriptor =
        YFileProviderDescriptor(
            id = PROVIDER_ID,
            kind = YFileProviderKind.Remote,
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
        YFileRef(PROVIDER_ID, VIRTUAL_ROOT)

    override fun parent(ref: YFileRef): YFileRef? {
        requireProvider(ref)
        if (ref.path == VIRTUAL_ROOT) return null
        val parsed = parse(ref)
        val profile = store.profile(parsed.profileId)
            ?: return root()
        if (parsed.remoteId == profileRoot(profile)) {
            return root()
        }
        val parentId =
            parents[ref.path]
                ?: return ref(
                    parsed.profileId,
                    profileRoot(profile),
                )
        return ref(parsed.profileId, parentId)
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        withContext(Dispatchers.IO) {
            try {
                if (directory.path == VIRTUAL_ROOT) {
                    return@withContext Outcome.Success(
                        sort(
                            store.profiles()
                                .map(::profileNode),
                            query,
                        ),
                    )
                }
                val parsed = parse(directory)
                val profile =
                    store.profile(parsed.profileId)
                        ?: error(
                            "Cloud profile no longer exists",
                        )
                val nodes =
                    if (
                        query.recursive &&
                        query.text.isNotBlank()
                    ) {
                        recursiveSearch(
                            profile,
                            parsed.remoteId,
                            query,
                        )
                    } else {
                        listDirect(
                            profile,
                            parsed.remoteId,
                        ).filter {
                            query.showHidden ||
                                !it.hidden
                        }.filter {
                            query.text.isBlank() ||
                                it.name.contains(
                                    query.text,
                                    ignoreCase = true,
                                )
                        }.take(
                            query.maxResults,
                        )
                    }
                Outcome.Success(
                    sort(nodes, query),
                )
            } catch (error: Throwable) {
                failure(
                    "cloud_list_failed",
                    error,
                )
            }
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> =
        withContext(Dispatchers.IO) {
            try {
                if (ref.path == VIRTUAL_ROOT) {
                    Outcome.Success(
                            YFileNode(
                                ref = root(),
                                name = "Cloud",
                                type =
                                    YFileType
                                        .Directory,
                            ),
                        )
                } else {
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                if (
                    parsed.remoteId ==
                    profileRoot(profile)
                ) {
                    Outcome.Success(
                        profileNode(profile),
                    )
                } else {
                    Outcome.Success(
                        metadata(
                            profile,
                            parsed.remoteId,
                        ),
                    )
                }
                }
            } catch (error: Throwable) {
                failure(
                    "cloud_stat_failed",
                    error,
                )
            }
        }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        mutate(
            parent,
            "cloud_create_directory_failed",
        ) { profile, parsed ->
            when (profile.kind) {
                YFilesCloudKind.GoogleDrive ->
                    googleCreate(
                        profile,
                        parsed.remoteId,
                        name,
                        folder = true,
                    )
                YFilesCloudKind.Dropbox ->
                    dropboxCreateFolder(
                        profile,
                        childDropbox(
                            parsed.remoteId,
                            name,
                        ),
                    )
                YFilesCloudKind.OneDrive ->
                    oneDriveCreateFolder(
                        profile,
                        parsed.remoteId,
                        name,
                    )
            }
        }

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        mutate(
            parent,
            "cloud_create_file_failed",
        ) { profile, parsed ->
            when (profile.kind) {
                YFilesCloudKind.GoogleDrive ->
                    googleCreate(
                        profile,
                        parsed.remoteId,
                        name,
                        folder = false,
                    )
                YFilesCloudKind.Dropbox ->
                    dropboxUpload(
                        profile,
                        childDropbox(
                            parsed.remoteId,
                            name,
                        ),
                        ByteArray(0),
                    )
                YFilesCloudKind.OneDrive ->
                    oneDriveUploadNew(
                        profile,
                        parsed.remoteId,
                        name,
                        ByteArray(0),
                    )
            }
        }

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        mutate(
            ref,
            "cloud_rename_failed",
        ) { profile, parsed ->
            when (profile.kind) {
                YFilesCloudKind.GoogleDrive ->
                    googlePatch(
                        profile,
                        parsed.remoteId,
                        JSONObject()
                            .put(
                                "name",
                                safeName(
                                    newName,
                                ),
                            ),
                    )
                YFilesCloudKind.Dropbox -> {
                    val parent =
                        parsed.remoteId
                            .substringBeforeLast(
                                "/",
                                "",
                            )
                    dropboxMove(
                        profile,
                        parsed.remoteId,
                        childDropbox(
                            parent,
                            newName,
                        ),
                    )
                }
                YFilesCloudKind.OneDrive ->
                    oneDrivePatch(
                        profile,
                        parsed.remoteId,
                        JSONObject()
                            .put(
                                "name",
                                safeName(
                                    newName,
                                ),
                            ),
                    )
            }
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                when (profile.kind) {
                    YFilesCloudKind.GoogleDrive ->
                        request(
                            profile,
                            Request.Builder()
                                .url(
                                    GOOGLE_FILES +
                                        "/" +
                                        urlSegment(
                                            parsed.remoteId,
                                        ),
                                )
                                .delete(),
                        ).use {
                            requireSuccess(it)
                        }
                    YFilesCloudKind.Dropbox ->
                        dropboxRpc(
                            profile,
                            DROPBOX_DELETE,
                            JSONObject()
                                .put(
                                    "path",
                                    parsed.remoteId,
                                ),
                        )
                    YFilesCloudKind.OneDrive ->
                        request(
                            profile,
                            Request.Builder()
                                .url(
                                    oneDriveItemUrl(
                                        parsed.remoteId,
                                    ),
                                )
                                .delete(),
                        ).use {
                            requireSuccess(it)
                        }
                }
                invalidate(ref)
                Outcome.Success(Unit)
            } catch (error: Throwable) {
                failure(
                    "cloud_delete_failed",
                    error,
                )
            }
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        withContext(Dispatchers.IO) {
            try {
                val sourceParsed =
                    parse(source)
                val destParsed =
                    parse(
                        destinationDirectory,
                    )
                require(
                    sourceParsed.profileId ==
                        destParsed.profileId,
                ) {
                    "Use streamed copy for different cloud accounts"
                }
                val profile =
                    store.profile(
                        sourceParsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                val sourceNode =
                    metadata(
                        profile,
                        sourceParsed.remoteId,
                    )
                require(
                    sourceNode.type ==
                        YFileType.File,
                ) {
                    "Cloud folder copy is handled through streamed copy"
                }
                val bytes =
                    downloadBytes(
                        profile,
                        sourceParsed.remoteId,
                    )
                val created =
                    when (profile.kind) {
                        YFilesCloudKind.GoogleDrive -> {
                            val meta =
                                googleCreate(
                                    profile,
                                    destParsed.remoteId,
                                    targetName,
                                    folder = false,
                                )
                            googleUploadExisting(
                                profile,
                                parse(meta.ref)
                                    .remoteId,
                                bytes,
                            )
                            metadata(
                                profile,
                                parse(meta.ref)
                                    .remoteId,
                            )
                        }
                        YFilesCloudKind.Dropbox ->
                            dropboxUpload(
                                profile,
                                childDropbox(
                                    destParsed.remoteId,
                                    targetName,
                                ),
                                bytes,
                                overwrite =
                                    replace,
                            )
                        YFilesCloudKind.OneDrive ->
                            oneDriveUploadNew(
                                profile,
                                destParsed.remoteId,
                                targetName,
                                bytes,
                            )
                    }
                Outcome.Success(created)
            } catch (error: Throwable) {
                failure(
                    "cloud_copy_failed",
                    error,
                )
            }
        }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> =
        withContext(Dispatchers.IO) {
            try {
                val sourceParsed =
                    parse(source)
                val destParsed =
                    parse(
                        destinationDirectory,
                    )
                require(
                    sourceParsed.profileId ==
                        destParsed.profileId,
                )
                val profile =
                    store.profile(
                        sourceParsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                val node =
                    when (profile.kind) {
                        YFilesCloudKind.GoogleDrive -> {
                            val oldParent =
                                parents[source.path]
                            val url =
                                buildString {
                                    append(
                                        GOOGLE_FILES,
                                    )
                                    append('/')
                                    append(
                                        urlSegment(
                                            sourceParsed
                                                .remoteId,
                                        ),
                                    )
                                    append(
                                        "?addParents=",
                                    )
                                    append(
                                        urlQuery(
                                            destParsed
                                                .remoteId,
                                        ),
                                    )
                                    if (
                                        !oldParent
                                            .isNullOrBlank()
                                    ) {
                                        append(
                                            "&removeParents=",
                                        )
                                        append(
                                            urlQuery(
                                                oldParent,
                                            ),
                                        )
                                    }
                                    append(
                                        "&fields=id,name,mimeType,size,modifiedTime,parents",
                                    )
                                }
                            val builder =
                                Request.Builder()
                                    .url(url)
                                    .patch(
                                        JSONObject()
                                            .put(
                                                "name",
                                                safeName(
                                                    targetName,
                                                ),
                                            )
                                            .toString()
                                            .toRequestBody(
                                                JSON,
                                            ),
                                    )
                            parseGoogleNode(
                                profile,
                                requestJson(
                                    profile,
                                    builder,
                                ),
                                destParsed
                                    .remoteId,
                            )
                        }
                        YFilesCloudKind.Dropbox ->
                            dropboxMove(
                                profile,
                                sourceParsed
                                    .remoteId,
                                childDropbox(
                                    destParsed
                                        .remoteId,
                                    targetName,
                                ),
                            )
                        YFilesCloudKind.OneDrive ->
                            oneDrivePatch(
                                profile,
                                sourceParsed
                                    .remoteId,
                                JSONObject()
                                    .put(
                                        "name",
                                        safeName(
                                            targetName,
                                        ),
                                    )
                                    .put(
                                        "parentReference",
                                        JSONObject()
                                            .put(
                                                "id",
                                                destParsed
                                                    .remoteId,
                                            ),
                                    ),
                            )
                    }
                parents[node.ref.path] =
                    destParsed.remoteId
                invalidate(source)
                Outcome.Success(node)
            } catch (error: Throwable) {
                failure(
                    "cloud_move_failed",
                    error,
                )
            }
        }

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        withContext(Dispatchers.IO) {
            try {
                require(offset >= 0L)
                require(maxBytes > 0)
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                val cache =
                    cacheFile(ref, "read")
                if (
                    offset == 0L ||
                    !cache.isFile
                ) {
                    cache.parentFile?.mkdirs()
                    cache.writeBytes(
                        downloadBytes(
                            profile,
                            parsed.remoteId,
                        ),
                    )
                }
                val chunk =
                    RandomAccessFile(
                        cache,
                        "r",
                    ).use { file ->
                        if (
                            offset >= file.length()
                        ) {
                            YFileChunk(
                                ByteArray(0),
                                true,
                            )
                        } else {
                            file.seek(offset)
                            val buffer =
                                ByteArray(maxBytes)
                            val count =
                                file.read(buffer)
                            if (count <= 0) {
                                YFileChunk(
                                    ByteArray(0),
                                    true,
                                )
                            } else {
                                YFileChunk(
                                    buffer.copyOf(count),
                                    offset + count >=
                                        file.length(),
                                )
                            }
                        }
                    }
                Outcome.Success(chunk)
            } catch (error: Throwable) {
                failure(
                    "cloud_read_failed",
                    error,
                )
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            try {
                require(offset >= 0L)
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                val cache =
                    cacheFile(ref, "write")
                cache.parentFile?.mkdirs()
                if (
                    !truncate &&
                    offset > 0L &&
                    !cache.exists()
                ) {
                    cache.writeBytes(
                        downloadBytes(
                            profile,
                            parsed.remoteId,
                        ),
                    )
                }
                RandomAccessFile(
                    cache,
                    "rw",
                ).use { file ->
                    if (truncate) {
                        file.setLength(0L)
                    }
                    file.seek(offset)
                    file.write(data)
                    file.fd.sync()
                }
                val bytes =
                    cache.readBytes()
                when (profile.kind) {
                    YFilesCloudKind.GoogleDrive ->
                        googleUploadExisting(
                            profile,
                            parsed.remoteId,
                            bytes,
                        )
                    YFilesCloudKind.Dropbox ->
                        dropboxUpload(
                            profile,
                            parsed.remoteId,
                            bytes,
                            overwrite = true,
                        )
                    YFilesCloudKind.OneDrive ->
                        oneDriveUploadExisting(
                            profile,
                            parsed.remoteId,
                            bytes,
                        )
                }
                invalidateRead(ref)
                Outcome.Success(Unit)
            } catch (error: Throwable) {
                failure(
                    "cloud_write_failed",
                    error,
                )
            }
        }

    fun profiles() = store.profiles()

    fun saveProfile(profile: YFilesCloudProfile) =
        store.save(profile)

    fun deleteProfile(id: String) =
        store.delete(id)

    suspend fun test(
        profile: YFilesCloudProfile,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            try {
                listDirect(
                    profile,
                    profileRoot(profile),
                )
                Outcome.Success(Unit)
            } catch (error: Throwable) {
                failure(
                    "cloud_test_failed",
                    error,
                )
            }
        }

    private suspend fun recursiveSearch(
        profile: YFilesCloudProfile,
        root: String,
        query: YFileQuery,
    ): List<YFileNode> {
        val result =
            mutableListOf<YFileNode>()
        val queue =
            ArrayDeque<String>()
        queue.add(root)
        var visited = 0
        while (
            queue.isNotEmpty() &&
            result.size <
                query.maxResults &&
            visited < MAX_TRAVERSAL
        ) {
            val current =
                queue.removeFirst()
            for (
                node in listDirect(
                    profile,
                    current,
                )
            ) {
                visited += 1
                if (
                    query.showHidden ||
                    !node.hidden
                ) {
                    if (
                        node.name.contains(
                            query.text,
                            ignoreCase = true,
                        )
                    ) {
                        result += node
                    }
                }
                if (
                    node.type ==
                    YFileType.Directory
                ) {
                    queue.add(
                        parse(node.ref)
                            .remoteId,
                    )
                }
                if (
                    result.size >=
                    query.maxResults ||
                    visited >=
                    MAX_TRAVERSAL
                ) {
                    break
                }
            }
        }
        return result
    }

    private fun listDirect(
        profile: YFilesCloudProfile,
        parentId: String,
    ): List<YFileNode> =
        when (profile.kind) {
            YFilesCloudKind.GoogleDrive ->
                googleList(
                    profile,
                    parentId,
                )
            YFilesCloudKind.Dropbox ->
                dropboxList(
                    profile,
                    parentId,
                )
            YFilesCloudKind.OneDrive ->
                oneDriveList(
                    profile,
                    parentId,
                )
        }

    private fun metadata(
        profile: YFilesCloudProfile,
        remoteId: String,
    ): YFileNode =
        when (profile.kind) {
            YFilesCloudKind.GoogleDrive ->
                parseGoogleNode(
                    profile,
                    requestJson(
                        profile,
                        Request.Builder()
                            .url(
                                GOOGLE_FILES +
                                    "/" +
                                    urlSegment(
                                        remoteId,
                                    ) +
                                    "?fields=id,name,mimeType,size,modifiedTime,parents",
                            ),
                    ),
                    null,
                )
            YFilesCloudKind.Dropbox ->
                parseDropboxNode(
                    profile,
                    dropboxRpc(
                        profile,
                        DROPBOX_METADATA,
                        JSONObject()
                            .put(
                                "path",
                                remoteId,
                            ),
                    ),
                    null,
                )
            YFilesCloudKind.OneDrive ->
                parseOneDriveNode(
                    profile,
                    requestJson(
                        profile,
                        Request.Builder()
                            .url(
                                oneDriveItemUrl(
                                    remoteId,
                                ),
                            ),
                    ),
                    null,
                )
        }

    private fun googleList(
        profile: YFilesCloudProfile,
        parentId: String,
    ): List<YFileNode> {
        val result =
            mutableListOf<YFileNode>()
        var token: String? = null
        do {
            val q =
                "'$parentId' in parents and trashed=false"
            val url =
                buildString {
                    append(GOOGLE_FILES)
                    append(
                        "?q=",
                    )
                    append(
                        urlQuery(q),
                    )
                    append(
                        "&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,parents)",
                    )
                    token?.let {
                        append(
                            "&pageToken=",
                        )
                        append(
                            urlQuery(it),
                        )
                    }
                }
            val json =
                requestJson(
                    profile,
                    Request.Builder()
                        .url(url),
                )
            val files =
                json.optJSONArray(
                    "files",
                ) ?: JSONArray()
            for (
                index in 0 until
                    files.length()
            ) {
                result +=
                    parseGoogleNode(
                        profile,
                        files.getJSONObject(
                            index,
                        ),
                        parentId,
                    )
            }
            token =
                json.optString(
                    "nextPageToken",
                ).takeIf {
                    it.isNotBlank()
                }
        } while (token != null)
        return result
    }

    private fun googleCreate(
        profile: YFilesCloudProfile,
        parentId: String,
        name: String,
        folder: Boolean,
    ): YFileNode {
        val body =
            JSONObject()
                .put(
                    "name",
                    safeName(name),
                )
                .put(
                    "parents",
                    JSONArray()
                        .put(parentId),
                )
        if (folder) {
            body.put(
                "mimeType",
                GOOGLE_FOLDER_MIME,
            )
        }
        return parseGoogleNode(
            profile,
            requestJson(
                profile,
                Request.Builder()
                    .url(
                        GOOGLE_FILES +
                            "?fields=id,name,mimeType,size,modifiedTime,parents",
                    )
                    .post(
                        body.toString()
                            .toRequestBody(
                                JSON,
                            ),
                    ),
            ),
            parentId,
        )
    }

    private fun googlePatch(
        profile: YFilesCloudProfile,
        id: String,
        body: JSONObject,
    ): YFileNode =
        parseGoogleNode(
            profile,
            requestJson(
                profile,
                Request.Builder()
                    .url(
                        GOOGLE_FILES +
                            "/" +
                            urlSegment(id) +
                            "?fields=id,name,mimeType,size,modifiedTime,parents",
                    )
                    .patch(
                        body.toString()
                            .toRequestBody(
                                JSON,
                            ),
                    ),
            ),
            null,
        )

    private fun googleUploadExisting(
        profile: YFilesCloudProfile,
        id: String,
        data: ByteArray,
    ) {
        request(
            profile,
            Request.Builder()
                .url(
                    GOOGLE_UPLOAD +
                        "/" +
                        urlSegment(id) +
                        "?uploadType=media",
                )
                .patch(
                    data.toRequestBody(
                        BINARY,
                    ),
                ),
        ).use {
            requireSuccess(it)
        }
    }

    private fun parseGoogleNode(
        profile: YFilesCloudProfile,
        json: JSONObject,
        fallbackParent: String?,
    ): YFileNode {
        val id = json.getString("id")
        val parentsJson =
            json.optJSONArray(
                "parents",
            )
        val parent =
            if (
                parentsJson != null &&
                parentsJson.length() > 0
            ) {
                parentsJson.optString(0)
            } else {
                fallbackParent
            }
        val node =
            YFileNode(
                ref = ref(profile.id, id),
                name =
                    json.optString(
                        "name",
                        id,
                    ),
                type =
                    if (
                        json.optString(
                            "mimeType",
                        ) ==
                        GOOGLE_FOLDER_MIME
                    ) {
                        YFileType.Directory
                    } else {
                        YFileType.File
                    },
                sizeBytes =
                    json.optString(
                        "size",
                    ).toLongOrNull(),
                modifiedAtMillis =
                    parseInstant(
                        json.optString(
                            "modifiedTime",
                        ),
                    ),
                mimeType =
                    json.optString(
                        "mimeType",
                    ).takeIf {
                        it.isNotBlank()
                    },
                hidden =
                    json.optString(
                        "name",
                    ).startsWith("."),
                readable = true,
                writable = true,
            )
        parent?.let {
            parents[node.ref.path] = it
        }
        return node
    }

    private fun dropboxList(
        profile: YFilesCloudProfile,
        parent: String,
    ): List<YFileNode> {
        val result =
            mutableListOf<YFileNode>()
        var json =
            dropboxRpc(
                profile,
                DROPBOX_LIST,
                JSONObject()
                    .put(
                        "path",
                        dropboxApiPath(parent),
                    )
                    .put("recursive", false),
            )
        while (true) {
            val entries =
                json.optJSONArray(
                    "entries",
                ) ?: JSONArray()
            for (
                index in 0 until
                    entries.length()
            ) {
                result +=
                    parseDropboxNode(
                        profile,
                        entries.getJSONObject(
                            index,
                        ),
                        parent,
                    )
            }
            if (
                !json.optBoolean(
                    "has_more",
                    false,
                )
            ) {
                break
            }
            json =
                dropboxRpc(
                    profile,
                    DROPBOX_LIST_CONTINUE,
                    JSONObject()
                        .put(
                            "cursor",
                            json.getString(
                                "cursor",
                            ),
                        ),
                )
        }
        return result
    }

    private fun dropboxCreateFolder(
        profile: YFilesCloudProfile,
        path: String,
    ): YFileNode {
        val json =
            dropboxRpc(
                profile,
                DROPBOX_CREATE_FOLDER,
                JSONObject()
                    .put("path", path)
                    .put(
                        "autorename",
                        false,
                    ),
            ).getJSONObject(
                "metadata",
            )
        return parseDropboxNode(
            profile,
            json,
            path.substringBeforeLast(
                "/",
                "",
            ),
        )
    }

    private fun dropboxMove(
        profile: YFilesCloudProfile,
        from: String,
        to: String,
    ): YFileNode {
        val json =
            dropboxRpc(
                profile,
                DROPBOX_MOVE,
                JSONObject()
                    .put(
                        "from_path",
                        from,
                    )
                    .put(
                        "to_path",
                        to,
                    )
                    .put(
                        "autorename",
                        false,
                    ),
            ).getJSONObject(
                "metadata",
            )
        return parseDropboxNode(
            profile,
            json,
            to.substringBeforeLast(
                "/",
                "",
            ),
        )
    }

    private fun dropboxUpload(
        profile: YFilesCloudProfile,
        path: String,
        data: ByteArray,
        overwrite: Boolean = true,
    ): YFileNode {
        val arg =
            JSONObject()
                .put("path", path)
                .put(
                    "mode",
                    if (overwrite) {
                        "overwrite"
                    } else {
                        "add"
                    },
                )
                .put(
                    "autorename",
                    !overwrite,
                )
                .put("mute", true)
        val response =
            request(
                profile,
                Request.Builder()
                    .url(DROPBOX_UPLOAD)
                    .header(
                        "Dropbox-API-Arg",
                        arg.toString(),
                    )
                    .post(
                        data.toRequestBody(
                            BINARY,
                        ),
                    ),
            )
        response.use {
            val json =
                JSONObject(
                    requireBody(it),
                )
            return parseDropboxNode(
                profile,
                json,
                path.substringBeforeLast(
                    "/",
                    "",
                ),
            )
        }
    }

    private fun parseDropboxNode(
        profile: YFilesCloudProfile,
        json: JSONObject,
        parent: String?,
    ): YFileNode {
        val path =
            json.optString(
                "path_display",
            ).ifBlank {
                json.optString(
                    "path_lower",
                )
            }
        val tag =
            json.optString(
                ".tag",
            )
        val node =
            YFileNode(
                ref =
                    ref(
                        profile.id,
                        path.ifBlank {
                            "/"
                        },
                    ),
                name =
                    json.optString(
                        "name",
                        path
                            .substringAfterLast(
                                "/",
                            ),
                    ),
                type =
                    if (
                        tag == "folder"
                    ) {
                        YFileType.Directory
                    } else {
                        YFileType.File
                    },
                sizeBytes =
                    json.optLong(
                        "size",
                        -1L,
                    ).takeIf {
                        it >= 0L
                    },
                modifiedAtMillis =
                    parseInstant(
                        json.optString(
                            "server_modified",
                        ),
                    ),
                hidden =
                    json.optString(
                        "name",
                    ).startsWith("."),
                readable = true,
                writable = true,
            )
        parent?.let {
            parents[node.ref.path] = it
        }
        return node
    }

    private fun dropboxRpc(
        profile: YFilesCloudProfile,
        url: String,
        body: JSONObject,
    ): JSONObject =
        requestJson(
            profile,
            Request.Builder()
                .url(url)
                .post(
                    body.toString()
                        .toRequestBody(JSON),
                ),
        )

    private fun oneDriveList(
        profile: YFilesCloudProfile,
        parentId: String,
    ): List<YFileNode> {
        val result =
            mutableListOf<YFileNode>()
        var url =
            if (parentId == ONEDRIVE_ROOT) {
                ONEDRIVE_BASE +
                    "/root/children"
            } else {
                oneDriveItemUrl(
                    parentId,
                ) + "/children"
            }
        while (true) {
            val json =
                requestJson(
                    profile,
                    Request.Builder()
                        .url(url),
                )
            val values =
                json.optJSONArray(
                    "value",
                ) ?: JSONArray()
            for (
                index in 0 until
                    values.length()
            ) {
                result +=
                    parseOneDriveNode(
                        profile,
                        values.getJSONObject(
                            index,
                        ),
                        parentId,
                    )
            }
            val next =
                json.optString(
                    "@odata.nextLink",
                )
            if (next.isBlank()) break
            url = next
        }
        return result
    }

    private fun oneDriveCreateFolder(
        profile: YFilesCloudProfile,
        parentId: String,
        name: String,
    ): YFileNode {
        val url =
            if (
                parentId ==
                ONEDRIVE_ROOT
            ) {
                ONEDRIVE_BASE +
                    "/root/children"
            } else {
                oneDriveItemUrl(
                    parentId,
                ) +
                    "/children"
            }
        val body =
            JSONObject()
                .put(
                    "name",
                    safeName(name),
                )
                .put(
                    "folder",
                    JSONObject(),
                )
                .put(
                    "@microsoft.graph.conflictBehavior",
                    "rename",
                )
        return parseOneDriveNode(
            profile,
            requestJson(
                profile,
                Request.Builder()
                    .url(url)
                    .post(
                        body.toString()
                            .toRequestBody(JSON),
                    ),
            ),
            parentId,
        )
    }

    private fun oneDriveUploadNew(
        profile: YFilesCloudProfile,
        parentId: String,
        name: String,
        data: ByteArray,
    ): YFileNode {
        val encoded =
            urlSegment(
                safeName(name),
            )
        val url =
            if (
                parentId ==
                ONEDRIVE_ROOT
            ) {
                ONEDRIVE_BASE +
                    "/root:/" +
                    encoded +
                    ":/content"
            } else {
                oneDriveItemUrl(
                    parentId,
                ) +
                    ":/" +
                    encoded +
                    ":/content"
            }
        return parseOneDriveNode(
            profile,
            requestJson(
                profile,
                Request.Builder()
                    .url(url)
                    .put(
                        data.toRequestBody(
                            BINARY,
                        ),
                    ),
            ),
            parentId,
        )
    }

    private fun oneDriveUploadExisting(
        profile: YFilesCloudProfile,
        id: String,
        data: ByteArray,
    ) {
        request(
            profile,
            Request.Builder()
                .url(
                    oneDriveItemUrl(id) +
                        "/content",
                )
                .put(
                    data.toRequestBody(
                        BINARY,
                    ),
                ),
        ).use {
            requireSuccess(it)
        }
    }

    private fun oneDrivePatch(
        profile: YFilesCloudProfile,
        id: String,
        body: JSONObject,
    ): YFileNode =
        parseOneDriveNode(
            profile,
            requestJson(
                profile,
                Request.Builder()
                    .url(
                        oneDriveItemUrl(id),
                    )
                    .patch(
                        body.toString()
                            .toRequestBody(JSON),
                    ),
            ),
            null,
        )

    private fun parseOneDriveNode(
        profile: YFilesCloudProfile,
        json: JSONObject,
        parent: String?,
    ): YFileNode {
        val id = json.getString("id")
        val folder =
            json.has("folder")
        val node =
            YFileNode(
                ref = ref(profile.id, id),
                name =
                    json.optString(
                        "name",
                        id,
                    ),
                type =
                    if (folder) {
                        YFileType.Directory
                    } else {
                        YFileType.File
                    },
                sizeBytes =
                    json.optLong(
                        "size",
                        -1L,
                    ).takeIf {
                        !folder &&
                            it >= 0L
                    },
                modifiedAtMillis =
                    parseInstant(
                        json.optString(
                            "lastModifiedDateTime",
                        ),
                    ),
                mimeType =
                    json.optJSONObject(
                        "file",
                    )?.optString(
                        "mimeType",
                    )?.takeIf {
                        it.isNotBlank()
                    },
                hidden =
                    json.optString(
                        "name",
                    ).startsWith("."),
                readable = true,
                writable = true,
            )
        val parentId =
            json.optJSONObject(
                "parentReference",
            )?.optString(
                "id",
            )?.takeIf {
                it.isNotBlank()
            } ?: parent
        parentId?.let {
            parents[node.ref.path] = it
        }
        return node
    }

    private fun downloadBytes(
        profile: YFilesCloudProfile,
        remoteId: String,
    ): ByteArray =
        when (profile.kind) {
            YFilesCloudKind.GoogleDrive ->
                request(
                    profile,
                    Request.Builder()
                        .url(
                            GOOGLE_FILES +
                                "/" +
                                urlSegment(
                                    remoteId,
                                ) +
                                "?alt=media",
                        ),
                ).use {
                    requireBytes(it)
                }
            YFilesCloudKind.Dropbox ->
                request(
                    profile,
                    Request.Builder()
                        .url(
                            DROPBOX_DOWNLOAD,
                        )
                        .header(
                            "Dropbox-API-Arg",
                            JSONObject()
                                .put(
                                    "path",
                                    remoteId,
                                )
                                .toString(),
                        )
                        .post(
                            ByteArray(0)
                                .toRequestBody(
                                    BINARY,
                                ),
                        ),
                ).use {
                    requireBytes(it)
                }
            YFilesCloudKind.OneDrive ->
                request(
                    profile,
                    Request.Builder()
                        .url(
                            oneDriveItemUrl(
                                remoteId,
                            ) +
                                "/content",
                        ),
                ).use {
                    requireBytes(it)
                }
        }

    private fun requestJson(
        profile: YFilesCloudProfile,
        builder: Request.Builder,
    ): JSONObject =
        request(
            profile,
            builder,
        ).use {
            JSONObject(requireBody(it))
        }

    private fun request(
        profile: YFilesCloudProfile,
        builder: Request.Builder,
    ): okhttp3.Response =
        client.newCall(
            builder
                .header(
                    "Authorization",
                    "Bearer " +
                        profile.accessToken,
                )
                .header(
                    "Accept",
                    "application/json",
                )
                .build(),
        ).execute()

    private fun requireBody(
        response: okhttp3.Response,
    ): String {
        requireSuccess(response)
        return response.body
            ?.string()
            ?: error(
                "Cloud provider returned no data",
            )
    }

    private fun requireBytes(
        response: okhttp3.Response,
    ): ByteArray {
        requireSuccess(response)
        return response.body
            ?.bytes()
            ?: error(
                "Cloud provider returned no data",
            )
    }

    private fun requireSuccess(
        response: okhttp3.Response,
    ) {
        if (!response.isSuccessful) {
            val message =
                response.body
                    ?.string()
                    ?.take(512)
                    .orEmpty()
            error(
                "HTTP " +
                    response.code +
                    (
                        if (
                            message.isBlank()
                        ) {
                            ""
                        } else {
                            ": " + message
                        }
                        ),
            )
        }
    }

    private suspend fun <T> mutate(
        ref: YFileRef,
        code: String,
        action:
            (YFilesCloudProfile, Parsed) -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    ) ?: error(
                        "Cloud profile no longer exists",
                    )
                Outcome.Success(
                    action(profile, parsed),
                )
            } catch (error: Throwable) {
                failure(code, error)
            }
        }

    private fun profileRoot(
        profile: YFilesCloudProfile,
    ): String =
        when (profile.kind) {
            YFilesCloudKind.GoogleDrive ->
                "root"
            YFilesCloudKind.Dropbox ->
                ""
            YFilesCloudKind.OneDrive ->
                ONEDRIVE_ROOT
        }

    private fun profileNode(
        profile: YFilesCloudProfile,
    ): YFileNode =
        YFileNode(
            ref =
                ref(
                    profile.id,
                    profileRoot(profile),
                ),
            name = profile.name,
            type = YFileType.Directory,
            mimeType = "inode/directory",
            readable = true,
            writable = true,
        )

    private fun parse(ref: YFileRef): Parsed {
        requireProvider(ref)
        require(
            ref.path != VIRTUAL_ROOT,
        ) {
            "Select a cloud account first"
        }
        val index =
            ref.path.indexOf(
                SEPARATOR,
            )
        require(index > 0)
        return Parsed(
            profileId =
                ref.path.substring(0, index),
            remoteId =
                ref.path.substring(
                    index +
                        SEPARATOR.length,
                ),
        )
    }

    private fun ref(
        profileId: String,
        remoteId: String,
    ) = YFileRef(
        providerId = PROVIDER_ID,
        path =
            profileId +
                SEPARATOR +
                remoteId,
    )

    private fun requireProvider(
        ref: YFileRef,
    ) {
        require(
            ref.providerId == PROVIDER_ID,
        )
    }

    private fun childDropbox(
        parent: String,
        name: String,
    ): String {
        val base =
            parent.trimEnd('/')
        return (
            if (base.isBlank()) {
                ""
            } else {
                base
            }
            ) +
            "/" +
            safeName(name)
    }

    private fun dropboxApiPath(
        path: String,
    ): String =
        if (
            path.isBlank() ||
            path == "/"
        ) {
            ""
        } else {
            path
        }

    private fun oneDriveItemUrl(
        id: String,
    ): String =
        ONEDRIVE_BASE +
            "/items/" +
            urlSegment(id)

    private fun safeName(
        value: String,
    ): String =
        value.trim()
            .replace('/', '_')
            .replace('\\', '_')
            .replace('\u0000', '_')
            .ifBlank {
                "file"
            }

    private fun urlSegment(
        value: String,
    ): String =
        URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
                .name(),
        ).replace("+", "%20")

    private fun urlQuery(
        value: String,
    ): String = urlSegment(value)

    private fun parseInstant(
        value: String,
    ): Long? =
        value.takeIf {
            it.isNotBlank()
        }?.let {
            runCatching {
                Instant.parse(it)
                    .toEpochMilli()
            }.getOrNull()
        }

    private fun cacheFile(
        ref: YFileRef,
        kind: String,
    ): File =
        File(
            cacheDirectory.apply {
                mkdirs()
            },
            kind +
                "-" +
                ref.path.hashCode()
                    .toUInt()
                    .toString(16) +
                ".bin",
        )

    private fun invalidate(
        ref: YFileRef,
    ) {
        cacheFile(ref, "read").delete()
        cacheFile(ref, "write").delete()
    }

    private fun invalidateRead(
        ref: YFileRef,
    ) {
        cacheFile(ref, "read").delete()
    }

    private fun sort(
        entries: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val comparator =
            when (query.sort) {
                YFileSort.Name ->
                    compareBy<YFileNode> {
                        it.name.lowercase()
                    }
                YFileSort.Modified ->
                    compareBy {
                        it.modifiedAtMillis ?: 0L
                    }
                YFileSort.Size ->
                    compareBy {
                        it.sizeBytes ?: 0L
                    }
                YFileSort.Type ->
                    compareBy {
                        it.type.ordinal
                    }
            }.thenBy {
                it.name.lowercase()
            }
        val directed =
            if (query.descending) {
                comparator.reversed()
            } else {
                comparator
            }
        return entries.sortedWith(
            compareByDescending<YFileNode> {
                it.type ==
                    YFileType.Directory
            }.then(directed),
        )
    }

    private fun <T> failure(
        code: String,
        error: Throwable,
    ): Outcome<T> =
        Outcome.Failure(
            code = code,
            message =
                error.message
                    ?: "Cloud operation failed",
            cause = error,
            retryable = true,
        )

    companion object {
        const val PROVIDER_ID = "cloud"
        private const val VIRTUAL_ROOT =
            "cloud://virtual-root"
        private const val SEPARATOR = "::"
        private const val MAX_TRAVERSAL =
            100_000

        private val JSON =
            "application/json; charset=utf-8"
                .toMediaType()
        private val BINARY =
            "application/octet-stream"
                .toMediaType()

        private const val GOOGLE_FILES =
            "https://www.googleapis.com/drive/v3/files"
        private const val GOOGLE_UPLOAD =
            "https://www.googleapis.com/upload/drive/v3/files"
        private const val GOOGLE_FOLDER_MIME =
            "application/vnd.google-apps.folder"

        private const val DROPBOX_LIST =
            "https://api.dropboxapi.com/2/files/list_folder"
        private const val DROPBOX_LIST_CONTINUE =
            "https://api.dropboxapi.com/2/files/list_folder/continue"
        private const val DROPBOX_METADATA =
            "https://api.dropboxapi.com/2/files/get_metadata"
        private const val DROPBOX_CREATE_FOLDER =
            "https://api.dropboxapi.com/2/files/create_folder_v2"
        private const val DROPBOX_MOVE =
            "https://api.dropboxapi.com/2/files/move_v2"
        private const val DROPBOX_DELETE =
            "https://api.dropboxapi.com/2/files/delete_v2"
        private const val DROPBOX_UPLOAD =
            "https://content.dropboxapi.com/2/files/upload"
        private const val DROPBOX_DOWNLOAD =
            "https://content.dropboxapi.com/2/files/download"

        private const val ONEDRIVE_BASE =
            "https://graph.microsoft.com/v1.0/me/drive"
        private const val ONEDRIVE_ROOT =
            "root"
    }
}
