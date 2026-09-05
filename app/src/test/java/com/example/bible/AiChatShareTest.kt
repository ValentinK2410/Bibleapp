package com.example.bible

import android.content.Context
import android.content.ContextWrapper
import com.example.bible.data.AiChatShare
import com.example.bible.data.DeepSeekMessage
import com.example.bible.data.GigaChatImages
import com.example.bible.data.MediaCatalogPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiChatShareTest {

    @Test
    fun formatsConversationForClipboardAndPost() {
        val messages = listOf(
            DeepSeekMessage("user", "Что такое вера?"),
            DeepSeekMessage("assistant", "Уверенность в невидимом."),
        )
        val plain = AiChatShare.plainText("Вера", messages)
        assertTrue(plain.contains("Беседа с ИИ"))
        assertTrue(plain.contains("Вы"))
        assertTrue(plain.contains("ИИ"))
        assertTrue(plain.contains("Что такое вера?"))

        val root = createTempDir()
        val context = FakeContext(root)
        val post = AiChatShare.toMicroblogPost(context, "Вера", messages)
        assertEquals("Вера", post.title)
        assertTrue(post.body.startsWith("Беседа с ИИ"))
        assertTrue(post.spans.isNotEmpty())
        assertTrue(post.spans.any { it.bold && it.fontSize == 20 })
    }

    @Test
    fun microblogPostIncludesLocalizedImages() {
        val root = createTempDir()
        val context = FakeContext(root)
        val imagesDir = GigaChatImages.dir(context)
        val id = "b28fbd4f-105a-43e0-ba5a-2faa80b1f43c"
        val imageFile = File(imagesDir, "$id.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val assistant = "Вот результат <img src=\"${GigaChatImages.FILE_PREFIX}${imageFile.name}\" />"
        val messages = listOf(
            DeepSeekMessage("user", "Нарисуй кота"),
            DeepSeekMessage("assistant", assistant),
        )

        val post = AiChatShare.toMicroblogPost(context, "Кот", messages)
        assertEquals(1, post.images.size)
        val copied = File(MediaCatalogPaths.microblogDir(context), post.images.single().fileName)
        assertTrue(copied.isFile)
        assertTrue(copied.length() > 0L)
        assertTrue(post.body.contains("Вот результат"))
    }

    private class FakeContext(private val filesRoot: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = filesRoot
    }
}
