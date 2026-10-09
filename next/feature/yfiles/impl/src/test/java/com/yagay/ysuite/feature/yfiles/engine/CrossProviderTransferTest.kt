package com.yagay.ysuite.feature.yfiles.engine

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileCapability
import com.yagay.ysuite.feature.yfiles.api.YFileChunk
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileProvider
import com.yagay.ysuite.feature.yfiles.api.YFileProviderDescriptor
import com.yagay.ysuite.feature.yfiles.api.YFileProviderKind
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.feature.yfiles.api.YFileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossProviderTransferTest {
    @Test
    fun engineStreamsFileBetweenProviders() = runBlocking {
        val source = MemoryProvider("source")
        val destination = MemoryProvider("destination")
        source.put("hello.txt", "hello world".toByteArray())

        val engine = DefaultYFilesEngine(
            YFileProviderRegistry(
                listOf(source, destination),
            ),
        )

        val copied = engine.copy(
            source = YFileRef("source", "/hello.txt"),
            destinationDirectory =
                destination.root(),
        )

        assertTrue(copied is Outcome.Success)
        copied as Outcome.Success
        assertEquals("hello.txt", copied.value.name)
        assertArrayEquals(
            "hello world".toByteArray(),
            destination.bytes("/hello.txt"),
        )
    }

    @Test
    fun engineUsesExplicitTargetName() = runBlocking {
        val source = MemoryProvider("source")
        val destination = MemoryProvider("destination")
        source.put("original.txt", "content".toByteArray())

        val engine = DefaultYFilesEngine(
            YFileProviderRegistry(
                listOf(source, destination),
            ),
        )

        val copied = engine.copy(
            source = YFileRef("source", "/original.txt"),
            destinationDirectory = destination.root(),
            targetName = "restored.txt",
        )

        assertTrue(copied is Outcome.Success)
        copied as Outcome.Success
        assertEquals("restored.txt", copied.value.name)
        assertArrayEquals(
            "content".toByteArray(),
            destination.bytes("/restored.txt"),
        )
    }

    @Test
    fun crossProviderMoveDeletesSourceAfterCopy() = runBlocking {
        val source = MemoryProvider("source")
        val destination = MemoryProvider("destination")
        source.put("move.bin", byteArrayOf(1, 2, 3, 4))

        val engine = DefaultYFilesEngine(
            YFileProviderRegistry(
                listOf(source, destination),
            ),
        )

        val moved = engine.move(
            source = YFileRef("source", "/move.bin"),
            destinationDirectory =
                destination.root(),
        )

        assertTrue(moved is Outcome.Success)
        assertTrue(
            source.stat(
                YFileRef("source", "/move.bin"),
            ) is Outcome.Failure,
        )
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            destination.bytes("/move.bin"),
        )
    }

    @Test
    fun replaceStagesNewContentBeforeRemovingExisting() = runBlocking {
        val source = MemoryProvider("source")
        val destination = MemoryProvider("destination")
        source.put("same.txt", "new".toByteArray())
        destination.put("same.txt", "old".toByteArray())

        val engine = DefaultYFilesEngine(
            YFileProviderRegistry(
                listOf(source, destination),
            ),
        )

        val copied = engine.copy(
            source = YFileRef("source", "/same.txt"),
            destinationDirectory = destination.root(),
            strategy = com.yagay.ysuite.feature.yfiles.api
                .YFileConflictStrategy.Replace,
        )

        assertTrue(copied is Outcome.Success)
        assertArrayEquals(
            "new".toByteArray(),
            destination.bytes("/same.txt"),
        )
        assertEquals(
            listOf("/same.txt"),
            destination.paths(),
        )
    }

    private class MemoryProvider(
        private val id: String,
    ) : YFileProvider {
        private val files =
            linkedMapOf<String, ByteArray>()

        override val descriptor =
            YFileProviderDescriptor(
                id = id,
                kind = YFileProviderKind.Remote,
                capabilities = setOf(
                    YFileCapability.Browse,
                    YFileCapability.Read,
                    YFileCapability.Write,
                    YFileCapability.Create,
                    YFileCapability.Delete,
                ),
            )

        override fun root() =
            YFileRef(id, "/")

        override fun parent(
            ref: YFileRef,
        ): YFileRef? =
            if (ref.path == "/") null else root()

        fun put(
            name: String,
            bytes: ByteArray,
        ) {
            files["/" + name] = bytes
        }

        fun bytes(path: String): ByteArray =
            files.getValue(path)

        fun paths(): List<String> =
            files.keys.sorted()

        override suspend fun list(
            directory: YFileRef,
            query: YFileQuery,
        ): Outcome<List<YFileNode>> =
            if (directory.path != "/") {
                failure("not_directory")
            } else {
                Outcome.Success(
                    files.map {
                        (path, bytes) ->
                        node(path, bytes)
                    },
                )
            }

        override suspend fun stat(
            ref: YFileRef,
        ): Outcome<YFileNode> {
            if (ref.path == "/") {
                return Outcome.Success(
                    YFileNode(
                        ref = root(),
                        name = id,
                        type = YFileType.Directory,
                        writable = true,
                    ),
                )
            }
            val bytes = files[ref.path]
                ?: return failure("not_found")
            return Outcome.Success(
                node(ref.path, bytes),
            )
        }

        override suspend fun createDirectory(
            parent: YFileRef,
            name: String,
        ): Outcome<YFileNode> =
            failure("unsupported_directory")

        override suspend fun createFile(
            parent: YFileRef,
            name: String,
        ): Outcome<YFileNode> {
            val path = "/" + name
            if (path in files) {
                return failure("exists")
            }
            files[path] = ByteArray(0)
            return Outcome.Success(
                node(path, ByteArray(0)),
            )
        }

        override suspend fun rename(
            ref: YFileRef,
            newName: String,
        ): Outcome<YFileNode> {
            val bytes = files.remove(ref.path)
                ?: return failure("not_found")
            val newPath = "/" + newName
            if (newPath in files) {
                files[ref.path] = bytes
                return failure("exists")
            }
            files[newPath] = bytes
            return Outcome.Success(
                node(newPath, bytes),
            )
        }

        override suspend fun delete(
            ref: YFileRef,
        ): Outcome<Unit> =
            if (files.remove(ref.path) != null) {
                Outcome.Success(Unit)
            } else {
                failure("not_found")
            }

        override suspend fun copy(
            source: YFileRef,
            destinationDirectory: YFileRef,
            targetName: String,
            replace: Boolean,
        ): Outcome<YFileNode> =
            failure("force_stream_copy")

        override suspend fun move(
            source: YFileRef,
            destinationDirectory: YFileRef,
            targetName: String,
            replace: Boolean,
        ): Outcome<YFileNode> =
            failure("force_stream_move")

        override suspend fun read(
            ref: YFileRef,
            offset: Long,
            maxBytes: Int,
        ): Outcome<YFileChunk> {
            val bytes = files[ref.path]
                ?: return failure("not_found")
            val start = offset.toInt()
            if (start >= bytes.size) {
                return Outcome.Success(
                    YFileChunk(
                        data = ByteArray(0),
                        eof = true,
                    ),
                )
            }
            val end = minOf(
                bytes.size,
                start + maxBytes,
            )
            return Outcome.Success(
                YFileChunk(
                    data = bytes.copyOfRange(start, end),
                    eof = end >= bytes.size,
                ),
            )
        }

        override suspend fun write(
            ref: YFileRef,
            offset: Long,
            data: ByteArray,
            truncate: Boolean,
        ): Outcome<Unit> {
            val current = files[ref.path]
                ?: return failure("not_found")
            val base = if (truncate) {
                ByteArray(0)
            } else {
                current
            }
            val end = offset.toInt() + data.size
            val next = if (end > base.size) {
                base.copyOf(end)
            } else {
                base.copyOf()
            }
            data.copyInto(
                destination = next,
                destinationOffset = offset.toInt(),
            )
            files[ref.path] = next
            return Outcome.Success(Unit)
        }

        private fun node(
            path: String,
            bytes: ByteArray,
        ) =
            YFileNode(
                ref = YFileRef(id, path),
                name = path.removePrefix("/"),
                type = YFileType.File,
                sizeBytes = bytes.size.toLong(),
                readable = true,
                writable = true,
            )

        private fun <T> failure(
            code: String,
        ): Outcome<T> =
            Outcome.Failure(
                code = code,
                message = code,
            )
    }
}
