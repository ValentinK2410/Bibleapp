package com.example.bible

import com.example.bible.ui.splitMediaTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitMediaTitleTest {

    @Test
    fun featInParenthesesBecomesArtistLine() {
        assertEquals(
            "Ближе" to "Алексей Захаренко",
            splitMediaTitle("Ближе (feat. Алексей Захаренко)"),
        )
    }

    @Test
    fun plainTitleStaysWhole() {
        assertEquals("Христианская проповедь" to null, splitMediaTitle("Христианская проповедь"))
    }
}
