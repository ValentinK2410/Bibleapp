package com.example.bible

import com.example.bible.ui.VerseShareText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerseShareTextTest {

    @Test
    fun singleVerseFormat() {
        val out = VerseShareText.format(
            bookName = "Исаия",
            chapter = 41,
            verseNumbers = setOf(3),
            verseTextsByNumber = mapOf(3 to "Он гонит их"),
        )
        assertEquals("Исаия 41:3\nОн гонит их", out)
    }

    @Test
    fun contiguousRangeFormat() {
        val out = VerseShareText.format(
            bookName = "1 Иоанна",
            chapter = 1,
            verseNumbers = setOf(2, 3, 4),
            verseTextsByNumber = mapOf(
                2 to "жизнь была",
                3 to "то, что мы видели",
                4 to "и пишем вам",
            ),
        )
        assertTrue(out.startsWith("1 Иоанна 1:2-4\n\n"))
        assertTrue(out.contains("2. жизнь была"))
        assertTrue(out.contains("4. и пишем вам"))
    }

    @Test
    fun verseNumbersFromRangeSpec_simpleEnd() {
        val nums = VerseShareText.verseNumbersFromRangeSpec(2, "5", 10)
        assertEquals(setOf(2, 3, 4, 5), nums)
    }
}
