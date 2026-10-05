package com.yagay.ysuite.feature.yfiles.provider.remote

import android.content.Context
import com.explorer.fileexplorer.core.data.DiagnosticLog
import com.explorer.fileexplorer.core.model.ConflictResolution
import com.explorer.fileexplorer.core.model.FileItem
import com.explorer.fileexplorer.core.network.NetworkConnection
import com.explorer.fileexplorer.core.network.NetworkFileRepository
import com.explorer.fileexplorer.core.network.Protocol
import com.explorer.fileexplorer.core.network.ftp.FtpFileRepository
import com.explorer.fileexplorer.core.network.sftp.SftpFileRepository
import com.explorer.fileexplorer.core.network.sftp.SftpHostKeyChallenge
import com.explorer.fileexplorer.core.network.sftp.SftpKnownHostsStore
import com.explorer.fileexplorer.core.network.smb.SmbFileRepository
import com.explorer.fileexplorer.core.network.webdav.WebDavFileRepository
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileConflictStrategy
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
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class RemoteFileProvider(
    context: Context,
    private val store: YFilesNetworkStore,
) : YFileProvider {
    private val appContext =
        context.applicationContext
    private val knownHosts =
        SftpKnownHostsStore(appContext)
    private val diagnosticLog =
        DiagnosticLog()
    private val cacheRoot =
        File(
            appContext.cacheDir,
            "yfiles-remote",
        ).apply { mkdirs() }

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
        YFileRef(
            PROVIDER_ID,
            VIRTUAL_ROOT,
        )

    override fun parent(
        ref: YFileRef,
    ): YFileRef? {
        requireProvider(ref)
        if (ref.path == VIRTUAL_ROOT) {
            return null
        }
        val parsed = parse(ref)
        val profile =
            store.profile(parsed.profileId)
                ?: return root()
        val profileRoot =
            normalizeRemotePath(
                profile.remotePath,
            )
        val current =
            normalizeRemotePath(parsed.remotePath)
        if (
            current == profileRoot ||
            current == "/"
        ) {
            return root()
        }
        val parent =
            current.trimEnd('/')
                .substringBeforeLast(
                    '/',
                    missingDelimiterValue = "",
                )
                .ifBlank { "/" }
        return ref(
            parsed.profileId,
            parent,
        )
    }

    override suspend fun list(
        directory: YFileRef,
        query: YFileQuery,
    ): Outcome<List<YFileNode>> =
        if (directory.path == VIRTUAL_ROOT) {
            Outcome.Success(
                sort(
                    store.profiles()
                        .map(::profileNode),
                    query,
                ),
            )
        } else {
            withRemote(
                directory,
                "remote_list_failed",
            ) { profile, repo, path ->
                val needle =
                    query.text.trim()
                val items =
                    if (
                        query.recursive &&
                        needle.isNotEmpty()
                    ) {
                        repo.search(
                            rootPath = path,
                            query = needle,
                            regex = false,
                            includeHidden =
                                query.showHidden,
                        ).toList()
                    } else {
                        repo.listFiles(path)
                            .first()
                            .asSequence()
                            .filter {
                                query.showHidden ||
                                    !it.isHidden
                            }
                            .filter {
                                needle.isEmpty() ||
                                    it.name.contains(
                                        needle,
                                        ignoreCase = true,
                                    )
                            }
                            .take(query.maxResults)
                            .toList()
                    }
                sort(
                    items.map {
                        itemNode(
                            profile.id,
                            it,
                        )
                    }.take(query.maxResults),
                    query,
                )
            }
        }

    override suspend fun stat(
        ref: YFileRef,
    ): Outcome<YFileNode> {
        if (ref.path == VIRTUAL_ROOT) {
            return Outcome.Success(
                YFileNode(
                    ref = root(),
                    name = "Remote",
                    type = YFileType.Directory,
                    writable = true,
                ),
            )
        }
        val parsed = parse(ref)
        val profile =
            store.profile(parsed.profileId)
                ?: return failure(
                    "remote_profile_missing",
                    "Remote profile no longer exists",
                )
        val profileRoot =
            normalizeRemotePath(
                profile.remotePath,
            )
        if (
            normalizeRemotePath(
                parsed.remotePath,
            ) == profileRoot
        ) {
            return Outcome.Success(
                profileNode(profile),
            )
        }
        return withRemote(
            ref,
            "remote_stat_failed",
        ) { current, repo, path ->
            val info =
                repo.getFileInfo(path)
                    ?: error(
                        "Remote item does not exist",
                    )
            itemNode(current.id, info)
        }
    }

    override suspend fun createDirectory(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withRemote(
            parent,
            "remote_create_directory_failed",
        ) { profile, repo, path ->
            val created =
                repo.createDirectory(
                    child(path, name),
                ).getOrThrow()
            itemNode(profile.id, created)
        }

    override suspend fun createFile(
        parent: YFileRef,
        name: String,
    ): Outcome<YFileNode> =
        withRemote(
            parent,
            "remote_create_file_failed",
        ) { profile, repo, path ->
            val remote =
                child(path, name)
            val empty =
                cacheFile(
                    profile.id,
                    remote,
                    "create",
                ).apply {
                    parentFile?.mkdirs()
                    writeBytes(ByteArray(0))
                }
            repo.upload(
                empty.absolutePath,
                remote,
            ).getOrThrow()
            val info =
                repo.getFileInfo(remote)
                    ?: error(
                        "Remote file was created but metadata is unavailable",
                    )
            itemNode(profile.id, info)
        }

    override suspend fun rename(
        ref: YFileRef,
        newName: String,
    ): Outcome<YFileNode> =
        withRemote(
            ref,
            "remote_rename_failed",
        ) { profile, repo, path ->
            itemNode(
                profile.id,
                repo.rename(
                    path,
                    sanitizeName(newName),
                ).getOrThrow(),
            )
        }

    override suspend fun delete(
        ref: YFileRef,
    ): Outcome<Unit> =
        withRemote(
            ref,
            "remote_delete_failed",
        ) { _, repo, path ->
            repo.deleteFiles(listOf(path))
                .getOrThrow()
            invalidate(ref)
            Unit
        }

    override suspend fun copy(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> {
        val sourceParsed = parse(source)
        val destinationParsed =
            parse(destinationDirectory)
        if (
            sourceParsed.profileId !=
                destinationParsed.profileId
        ) {
            return failure(
                "remote_cross_profile_copy",
                "Use streamed copy for different remote profiles",
            )
        }
        return withRemote(
            source,
            "remote_copy_failed",
        ) { profile, repo, sourcePath ->
            val destination =
                normalizeRemotePath(
                    destinationParsed.remotePath,
                )
            repo.copyFiles(
                sources = listOf(sourcePath),
                destination = destination,
                conflictResolution =
                    if (replace) {
                        ConflictResolution.OVERWRITE
                    } else {
                        ConflictResolution.RENAME
                    },
            ).getOrThrow()
            val target =
                child(
                    destination,
                    targetName,
                )
            val info =
                repo.getFileInfo(target)
                    ?: repo.listFiles(destination)
                        .first()
                        .firstOrNull {
                            it.name == targetName
                        }
                    ?: error(
                        "Remote copy completed but target is unavailable",
                    )
            itemNode(profile.id, info)
        }
    }

    override suspend fun move(
        source: YFileRef,
        destinationDirectory: YFileRef,
        targetName: String,
        replace: Boolean,
    ): Outcome<YFileNode> {
        val sourceParsed = parse(source)
        val destinationParsed =
            parse(destinationDirectory)
        if (
            sourceParsed.profileId !=
                destinationParsed.profileId
        ) {
            return failure(
                "remote_cross_profile_move",
                "Use streamed move for different remote profiles",
            )
        }
        return withRemote(
            source,
            "remote_move_failed",
        ) { profile, repo, sourcePath ->
            val destination =
                normalizeRemotePath(
                    destinationParsed.remotePath,
                )
            repo.moveFiles(
                sources = listOf(sourcePath),
                destination = destination,
                conflictResolution =
                    if (replace) {
                        ConflictResolution.OVERWRITE
                    } else {
                        ConflictResolution.RENAME
                    },
            ).getOrThrow()
            val target =
                child(
                    destination,
                    targetName,
                )
            val info =
                repo.getFileInfo(target)
                    ?: repo.listFiles(destination)
                        .first()
                        .firstOrNull {
                            it.name == targetName
                        }
                    ?: error(
                        "Remote move completed but target is unavailable",
                    )
            invalidate(source)
            itemNode(profile.id, info)
        }
    }

    override suspend fun read(
        ref: YFileRef,
        offset: Long,
        maxBytes: Int,
    ): Outcome<YFileChunk> =
        withRemote(
            ref,
            "remote_read_failed",
        ) { profile, repo, path ->
            require(offset >= 0L)
            require(maxBytes > 0)
            val cached =
                cacheFile(
                    profile.id,
                    path,
                    "read",
                )
            if (
                offset == 0L ||
                !cached.isFile
            ) {
                cached.parentFile?.mkdirs()
                repo.download(
                    path,
                    cached.absolutePath,
                ).getOrThrow()
            }
            RandomAccessFile(
                cached,
                "r",
            ).use { file ->
                if (offset >= file.length()) {
                    return@use YFileChunk(
                        ByteArray(0),
                        true,
                    )
                }
                file.seek(offset)
                val buffer =
                    ByteArray(maxBytes)
                val count = file.read(buffer)
                if (count <= 0) {
                    YFileChunk(
                        ByteArray(0),
                        true,
                    )
                } else {
                    YFileChunk(
                        data =
                            buffer.copyOf(count),
                        eof =
                            offset + count >=
                                file.length(),
                    )
                }
            }
        }

    override suspend fun write(
        ref: YFileRef,
        offset: Long,
        data: ByteArray,
        truncate: Boolean,
    ): Outcome<Unit> =
        withRemote(
            ref,
            "remote_write_failed",
        ) { profile, repo, path ->
            require(offset >= 0L)
            val staged =
                cacheFile(
                    profile.id,
                    path,
                    "write",
                )
            staged.parentFile?.mkdirs()
            if (
                !truncate &&
                offset > 0L &&
                !staged.exists()
            ) {
                repo.download(
                    path,
                    staged.absolutePath,
                ).getOrThrow()
            }
            RandomAccessFile(
                staged,
                "rw",
            ).use { file ->
                if (truncate) {
                    file.setLength(0L)
                }
                file.seek(offset)
                file.write(data)
                file.fd.sync()
            }
            repo.upload(
                staged.absolutePath,
                path,
            ).getOrThrow()
            invalidateRead(profile.id, path)
            Unit
        }

    fun profiles():
        List<YFilesNetworkProfile> =
        store.profiles()

    fun saveProfile(
        profile: YFilesNetworkProfile,
    ) {
        store.save(profile)
    }

    fun deleteProfile(id: String) {
        store.delete(id)
    }

    fun trustSftpHost(
        challenge: SftpHostKeyChallenge,
    ) {
        knownHosts.trust(challenge)
    }

    suspend fun test(
        profile: YFilesNetworkProfile,
    ): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            val repo =
                repository(profile.protocol)
            try {
                repo.connect(
                    connection(profile),
                ).getOrThrow()
                Outcome.Success(Unit)
            } catch (error: Throwable) {
                failure(
                    "remote_test_failed",
                    error.message
                        ?: "Remote connection failed",
                    error,
                )
            } finally {
                runCatching {
                    repo.disconnect()
                }
            }
        }

    private suspend fun <T> withRemote(
        ref: YFileRef,
        code: String,
        block:
            suspend (
                YFilesNetworkProfile,
                NetworkFileRepository,
                String,
            ) -> T,
    ): Outcome<T> =
        withContext(Dispatchers.IO) {
            try {
                val parsed = parse(ref)
                val profile =
                    store.profile(
                        parsed.profileId,
                    )
                        ?: error(
                            "Remote profile no longer exists",
                        )
                val repo =
                    repository(profile.protocol)
                try {
                    repo.connect(
                        connection(profile),
                    ).getOrThrow()
                    Outcome.Success(
                        block(
                            profile,
                            repo,
                            normalizeRemotePath(
                                parsed.remotePath,
                            ),
                        ),
                    )
                } finally {
                    runCatching {
                        repo.disconnect()
                    }
                }
            } catch (error: Throwable) {
                failure(
                    code,
                    error.message
                        ?: "Remote operation failed",
                    error,
                )
            }
        }

    private fun repository(
        protocol: YFilesNetworkProtocol,
    ): NetworkFileRepository =
        when (protocol) {
            YFilesNetworkProtocol.SMB ->
                SmbFileRepository()
            YFilesNetworkProtocol.SFTP ->
                SftpFileRepository(
                    knownHosts,
                    diagnosticLog,
                )
            YFilesNetworkProtocol.FTP,
            YFilesNetworkProtocol.FTPS ->
                FtpFileRepository()
            YFilesNetworkProtocol.WebDAV ->
                WebDavFileRepository()
        }

    private fun connection(
        profile: YFilesNetworkProfile,
    ): NetworkConnection =
        NetworkConnection(
            name = profile.name,
            protocol =
                when (profile.protocol) {
                    YFilesNetworkProtocol.SMB ->
                        Protocol.SMB
                    YFilesNetworkProtocol.SFTP ->
                        Protocol.SFTP
                    YFilesNetworkProtocol.FTP ->
                        Protocol.FTP
                    YFilesNetworkProtocol.FTPS ->
                        Protocol.FTPS
                    YFilesNetworkProtocol.WebDAV ->
                        Protocol.WEBDAV
                },
            host = profile.host,
            port = profile.port,
            username = profile.username,
            password = profile.password,
            shareName = profile.shareName,
            remotePath = profile.remotePath,
            privateKeyPath =
                profile.privateKeyPath,
            useTls = profile.useTls,
        )

    private data class Parsed(
        val profileId: String,
        val remotePath: String,
    )

    private fun parse(
        ref: YFileRef,
    ): Parsed {
        requireProvider(ref)
        require(ref.path != VIRTUAL_ROOT) {
            "Select a remote connection first"
        }
        val separator =
            ref.path.indexOf(SEPARATOR)
        require(separator > 0) {
            "Invalid remote reference"
        }
        return Parsed(
            profileId =
                ref.path.substring(
                    0,
                    separator,
                ),
            remotePath =
                ref.path.substring(
                    separator +
                        SEPARATOR.length,
                ),
        )
    }

    private fun ref(
        profileId: String,
        remotePath: String,
    ): YFileRef =
        YFileRef(
            PROVIDER_ID,
            profileId +
                SEPARATOR +
                normalizeRemotePath(
                    remotePath,
                ),
        )

    private fun profileNode(
        profile: YFilesNetworkProfile,
    ): YFileNode =
        YFileNode(
            ref =
                ref(
                    profile.id,
                    profile.remotePath,
                ),
            name = profile.name,
            type = YFileType.Directory,
            mimeType = "inode/directory",
            writable = true,
        )

    private fun itemNode(
        profileId: String,
        item: FileItem,
    ): YFileNode =
        YFileNode(
            ref =
                ref(
                    profileId,
                    item.path,
                ),
            name = item.name,
            type =
                when {
                    item.isSymlink ->
                        YFileType.SymbolicLink
                    item.isDirectory ->
                        YFileType.Directory
                    else ->
                        YFileType.File
                },
            sizeBytes =
                if (item.isDirectory) {
                    null
                } else {
                    item.size
                },
            modifiedAtMillis =
                item.lastModified
                    .takeIf { it > 0L },
            mimeType = item.mimeType,
            hidden = item.isHidden,
            readable = item.isReadable,
            writable = item.isWritable,
        )

    private fun child(
        parent: String,
        name: String,
    ): String =
        normalizeRemotePath(parent)
            .trimEnd('/') +
            "/" +
            sanitizeName(name)

    private fun normalizeRemotePath(
        value: String,
    ): String {
        val normalized =
            value.replace('\\', '/')
                .replace(
                    Regex("/+"),
                    "/",
                )
        return (
            if (
                normalized.startsWith("/")
            ) {
                normalized
            } else {
                "/" + normalized
            }
            ).ifBlank { "/" }
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

    private fun sort(
        nodes: List<YFileNode>,
        query: YFileQuery,
    ): List<YFileNode> {
        val base =
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
                base.reversed()
            } else {
                base
            }
        return nodes.sortedWith(
            compareByDescending<YFileNode> {
                it.type ==
                    YFileType.Directory
            }.then(directed),
        )
    }

    private fun cacheFile(
        profileId: String,
        path: String,
        kind: String,
    ): File {
        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    (
                        profileId +
                            "\u0000" +
                            path
                        ).toByteArray(),
                )
                .take(12)
                .joinToString("") {
                    "%02x".format(it)
                }
        return File(
            cacheRoot,
            "$kind-$hash.bin",
        )
    }

    private fun invalidate(ref: YFileRef) {
        val parsed =
            runCatching { parse(ref) }
                .getOrNull()
                ?: return
        invalidateRead(
            parsed.profileId,
            parsed.remotePath,
        )
        cacheFile(
            parsed.profileId,
            parsed.remotePath,
            "write",
        ).delete()
    }

    private fun invalidateRead(
        profileId: String,
        path: String,
    ) {
        cacheFile(
            profileId,
            path,
            "read",
        ).delete()
    }

    private fun requireProvider(
        ref: YFileRef,
    ) {
        require(
            ref.providerId == PROVIDER_ID,
        ) {
            "Reference belongs to another provider"
        }
    }

    private fun <T> failure(
        code: String,
        message: String,
        cause: Throwable? = null,
    ): Outcome<T> =
        Outcome.Failure(
            code = code,
            message = message,
            cause = cause,
            retryable = true,
        )

    companion object {
        const val PROVIDER_ID = "remote"
        private const val VIRTUAL_ROOT =
            "remote://virtual-root"
        private const val SEPARATOR = "::"
    }
}
