package com.example.bible

import com.example.bible.data.GigaChatContentPart
import com.example.bible.data.GigaChatImages
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GigaChatImagesTest {

    @Test
    fun extractsRemoteIdsAndLocalizes() = runBlocking {
        val id = "b28fbd4f-105a-43e0-ba5a-2faa80b1f43c"
        val raw = "Вот кот <img src=\"$id\" fuse=\"true\"/> готово."
        assertEquals(listOf(id), GigaChatImages.remoteIds(raw))
        val dir = File(createTempDir(), "imgs").apply { mkdirs() }
        val localized = GigaChatImages.materialize(raw, dir = dir) { "jpeg".toByteArray() }
        assertTrue(localized.contains("${GigaChatImages.FILE_PREFIX}$id.jpg"))
        assertTrue(File(dir, "$id.jpg").isFile)
        val parts = GigaChatImages.parts(localized, dir)
        assertEquals(3, parts.size)
        assertTrue(parts[0] is GigaChatContentPart.Text)
        assertTrue(parts[1] is GigaChatContentPart.Image)
        assertTrue(parts[2] is GigaChatContentPart.Text)
        assertEquals("Вот кот готово.", GigaChatImages.stripForApi(localized))
    }

    @Test
    fun identifyUserMessageKeepsPhotoTag() {
        val dir = File(createTempDir(), "photos").apply { mkdirs() }
        val tag = GigaChatImages.saveJpeg(dir, byteArrayOf(1, 2, 3, 4))
        assertTrue(tag != null && tag.contains(GigaChatImages.FILE_PREFIX))
        val msg = GigaChatImages.identifyUserMessage(tag)
        assertTrue(msg.startsWith("Определи, что на фото"))
        assertTrue(msg.contains(tag!!))
    }

    @Test
    fun stripForSpeechRemovesImgTag() {
        val spoken = GigaChatImages.stripForSpeech(
            "Текст <img src=\"a598ae3d-d3eb-454e-a8bf-0848193a603e\" fuse=\"true\"/> дальше",
        )
        assertEquals("Текст дальше", spoken)
    }
}
