package com.yagay.ysuite.feature.yfiles.provider.local

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileQuery
import com.yagay.ysuite.feature.yfiles.api.YFileType
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileProviderTest {
    @Test
    fun providerListsDirectoriesBeforeFiles() = runBlocking {
        val root =
            Files.createTempDirectory("yfiles-local")
                .toFile()
        try {
            root.resolve("Folder").mkdirs()
            root.resolve("zeta.txt").writeText("z")
            root.resolve("alpha.txt").writeText("a")

            val provider =
                LocalFileProvider(root.absolutePath)
            val result = provider.list(
                provider.root(),
            )

            assertTrue(result is Outcome.Success)
            val nodes = (result as Outcome.Success).value
            assertEquals(
                listOf(
                    "Folder",
                    "alpha.txt",
                    "zeta.txt",
                ),
                nodes.map { it.name },
            )
            assertEquals(
                YFileType.Directory,
                nodes.first().type,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun recursiveSearchFindsNestedItem() = runBlocking {
        val root =
            Files.createTempDirectory("yfiles-search")
                .toFile()
        try {
            val nested =
                root.resolve("nested").apply {
                    mkdirs()
                }
            nested.resolve("target.log")
                .writeText("test")

            val provider =
                LocalFileProvider(root.absolutePath)
            val searchTerm = "target"
            val result = provider.list(
                directory = provider.root(),
                query = YFileQuery(
                    text = searchTerm,
                    recursive = true,
                ),
            )

            assertTrue(result is Outcome.Success)
            val nodes = (result as Outcome.Success).value
            assertEquals(
                listOf("target.log"),
                nodes.map { it.name },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun providerRootDoesNotNavigateOutsideBoundary() {
        val root =
            Files.createTempDirectory("yfiles-boundary")
                .toFile()
        try {
            val provider =
                LocalFileProvider(root.absolutePath)
            assertNull(
                provider.parent(provider.root()),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun engineRoutesCreateRenameCopyAndMove() = runBlocking {
        val root =
            Files.createTempDirectory("yfiles-engine")
                .toFile()
        try {
            val provider =
                LocalFileProvider(root.absolutePath)
            val engine =
                com.yagay.ysuite.feature.yfiles.engine
                    .DefaultYFilesEngine(
                        com.yagay.ysuite.feature.yfiles.engine
                            .YFileProviderRegistry(
                                listOf(provider),
                            ),
                    )

            val created = engine.createFile(
                provider.root(),
                "one.txt",
            ) as Outcome.Success
            val renamed = engine.rename(
                created.value.ref,
                "two.txt",
            ) as Outcome.Success

            val folder = engine.createDirectory(
                provider.root(),
                "folder",
            ) as Outcome.Success
            val copied = engine.copy(
                renamed.value.ref,
                folder.value.ref,
            ) as Outcome.Success

            assertEquals(
                "two.txt",
                copied.value.name,
            )

            val moved = engine.move(
                copied.value.ref,
                provider.root(),
            )

            assertTrue(
                moved is Outcome.Success,
            )
            moved as Outcome.Success
            assertEquals(
                "two (1).txt",
                moved.value.name,
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
