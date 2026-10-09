package com.yagay.yfiles

import org.junit.Assert.assertEquals
import org.junit.Test

class FileExplorerEntryRowTest {
    @Test
    fun foldersNeverUseExtensionIcons() {
        assertEquals(ExplorerIconCategory.FOLDER, explorerIconCategory("photo.jpg", true))
    }

    @Test
    fun mediaAndDocumentsHaveSeparateIcons() {
        assertEquals(ExplorerIconCategory.IMAGE, explorerIconCategory("IMAGE.HEIC", false))
        assertEquals(ExplorerIconCategory.VIDEO, explorerIconCategory("clip.mkv", false))
        assertEquals(ExplorerIconCategory.AUDIO, explorerIconCategory("song.flac", false))
        assertEquals(ExplorerIconCategory.DOCUMENT, explorerIconCategory("manual.pdf", false))
    }

    @Test
    fun unknownFilesUseGenericIcon() {
        assertEquals(ExplorerIconCategory.OTHER, explorerIconCategory("random.bin", false))
    }
}
