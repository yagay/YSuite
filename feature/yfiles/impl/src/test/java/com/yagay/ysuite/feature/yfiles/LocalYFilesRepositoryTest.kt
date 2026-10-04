package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import com.yagay.ysuite.feature.yfiles.api.YFileTransferMode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalYFilesRepositoryTest {
    @Test
    fun listFiltersAndKeepsDirectoriesFirst() = runBlocking {
        val root = Files.createTempDirectory("yfiles-test").toFile()
        try {
            root.resolve("Folder").mkdirs()
            root.resolve("zeta.txt").writeText("z")
            root.resolve("alpha.txt").writeText("a")

            val repository =
                LocalYFilesRepository(root.absolutePath)

            val result = repository.list(
                YFileQuery(
                    path = root.absolutePath,
                    sort = YFileSort.Name,
                ),
            )

            assertTrue(result is Outcome.Success)
            val entries = (result as Outcome.Success).value
            assertEquals(
                listOf("Folder", "alpha.txt", "zeta.txt"),
                entries.map { it.name },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recursiveSearchFindsNestedFile() = runBlocking {
        val root = Files.createTempDirectory("yfiles-search").toFile()
        try {
            val nested = root.resolve("nested").apply { mkdirs() }
            nested.resolve("target.log").writeText("test")

            val repository =
                LocalYFilesRepository(root.absolutePath)
            val searchTerm = "target"

            val result = repository.list(
                YFileQuery(
                    path = root.absolutePath,
                    text = searchTerm,
                    recursive = true,
                ),
            )

            assertTrue(result is Outcome.Success)
            val entries = (result as Outcome.Success).value
            assertEquals(
                listOf("target.log"),
                entries.map { it.name },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun createRenameCopyMoveAndTrashRoundTrip() = runBlocking {
        val root = Files.createTempDirectory("yfiles-ops").toFile()
        try {
            val repository =
                LocalYFilesRepository(root.absolutePath)

            val created = repository.createFile(
                root.absolutePath,
                "alpha.txt",
            ) as Outcome.Success
            root.resolve("alpha.txt").writeText("content")

            val renamed = repository.rename(
                created.value,
                "renamed.txt",
            ) as Outcome.Success
            assertTrue(root.resolve("renamed.txt").isFile)

            val destination = root.resolve("dest").apply { mkdirs() }
            val copied = repository.transfer(
                listOf(renamed.value),
                destination.absolutePath,
                YFileTransferMode.Copy,
            ) as Outcome.Success
            assertEquals(1, copied.value.succeeded)
            assertTrue(destination.resolve("renamed.txt").isFile)

            val trashed = repository.moveToTrash(
                listOf(renamed.value),
            ) as Outcome.Success
            assertEquals(1, trashed.value.succeeded)
            assertFalse(root.resolve("renamed.txt").exists())

            val trash = repository.listTrash() as Outcome.Success
            assertEquals(1, trash.value.size)

            val restored = repository.restoreTrash(
                setOf(trash.value.single().id),
            ) as Outcome.Success
            assertEquals(1, restored.value.succeeded)
            assertTrue(root.resolve("renamed.txt").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun batchMoveTransfersAllSelectedEntries() = runBlocking {
        val root = Files.createTempDirectory("yfiles-batch").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            val destination = root.resolve("destination").apply { mkdirs() }
            source.resolve("one.txt").writeText("1")
            source.resolve("two.txt").writeText("2")

            val repository =
                LocalYFilesRepository(root.absolutePath)
            val listed = repository.list(
                YFileQuery(path = source.absolutePath),
            ) as Outcome.Success

            val moved = repository.transfer(
                entries = listed.value,
                destinationPath = destination.absolutePath,
                mode = YFileTransferMode.Move,
            ) as Outcome.Success

            assertEquals(2, moved.value.succeeded)
            assertTrue(source.listFiles().isNullOrEmpty())
            assertEquals(
                setOf("one.txt", "two.txt"),
                destination.listFiles()
                    .orEmpty()
                    .map { it.name }
                    .toSet(),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
