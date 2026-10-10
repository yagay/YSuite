package com.yagay.suite.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class FeatureInfrastructureTest {
    @Test fun ringLogKeepsLatestEntries() {
        val history = FeatureLogBuffer(2)
        history.append("old")
        history.append("new")
        history.append("latest")
        assertEquals("new\nlatest", history.readAll())
        history.clear()
        assertEquals("", history.readAll())
    }

    @Test fun rootTextLinesAreNormalizedCentrally() {
        assertEquals(listOf("first", "second"), FeatureRootCommands.splitLines("first\r\nsecond"))
        assertTrue(FeatureRootCommands.splitLines("").isEmpty())
        val failed = HostCommandResult(-1, "", "", errorMessage = "Unavailable")
        assertEquals("Unavailable", FeatureRootCommands.errorText(failed))
    }

    @Test fun diagnosticArchiveKeepsRelativePaths() {
        val root = kotlin.io.path.createTempDirectory("feature-diagnostics").toFile()
        try {
            val src = File(root, "evidence").also { it.mkdirs() }
            val nested = File(src, "sub").also { it.mkdirs() }
            File(nested, "status.txt").writeText("ok")
            val zip = File(root, "bundle.zip")
            FeatureDiagnosticArchive.zipDirectory(src, zip)
            ZipFile(zip).use { archive ->
                assertEquals("ok", archive.getInputStream(archive.getEntry("sub/status.txt"))
                    .bufferedReader().readText())
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
