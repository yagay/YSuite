package com.yagay.ysuite.feature.yfiles.provider.archive

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFilesEngine
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YFilesArchiveController(
    private val engine: YFilesEngine,
    private val provider: ZipArchiveProvider,
    private val cacheDirectory: File,
) {
    suspend fun mount(
        source: YFileRef,
    ): Outcome<YFileRef> {
        val node = when (
            val result = engine.stat(source)
        ) {
            is Outcome.Success ->
                result.value
            is Outcome.Failure ->
                return result
        }

        return try {
            val localFile =
                if (
                    source.providerId ==
                        LocalFileProvider.PROVIDER_ID
                ) {
                    File(source.path)
                } else {
                    materialize(source)
                }
            Outcome.Success(
                provider.mount(
                    file = localFile,
                    displayName = node.name,
                    temporary =
                        source.providerId !=
                            LocalFileProvider.PROVIDER_ID,
                ),
            )
        } catch (error: Throwable) {
            Outcome.Failure(
                code = "archive_mount_failed",
                message = error.message
                    ?: ARCHIVE_MOUNT_MESSAGE,
                cause = error,
            )
        }
    }

    fun unmount(
        root: YFileRef,
    ) {
        provider.unmount(root)
    }

    private suspend fun materialize(
        source: YFileRef,
    ): File =
        withContext(Dispatchers.IO) {
            require(
                cacheDirectory.mkdirs() ||
                    cacheDirectory.isDirectory,
            )
            val target = File(
                cacheDirectory,
                "archive-" +
                    UUID.randomUUID() +
                    ".zip",
            )
            FileOutputStream(target).use {
                output ->
                var offset = 0L
                while (true) {
                    val chunk = engine.read(
                        source,
                        offset,
                        CHUNK_BYTES,
                    )
                    when (chunk) {
                        is Outcome.Success -> {
                            output.write(
                                chunk.value.data,
                            )
                            offset +=
                                chunk.value.data.size
                            if (chunk.value.eof) {
                                break
                            }
                        }
                        is Outcome.Failure ->
                            error(
                                chunk.message,
                            )
                    }
                }
            }
            target
        }

    companion object {
        private const val CHUNK_BYTES =
            64 * 1024
        private const val ARCHIVE_MOUNT_MESSAGE =
            "Unable to mount archive"
    }
}
