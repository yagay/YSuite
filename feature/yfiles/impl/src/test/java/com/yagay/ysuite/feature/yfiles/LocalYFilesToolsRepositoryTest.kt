package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YBatchRenameRule
import com.yagay.ysuite.feature.yfiles.api.YFileEntry
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalYFilesToolsRepositoryTest {
    private val tools = LocalYFilesToolsRepository()

    @Test
    fun zipRoundTripPreservesContent() = runBlocking {
        val root = Files.createTempDirectory("yfiles-zip").toFile()
        try {
            val source = root.resolve("source.txt")
            source.writeText("hello")
            val entry = entry(source)

            val archive = tools.createZip(
                entries = listOf(entry),
                destinationPath = root.absolutePath,
                archiveName = "test",
            ) as Outcome.Success

            assertTrue(archive.value.archive.path.endsWith(".zip"))

            val output = root.resolve("out").apply { mkdirs() }
            val extracted = tools.extractZip(
                archive = archive.value.archive,
                destinationPath = output.absolutePath,
            ) as Outcome.Success

            assertEquals(1, extracted.value.succeeded)
            assertEquals(
                "hello",
                output.resolve("source.txt").readText(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateScanUsesContentHash() = runBlocking {
        val root = Files.createTempDirectory("yfiles-dupes").toFile()
        try {
            root.resolve("a.bin").writeBytes(
                byteArrayOf(1, 2, 3),
            )
            root.resolve("b.bin").writeBytes(
                byteArrayOf(1, 2, 3),
            )
            root.resolve("c.bin").writeBytes(
                byteArrayOf(4, 5, 6),
            )

            val result = tools.findDuplicates(
                root.absolutePath,
            ) as Outcome.Success

            assertEquals(1, result.value.size)
            assertEquals(
                setOf("a.bin", "b.bin"),
                result.value.single().entries
                    .map { it.name }
                    .toSet(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shaTextHexAndAnalysisAreBounded() = runBlocking {
        val root = Files.createTempDirectory("yfiles-tools").toFile()
        try {
            val file = root.resolve("note.txt")
            file.writeText("abcdef")
            val entry = entry(file)

            val hash = tools.sha256(entry) as Outcome.Success
            assertEquals(64, hash.value.hex.length)

            val text = tools.readText(
                entry = entry,
                maxChars = 3,
            ) as Outcome.Success
            assertEquals("abc", text.value.text)
            assertTrue(text.value.truncated)

            val hex = tools.readHex(
                entry = entry,
                maxBytes = 3,
            ) as Outcome.Success
            assertTrue(hex.value.text.contains("61 62 63"))
            assertTrue(hex.value.truncated)

            val analysis = tools.analyzeDirectory(
                root.absolutePath,
            ) as Outcome.Success
            assertEquals(1, analysis.value.fileCount)
            assertEquals(6L, analysis.value.totalBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun textEditAndBatchRenameWork() = runBlocking {
        val root = Files.createTempDirectory("yfiles-edit").toFile()
        try {
            val one = root.resolve("one.txt").apply {
                writeText("old")
            }
            val two = root.resolve("two.txt").apply {
                writeText("two")
            }

            val saved = tools.writeText(
                entry(one),
                "new",
            ) as Outcome.Success
            assertEquals("new", one.readText())
            assertEquals(3L, saved.value.sizeBytes)

            val preview = tools.previewBatchRename(
                entries = listOf(entry(one), entry(two)),
                rule = YBatchRenameRule(
                    prefix = "x_",
                    find = ".txt",
                    replace = ".log",
                ),
            ) as Outcome.Success

            val applied = tools.applyBatchRename(
                preview.value,
            ) as Outcome.Success

            assertEquals(2, applied.value.succeeded)
            assertTrue(root.resolve("x_one.log").exists())
            assertTrue(root.resolve("x_two.log").exists())
            assertFalse(one.exists())
            assertFalse(two.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun entry(file: java.io.File) =
        YFileEntry(
            name = file.name,
            path = file.absolutePath,
            directory = file.isDirectory,
            sizeBytes = if (file.isFile) file.length() else 0L,
            modifiedAtMillis = file.lastModified(),
            hidden = file.isHidden,
        )
}
