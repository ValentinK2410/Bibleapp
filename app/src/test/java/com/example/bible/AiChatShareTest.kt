package com.example.bible

import com.example.bible.data.AiChatShare
import com.example.bible.data.DeepSeekMessage
import org.junit.Assert.assertTrue
import org.junit.Test

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

        val post = AiChatShare.toMicroblogPost("Вера", messages)
        assertTrue(post.body.startsWith("Беседа с ИИ"))
        assertTrue(post.spans.isNotEmpty())
        assertTrue(post.spans.any { it.bold && it.fontSize == 20 })
    }
}
