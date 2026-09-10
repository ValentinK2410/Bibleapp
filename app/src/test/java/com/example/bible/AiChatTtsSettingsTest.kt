package com.example.bible

import com.example.bible.data.AiChatTtsIntonation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatTtsSettingsTest {

    @Test
    fun parsesIntonationKeys() {
        assertEquals(AiChatTtsIntonation.WHISPER, AiChatTtsIntonation.fromKey("whisper"))
        assertEquals(AiChatTtsIntonation.JOYFUL, AiChatTtsIntonation.fromKey("joyful"))
        assertEquals(AiChatTtsIntonation.NORMAL, AiChatTtsIntonation.fromKey(null))
    }

    @Test
    fun intonationModifiersDiffer() {
        val whisper = AiChatTtsIntonation.WHISPER.modifiers()
        val joyful = AiChatTtsIntonation.JOYFUL.modifiers()
        assertTrue(whisper.volume < joyful.volume)
        assertTrue(whisper.pitchMul < joyful.pitchMul)
    }
}
