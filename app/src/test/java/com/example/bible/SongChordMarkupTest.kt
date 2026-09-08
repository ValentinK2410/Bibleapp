package com.example.bible

import com.example.bible.data.SongChordMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongChordMarkupTest {

    @Test
    fun detectsHolyChordsLine() {
        val lyrics = """
            |           G                       A                D/F#
            |Прекрасная, прекрасная Твоя любовь, мой Бог
        """.trimMargin()
        assertTrue(SongChordMarkup.hasChords(lyrics))
        assertTrue(SongChordMarkup.isChordLine("           G                       A                D/F#"))
        assertFalse(SongChordMarkup.isChordLine("Прекрасная, прекрасная Твоя любовь, мой Бог"))
    }

    @Test
    fun stripRemovesChordRowsAndChordPro() {
        val lyrics = """
            |G     Am
            |Слава [C]Тебе
        """.trimMargin()
        val plain = SongChordMarkup.stripChords(lyrics)
        assertEquals("Слава Тебе", plain.trim())
        assertFalse(SongChordMarkup.hasChords(plain))
    }

    @Test
    fun transposeHolyChordsUpTone() {
        val line = "G                       A                D/F#"
        val out = SongChordMarkup.transpose(line, 2)
        assertTrue(out.contains("A"))
        assertTrue(out.contains("H"))
        assertTrue(out.contains("E/G#"))
    }

    @Test
    fun expandChordProForDisplay() {
        val lines = SongChordMarkup.displayLines("Слава [G]Тебе", showChords = true, transposeSemitones = 0)
        assertEquals(2, lines.size)
        assertTrue(lines[0].isChord)
        assertTrue(lines[0].text.contains("G"))
        assertEquals("Слава Тебе", lines[1].text)
    }

    @Test
    fun keepsSpacesFromHtml() {
        val html = "           G                       A\nПрекрасная любовь"
        val text = SongChordMarkup.fromHtmlFragment(html)
        assertTrue(text.contains("           G"))
        assertTrue(SongChordMarkup.hasChords(text))
    }
}
