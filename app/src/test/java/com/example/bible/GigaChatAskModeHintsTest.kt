package com.example.bible

import com.example.bible.data.GigaChatAskModeHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GigaChatAskModeHintsTest {

    @Test
    fun buildsHintsForEachMode() {
        assertTrue(GigaChatAskModeHints.build(quick = true, deep = false, webSearch = false).contains("Быстрый"))
        assertTrue(GigaChatAskModeHints.build(quick = false, deep = true, webSearch = false).contains("Глубоко"))
        assertTrue(GigaChatAskModeHints.build(quick = false, deep = false, webSearch = true).contains("Интернет"))
    }

    @Test
    fun timeoutsMatchDeepSeekPattern() {
        assertEquals(45_000, GigaChatAskModeHints.timeoutMs(quick = true, deep = false, webSearch = false))
        assertEquals(120_000, GigaChatAskModeHints.timeoutMs(quick = false, deep = true, webSearch = false))
        assertEquals(150_000, GigaChatAskModeHints.timeoutMs(quick = false, deep = false, webSearch = true))
    }
}
