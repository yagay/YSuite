package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileSort
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
                    text = "",
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

            val result = repository.list(
                YFileQuery(
                    path = root.absolutePath,
                    text = "target",
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
}
