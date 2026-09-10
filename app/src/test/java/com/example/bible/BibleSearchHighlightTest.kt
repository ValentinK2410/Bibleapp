package com.example.bible

import com.example.bible.data.computeSearchHighlightRanges
import com.example.bible.ui.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BibleSearchHighlightTest {

    private val settings = SearchSettings()

    @Test
    fun highlightSingleWord_doesNotIncludeFollowingWord() {
        val text = "царь, рабы вашего"
        val ranges = computeSearchHighlightRanges(text, "царь", settings)
        assertEquals(1, ranges.size)
        assertEquals("царь", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun highlightSingleWord_withColonAndNextWord() {
        val text = "царь: останьтесь здесь"
        val ranges = computeSearchHighlightRanges(text, "царь", settings)
        assertEquals(1, ranges.size)
        assertEquals("царь", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun highlightSingleWord_capitalizedInText() {
        val text = "Царь Давид царствовал"
        val ranges = computeSearchHighlightRanges(text, "царь", settings)
        assertEquals(1, ranges.size)
        assertEquals("Царь", text.substring(ranges[0].first, ranges[0].last + 1))
    }

    @Test
    fun highlightSingleWord_doesNotBleedIntoNextToken() {
        val text = "царь что сказал"
        val ranges = computeSearchHighlightRanges(text, "царь", settings)
        assertTrue(ranges.isNotEmpty())
        for (r in ranges) {
            val slice = text.substring(r.first, r.last + 1)
            assertTrue("unexpected highlight: '$slice'", slice.equals("царь", ignoreCase = true))
        }
    }

    @Test
    fun highlightSubstring_inWord() {
        val text = "царствовал"
        val ranges = computeSearchHighlightRanges(text, "цар", settings)
        assertEquals(1, ranges.size)
        assertEquals("цар", text.substring(ranges[0].first, ranges[0].last + 1))
    }
}
