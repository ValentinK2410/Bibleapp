package com.example.bible

import com.example.bible.data.AiChatNeuralSpeechPlayer
import com.example.bible.data.AiChatTtsIntonation
import com.example.bible.data.SaluteSpeechClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaluteSpeechClientTest {

    @Test
    fun wrapSsml_onlyForNonNormalIntonation() {
        assertNull(SaluteSpeechClient.wrapSsml("Привет", AiChatTtsIntonation.NORMAL))
        val whisper = SaluteSpeechClient.wrapSsml("Привет", AiChatTtsIntonation.WHISPER)
        assertTrue(whisper!!.contains("<speak>"))
        assertTrue(whisper.contains("x-soft"))
    }

    @Test
    fun escapeXml_specialChars() {
        assertEquals("a &amp; b", SaluteSpeechClient.escapeXml("a & b"))
    }

    @Test
    fun splitSpeechChunks_respectsLimit() {
        val text = "а".repeat(5000)
        val chunks = AiChatNeuralSpeechPlayer.splitSpeechChunks(text, maxSize = 3800)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 3800 })
    }

    @Test
    fun resolveAuthKey_prefersSaluteKey() {
        assertEquals("abc", SaluteSpeechClient.resolveAuthKey("abc", "xyz"))
        assertEquals("xyz", SaluteSpeechClient.resolveAuthKey("", "xyz"))
    }
}
