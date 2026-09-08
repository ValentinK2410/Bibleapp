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
    fun chordOnlyBracketLineStaysAboveLyrics() {
        val lyrics = """
            |[Am].          [A].                [E]
            |Косари на лугу размахалися
        """.trimMargin()
        val lines = SongChordMarkup.displayLines(lyrics, showChords = true, transposeSemitones = 0)
        assertEquals(2, lines.size)
        assertTrue(lines[0].isChord)
        assertFalse(lines[1].isChord)
        assertEquals("Косари на лугу размахалися", lines[1].text)
        assertTrue(lines[0].text.startsWith("Am"))
        assertFalse(lines.any { it.text.trim() == "." || it.text.contains("..") && it.text.none { ch -> ch.isLetter() } && !it.isChord })
        val amAt = lyrics.lines()[0].indexOf("[Am]")
        val displayedAm = lines[0].text.indexOf("Am")
        assertEquals(amAt, displayedAm)
    }

    @Test
    fun uniqueChordsKeepOrderAndTranspose() {
        val lyrics = """
            |[Am].          [A].                [E]
            |Косари на лугу
            |G     D/F#
            |вторая строка
        """.trimMargin()
        assertEquals(listOf("Am", "A", "E", "G", "D/F#"), SongChordMarkup.uniqueChordNames(lyrics))
        val up = SongChordMarkup.uniqueChordNames(lyrics, 2)
        assertEquals(listOf("Hm", "H", "F#", "A", "E/G#"), up)
    }

    @Test
    fun keepsSpacesFromHtml() {
        val html = "           G                       A\nПрекрасная любовь"
        val text = SongChordMarkup.fromHtmlFragment(html)
        assertTrue(text.contains("           G"))
        assertTrue(SongChordMarkup.hasChords(text))
    }
}
