package com.example.bible

import com.example.bible.data.AiChatRepository
import com.example.bible.data.db.AiChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatContextTest {

    @Test
    fun apiMessagesKeepsOnlyRecentTail() {
        val stored = (1..20).map { i ->
            AiChatMessageEntity(
                id = i.toLong(),
                chatId = 1L,
                role = if (i % 2 == 1) "user" else "assistant",
                content = "m$i",
                createdAtMs = i.toLong(),
            )
        }
        val api = AiChatRepository.apiMessages(stored)
        assertEquals("system", api.first().role)
        assertTrue(api.size <= 13)
        assertEquals("m20", api.last().content)
        assertTrue(api.none { it.content == "m1" })
    }
}
