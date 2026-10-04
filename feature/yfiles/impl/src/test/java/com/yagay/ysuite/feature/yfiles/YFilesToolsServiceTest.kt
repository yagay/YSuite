package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.engine.DefaultYFilesEngine
import com.yagay.ysuite.feature.yfiles.engine.YFileProviderRegistry
import com.yagay.ysuite.feature.yfiles.provider.archive.YFilesArchiveController
import com.yagay.ysuite.feature.yfiles.provider.archive.ZipArchiveProvider
import com.yagay.ysuite.feature.yfiles.provider.local.LocalFileProvider
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YFilesToolsServiceTest {
    @Test
    fun hashTextHexAnalysisAndZipRoundTrip() = runBlocking {
        val root = Files.createTempDirectory(
            "yfiles-tools-root",
        ).toFile()
        val cache = Files.createTempDirectory(
            "yfiles-tools-cache",
        ).toFile()

        try {
            val file = root.resolve("note.txt")
            file.writeText("hello")
            val local = LocalFileProvider(
                root.absolutePath,
            )
            val archive = ZipArchiveProvider()
            val engine = DefaultYFilesEngine(
                YFileProviderRegistry(
                    listOf(local, archive),
                ),
            )
            val controller =
                YFilesArchiveController(
                    engine = engine,
                    provider = archive,
                    cacheDirectory = cache,
                )
            val tools = YFilesToolsService(
                engine = engine,
                archives = controller,
                cacheDirectory = cache,
            )

            val listed = engine.list(
                local.root(),
            ) as Outcome.Success
            val note = listed.value.single()

            val hash = tools.sha256(
                note.ref,
            ) as Outcome.Success
            assertEquals(64, hash.value.hex.length)

            val text = tools.readText(
                note.ref,
            ) as Outcome.Success
            assertEquals("hello", text.value.text)

            val hex = tools.readHex(
                note.ref,
            ) as Outcome.Success
            assertTrue(
                hex.value.text.contains(
                    "68 65 6C 6C 6F",
                ),
            )

            val analysis = tools.analyze(
                local.root(),
            ) as Outcome.Success
            assertEquals(1, analysis.value.fileCount)
            assertEquals(5L, analysis.value.totalBytes)

            val zipped = tools.createZip(
                refs = listOf(note.ref),
                destination = local.root(),
                archiveName = "bundle",
            )
            assertTrue(zipped is Outcome.Success)
            zipped as Outcome.Success
            assertTrue(
                zipped.value.name.endsWith(".zip"),
            )

            val mounted = controller.mount(
                zipped.value.ref,
            )
            assertTrue(mounted is Outcome.Success)
            mounted as Outcome.Success

            val archiveFiles = engine.list(
                mounted.value,
            ) as Outcome.Success
            assertEquals(
                listOf("note.txt"),
                archiveFiles.value.map { it.name },
            )

            val archiveText = tools.readText(
                archiveFiles.value.single().ref,
            ) as Outcome.Success
            assertEquals(
                "hello",
                archiveText.value.text,
            )
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }
}
