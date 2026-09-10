package com.example.bible

import com.example.bible.data.MediaDownloadDedup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaDownloadDedupTest {

    @Test
    fun stemKey_ignoresExtensionAndCase() {
        assertEquals(
            MediaDownloadDedup.stemKey("Проповедь.mp4"),
            MediaDownloadDedup.stemKey("проповедь.MP4"),
        )
        assertEquals(
            MediaDownloadDedup.stemKey("  Гимн / часть 1  "),
            MediaDownloadDedup.stemKey("Гимн _ часть 1.webm"),
        )
        assertEquals(
            MediaDownloadDedup.stemKey("Very Long Title That Gets Truncated By Yt Dlp Template"),
            MediaDownloadDedup.stemKey("Very Long Title That Gets Truncated By Yt Dlp Template.mp4"),
        )
        assertEquals(
            MediaDownloadDedup.stemKey("/storage/emulated/0/Download/Bible/Проповедь.mp4"),
            MediaDownloadDedup.stemKey("проповедь.MP4"),
        )
    }

    @Test
    fun collectStems_findsFileInDirectory() {
        val dir = File(System.getProperty("java.io.tmpdir"), "bible-dedup-${System.nanoTime()}")
        dir.mkdirs()
        try {
            File(dir, "Утренняя молитва.mp4").writeText("x")
            val keys = MediaDownloadDedup.collectStems(
                titles = listOf("Другое видео"),
                dirs = listOf(dir),
            )
            assertTrue(MediaDownloadDedup.stemKey("Утренняя молитва") in keys)
            assertTrue(MediaDownloadDedup.stemKey("Другое видео") in keys)
            assertFalse(MediaDownloadDedup.stemKey("Совсем новое") in keys)
        } finally {
            dir.deleteRecursively()
        }
    }
}
