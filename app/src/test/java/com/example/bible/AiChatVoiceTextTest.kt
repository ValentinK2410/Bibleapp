package com.example.bible

import com.example.bible.data.AiChatVoiceText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiChatVoiceTextTest {

    @Test
    fun stripsMarkdownForSpeech() {
        val spoken = AiChatVoiceText.forSpeech(
            """
            ## Заголовок
            **Вера** — это [уверенность](https://example.com) в невидимом.
            - пункт
            `код`
            """.trimIndent(),
        )
        assertEquals(
            "Заголовок Вера — это уверенность в невидимом. пункт код",
            spoken,
        )
        assertFalse(spoken.contains("**"))
        assertFalse(spoken.contains("https://"))
    }
}
